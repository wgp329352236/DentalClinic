package com.dentalclinic.common.config

import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.slf4j.LoggerFactory

/**
 * 轻量级表结构迁移。
 *
 * 背景：Exposed R2DBC 的 `SchemaUtils.create()` 只会创建「不存在的表」，
 * 对已存在的表不会补列；而 `createMissingTablesAndColumns()` 在 1.3.1 已标记废弃
 * （替代者 MigrationUtils 不在当前依赖中）。因此新增字段需要显式迁移。
 *
 * 约定：
 * - 全部语句使用 PostgreSQL 的 `ADD COLUMN IF NOT EXISTS`，可重复执行（幂等）。
 * - 表名/列名/类型均为本文件内的硬编码常量，不接收外部输入，无注入风险。
 * - 仅在「本次真的补了新列」时才回填历史数据，避免每次启动都做全表扫描。
 * - 迁移只做「加法式」变更（补列、补索引），不删改业务数据；数据取舍交给人工。
 */
object SchemaMigration {

    private val logger = LoggerFactory.getLogger("SchemaMigration")

    /** 启动时执行全部迁移。任一语句失败只告警，不阻断服务启动。 */
    suspend fun migrate() {
        try {
            suspendTransaction(DBConfig.database) {
                migrateConsumptionRecord()
                migratePointsRuleUnitAmount()
                migratePointsRuleUniqueActionType()
                migrateDoctorScheduleUnique()
                migrateUploadedFileOwnerType()
            }
        } catch (e: Exception) {
            logger.error("[SchemaMigration] 表结构迁移失败，请检查数据库权限", e)
        }
    }

    /**
     * consumption_record 补列：消费结算链路所需的优惠明细与审计字段。
     * 回填规则：历史数据的原价等于消费金额，实付等于余额支付加微信支付。
     */
    private suspend fun R2dbcTransaction.migrateConsumptionRecord() {
        val added = listOf(
            addColumnIfMissing("consumption_record", "original_amount", "DECIMAL(12,2) NOT NULL DEFAULT 0"),
            addColumnIfMissing("consumption_record", "discount_amount", "DECIMAL(12,2) NOT NULL DEFAULT 0"),
            addColumnIfMissing("consumption_record", "coupon_deduct", "DECIMAL(12,2) NOT NULL DEFAULT 0"),
            addColumnIfMissing("consumption_record", "user_coupon_id", "BIGINT NULL"),
            addColumnIfMissing("consumption_record", "pay_amount", "DECIMAL(12,2) NOT NULL DEFAULT 0"),
            addColumnIfMissing("consumption_record", "status", "VARCHAR(20) NOT NULL DEFAULT 'success'"),
            addColumnIfMissing("consumption_record", "operator_name", "VARCHAR(50) NULL")
        ).count { it }

        if (added == 0) return

        // 历史数据回填：无优惠明细的旧记录，原价即消费金额、实付即已记录的支付拆分
        exec("UPDATE consumption_record SET original_amount = amount WHERE original_amount = 0")
        exec("UPDATE consumption_record SET pay_amount = balance_used + wechat_paid WHERE pay_amount = 0")
        logger.info("[SchemaMigration] consumption_record 补齐 $added 个字段并回填历史数据。")
    }

    /**
     * points_rule 补 unit_amount 列。
     *
     * 需求 3.4.1 的充值积分是「每充值 1 元积 0.5 分」，整型的 points_per_unit 表达不了小数比率。
     * 补列后比率由「每 unit_amount 个单位积 points_per_unit 分」表达，充值规则即
     * unit_amount=2 + points_per_unit=1。历史行默认 1，与原语义完全等价，无需回填。
     */
    private suspend fun R2dbcTransaction.migratePointsRuleUnitAmount() {
        addColumnIfMissing("points_rule", "unit_amount", "INTEGER NOT NULL DEFAULT 1")
    }

    /**
     * points_rule.action_type 唯一索引。
     *
     * 业务语义是「一个 actionType 对应一条生效规则」，但启动播种与 test-data.sql 曾各插一套，
     * 导致同一 actionType 多行，规则命中结果取决于查询顺序，积分额度会漂移。加唯一索引从库层面根治。
     *
     * 已有重复数据时索引无法创建，此时只告警并跳过——删哪一行属于业务取舍，迁移不代做决定；
     * 人工清理后下次启动会自动补上索引。
     */
    private suspend fun R2dbcTransaction.migratePointsRuleUniqueActionType() {
        if (indexExists("points_rule", POINTS_RULE_ACTION_TYPE_INDEX)) return

        val duplicated = exec(
            "SELECT action_type FROM points_rule GROUP BY action_type HAVING COUNT(*) > 1",
            explicitStatementType = StatementType.SELECT,
            transform = { 1 }
        )?.toList().orEmpty()

        if (duplicated.isNotEmpty()) {
            logger.warn(
                "[SchemaMigration] points_rule.action_type 存在 ${duplicated.size} 组重复，跳过唯一索引创建；" +
                    "请人工清理重复规则后重启，索引会自动补上。"
            )
            return
        }

        exec(
            "CREATE UNIQUE INDEX IF NOT EXISTS $POINTS_RULE_ACTION_TYPE_INDEX " +
                "ON points_rule (action_type)"
        )
        logger.info("[SchemaMigration] 已为 points_rule.action_type 创建唯一索引 $POINTS_RULE_ACTION_TYPE_INDEX。")
    }

    /**
     * doctor_schedule 唯一索引 (doctor_id, day_of_week, start_time)。
     *
     * test-data.sql 曾在已有排班的库上重复执行，为同一医生同一天同一开始时间插入第二条排班，
     * 使可约号源被重复计算。加唯一索引后重复执行会直接报错，而非静默产生冲突数据。
     *
     * 局限：只能拦「开始时间完全相同」的重复。开始时间不同但时段部分重叠（如 10:00-16:00
     * 与 14:00-17:00）仍需应用层校验——数据库层要拦这种情形得用排他约束（EXCLUDE USING gist），
     * 成本与可维护性都不划算，暂不引入。
     *
     * 已有重复数据时索引无法创建，此时只告警并跳过——删哪一行属业务取舍，迁移不代做决定；
     * 人工清理后下次启动会自动补上索引。
     */
    private suspend fun R2dbcTransaction.migrateDoctorScheduleUnique() {
        if (indexExists("doctor_schedule", DOCTOR_SCHEDULE_UNIQUE_INDEX)) return

        val duplicated = exec(
            "SELECT doctor_id FROM doctor_schedule " +
                "GROUP BY doctor_id, day_of_week, start_time HAVING COUNT(*) > 1",
            explicitStatementType = StatementType.SELECT,
            transform = { 1 }
        )?.toList().orEmpty()

        if (duplicated.isNotEmpty()) {
            logger.warn(
                "[SchemaMigration] doctor_schedule 存在 ${duplicated.size} 组 " +
                    "(doctor_id, day_of_week, start_time) 重复，跳过唯一索引创建；" +
                    "请人工清理重复排班后重启，索引会自动补上。"
            )
            return
        }

        exec(
            "CREATE UNIQUE INDEX IF NOT EXISTS $DOCTOR_SCHEDULE_UNIQUE_INDEX " +
                "ON doctor_schedule (doctor_id, day_of_week, start_time)"
        )
        logger.info("[SchemaMigration] 已为 doctor_schedule 创建唯一索引 $DOCTOR_SCHEDULE_UNIQUE_INDEX。")
    }

    /**
     * uploaded_file 补 owner_type 列。
     *
     * 管理端要上传轮播图，但 uploaded_file.user_id 是 NOT NULL 且语义是「C 端用户」。
     * 管理员上传约定写 user_id=0 并用 owner_type='admin' 区分归属。
     * 历史行全是 C 端上传，默认值 'user' 即正确语义，无需回填。
     */
    private suspend fun R2dbcTransaction.migrateUploadedFileOwnerType() {
        addColumnIfMissing("uploaded_file", "owner_type", "VARCHAR(10) NOT NULL DEFAULT 'user'")
    }

    /** 索引是否已存在。 */
    private suspend fun R2dbcTransaction.indexExists(table: String, indexName: String): Boolean =
        exec(
            "SELECT indexname FROM pg_indexes WHERE tablename = '$table' AND indexname = '$indexName'",
            explicitStatementType = StatementType.SELECT,
            transform = { 1 }
        )?.toList().orEmpty().isNotEmpty()

    /**
     * 补单列，返回是否真的执行了 ADD COLUMN（列已存在时返回 false）。
     */
    private suspend fun R2dbcTransaction.addColumnIfMissing(
        table: String,
        column: String,
        definition: String
    ): Boolean {
        val existing = exec(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = '$table' AND column_name = '$column'",
            explicitStatementType = StatementType.SELECT,
            transform = { 1 }
        )?.toList().orEmpty()

        if (existing.isNotEmpty()) return false

        exec("ALTER TABLE $table ADD COLUMN IF NOT EXISTS $column $definition")
        logger.info("[SchemaMigration] ALTER TABLE $table ADD COLUMN $column $definition")
        return true
    }

    private const val POINTS_RULE_ACTION_TYPE_INDEX = "idx_points_rule_action_type"
    private const val DOCTOR_SCHEDULE_UNIQUE_INDEX = "idx_doctor_schedule_doctor_day_start"
}
