package com.dentalclinic.common.config

import com.dentalclinic.admin.entity.Users
import com.dentalclinic.common.security.PhoneCipherService
import kotlinx.coroutines.flow.toList
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.StatementType
import org.jetbrains.exposed.v1.r2dbc.R2dbcTransaction
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update
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
                migrateUserPhoneEncryption()
                migratePointsRuleUnitAmount()
                migratePointsRuleUniqueActionType()
                migrateDoctorScheduleUnique()
                migrateUploadedFileOwnerType()
                migrateRechargeOrderColumns()
                migrateAppointmentReminderFlags()
                migrateAppointmentCancelRequestColumns()
                migratePointsRecordExpiryColumns()
                migrateProfileCompletePointsUniqueIndex()
                migrateReferralRelationIndex()
                migrateMpBannerActivity()
                migrateCustomerClinicRelations()
                migrateDropPrimaryClinicId()
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
            addColumnIfMissing("consumption_record", "operator_name", "VARCHAR(50) NULL"),
            addColumnIfMissing("consumption_record", "points_used", "INTEGER NOT NULL DEFAULT 0"),
            addColumnIfMissing("consumption_record", "points_deduct", "DECIMAL(12,2) NOT NULL DEFAULT 0"),
            addColumnIfMissing("consumption_record", "payment_status", "VARCHAR(20) NOT NULL DEFAULT 'success'"),
            addColumnIfMissing("consumption_record", "out_trade_no", "VARCHAR(64) NULL"),
            addColumnIfMissing("consumption_record", "prepay_id", "VARCHAR(64) NULL"),
            addColumnIfMissing("consumption_record", "wechat_transaction_id", "VARCHAR(64) NULL"),
            addColumnIfMissing("consumption_record", "paid_at", "TIMESTAMP NULL")
        ).count { it }

        if (added == 0) return

        // 历史数据回填：无优惠明细的旧记录，原价即消费金额、实付即已记录的支付拆分
        exec("UPDATE consumption_record SET original_amount = amount WHERE original_amount = 0")
        exec("UPDATE consumption_record SET pay_amount = balance_used + wechat_paid WHERE pay_amount = 0")
        logger.info("[SchemaMigration] consumption_record 补齐 $added 个字段并回填历史数据。")
    }

    /**
     * user 表手机号加密迁移（需求 P0-4 合规）。
     *
     * - phone_cipher 保存 AES-256-GCM 密文；
     * - phone_hash 保存 HMAC-SHA256 盲索引，用于精确查询与唯一约束；
     * - 历史 phone 明文逐行加密后清空，迁移幂等，已有合法密文不重复处理；
     * - 唯一索引创建前先检查盲索引重复，存在重复时跳过并告警，避免回滚整批迁移。
     */
    private suspend fun R2dbcTransaction.migrateUserPhoneEncryption() {
        addColumnIfMissing("user", "phone_hash", "VARCHAR(64) NULL")
        addColumnIfMissing("user", "phone_cipher", "TEXT NULL")
        // P2-18: 客户备注字段
        addColumnIfMissing("user", "remark", "TEXT NULL")
        // P2-19: 门店经纬度字段（用于距离计算）
        addColumnIfMissing("clinic", "latitude", "DOUBLE PRECISION NULL")
        addColumnIfMissing("clinic", "longitude", "DOUBLE PRECISION NULL")

        val rows = Users.selectAll()
            .where { Users.phone.isNotNull() or Users.phoneHash.isNull() }
            .toList()

        var migrated = 0
        rows.forEach { row ->
            val userId = row[Users.id]
            val plaintext = row[Users.phone]?.takeIf { it.isNotBlank() }
            val existingCipher = row[Users.phoneCipher]?.takeIf { it.isNotBlank() }

            if (plaintext != null) {
                val encrypted = PhoneCipherService.encrypt(plaintext)
                Users.update({ Users.id eq userId }) {
                    it[Users.phone] = null
                    it[Users.phoneCipher] = encrypted.ciphertext
                    it[Users.phoneHash] = encrypted.blindIndex
                }
                migrated++
            } else if (existingCipher != null && row[Users.phoneHash].isNullOrBlank()) {
                val decrypted = PhoneCipherService.decrypt(existingCipher)
                if (decrypted != null) {
                    Users.update({ Users.id eq userId }) {
                        it[Users.phoneHash] = PhoneCipherService.blindIndex(decrypted)
                    }
                    migrated++
                } else {
                    logger.warn("[SchemaMigration] 用户 {} 的 phone_cipher 无法解密，跳过盲索引回填", userId)
                }
            }
        }

        if (migrated > 0) {
            logger.info("[SchemaMigration] 已完成 {} 个用户手机号加密/盲索引迁移。", migrated)
        }

        if (!indexExists("user", "cuser_uk_phone_hash")) {
            val duplicated = exec(
                "SELECT phone_hash FROM \"user\" " +
                    "WHERE phone_hash IS NOT NULL GROUP BY phone_hash HAVING COUNT(*) > 1",
                explicitStatementType = StatementType.SELECT,
                transform = { 1 }
            )?.toList().orEmpty()
            if (duplicated.isEmpty()) {
                exec("CREATE UNIQUE INDEX IF NOT EXISTS cuser_uk_phone_hash ON \"user\" (phone_hash)")
                logger.info("[SchemaMigration] user.phone_hash 唯一索引已创建。")
            } else {
                logger.warn(
                    "[SchemaMigration] user.phone_hash 存在 {} 组重复，跳过唯一索引；请人工清理后重启。",
                    duplicated.size
                )
            }
        }
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

    /**
     * recharge_record 补微信支付所需列：out_trade_no（商户单号）、prepay_id（预支付会话）。
     *
     * 真实微信支付链路中，下单时写 out_trade_no/prepay_id，回调与主动查单都靠
     * out_trade_no 唯一定位订单，因此还要补唯一索引——重复回调的幂等入账依赖它。
     * 历史 mock 订单两列为 NULL，不影响唯一索引（PostgreSQL 中 NULL 互不相等）。
     */
    private suspend fun R2dbcTransaction.migrateRechargeOrderColumns() {
        addColumnIfMissing("recharge_record", "out_trade_no", "VARCHAR(64) NULL")
        addColumnIfMissing("recharge_record", "prepay_id", "VARCHAR(64) NULL")
        exec("CREATE UNIQUE INDEX IF NOT EXISTS recharge_uk_out_trade_no " +
            "ON recharge_record (out_trade_no)")
    }

    /**
     * appointment 补预约提醒幂等标记列。
     *
     * 定时任务在就诊前一天 20:00、当天 10:00 各发一次订阅消息/站内信。
     * 小时级 tick 会重复扫描到同一批预约，用 0/1 标记位保证每类提醒只发一次。
     * 历史预约默认 0（未发送），若恰好命中发送窗口会补发，符合业务预期。
     */
    private suspend fun R2dbcTransaction.migrateAppointmentReminderFlags() {
        addColumnIfMissing("appointment", "remind_evening_sent", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing("appointment", "remind_morning_sent", "INTEGER NOT NULL DEFAULT 0")
    }

    /** appointment 补取消申请与门店审核审计字段。 */
    private suspend fun R2dbcTransaction.migrateAppointmentCancelRequestColumns() {
        addColumnIfMissing("appointment", "cancel_request_status", "VARCHAR(20) NULL")
        addColumnIfMissing("appointment", "cancel_request_reason", "TEXT NULL")
        addColumnIfMissing("appointment", "cancel_requested_at", "TIMESTAMP NULL")
        addColumnIfMissing("appointment", "cancel_reviewed_by", "BIGINT NULL")
        addColumnIfMissing("appointment", "cancel_reviewed_at", "TIMESTAMP NULL")
    }

    /**
     * points_record 补积分批次过期处理列。
     *
     * - expired_amount：该批次已被过期任务处理的积分量（达到 changeAmount 即整批处理完，幂等标记）。
     * - expiry_notified_at：到期前 30 天提醒的发送时间，NULL 表示未提醒。
     */
    private suspend fun R2dbcTransaction.migratePointsRecordExpiryColumns() {
        addColumnIfMissing("points_record", "expired_amount", "INTEGER NOT NULL DEFAULT 0")
        addColumnIfMissing("points_record", "expiry_notified_at", "TIMESTAMP NULL")
    }

    /**
     * 完善资料积分每个用户只允许成功领取一次。
     *
     * 历史版本可能在并发或重复请求下写入多条同来源流水。直接建唯一索引会导致启动失败，
     * 因此先检查重复；存在重复时只告警并跳过，待人工清理后下次启动自动补建。
     */
    private suspend fun R2dbcTransaction.migrateProfileCompletePointsUniqueIndex() {
        val indexName = "points_uk_profile_complete"
        if (indexExists("points_record", indexName)) return

        val duplicates = exec(
            "SELECT user_id FROM points_record " +
                "WHERE source_type = 'profile_complete' GROUP BY user_id HAVING COUNT(*) > 1",
            explicitStatementType = StatementType.SELECT,
            transform = { 1 }
        )?.toList().orEmpty()

        if (duplicates.isNotEmpty()) {
            logger.warn(
                "[SchemaMigration] points_record 存在完善资料积分重复用户，跳过唯一索引 {}；userIds={}",
                indexName,
                duplicates
            )
            return
        }

        exec(
            "CREATE UNIQUE INDEX IF NOT EXISTS $indexName " +
                "ON points_record (user_id) WHERE source_type = 'profile_complete'"
        )
    }

    /**
     * referral_relation 唯一索引换型。
     *
     * 旧索引 ref_rel_uk_referee_id 是 referee_id 单列唯一：一个用户只能有一行，
     * 于是「本人推荐码占位行（referrer=referee=本人）」与「真实推荐关系行」无法共存——
     * 新模型下被推荐人既要保留自己的分享码占位行，又要有一行真实关系。
     *
     * 换成 PostgreSQL 部分唯一索引：只约束真实关系行（referrer_id <> referee_id），
     * 保证一个用户最多被一个人推荐；占位行不参与约束，可与真实行并存。
     * 旧库每个 referee 至多一行，替换不会因历史重复数据失败。
     */
    private suspend fun R2dbcTransaction.migrateReferralRelationIndex() {
        // 先删约束再删独立索引：旧库中 ref_rel_uk_referee_id 可能是唯一约束
        // （PostgreSQL 不允许直接 DROP INDEX 被约束引用的索引），也可能只是普通唯一索引。
        // 两条语句都带 IF EXISTS，约束/索引/两者同时存在三种情况均安全，全部幂等。
        exec("ALTER TABLE referral_relation DROP CONSTRAINT IF EXISTS ref_rel_uk_referee_id")
        exec("DROP INDEX IF EXISTS ref_rel_uk_referee_id")
        exec(
            "CREATE UNIQUE INDEX IF NOT EXISTS ref_rel_uk_referee_real " +
                "ON referral_relation (referee_id) WHERE referrer_id <> referee_id"
        )
        logger.info("[SchemaMigration] referral_relation 唯一索引已替换为部分唯一索引 ref_rel_uk_referee_real。")
    }

    /**
     * mp_banner 补 activity_id 列并建唯一索引（一个轮播图对应一个活动）。
     *
     * 历史轮播图 activity_id 为 NULL：PostgreSQL 唯一索引允许多个 NULL，迁移不会失败；
     * 但 C 端只展示已绑定有效活动的轮播图，历史数据需在管理端重新绑定活动后才会恢复展示。
     */
    private suspend fun R2dbcTransaction.migrateMpBannerActivity() {
        val added = addColumnIfMissing("mp_banner", "activity_id", "BIGINT NULL")
        if (!indexExists("mp_banner", "mp_banner_uk_activity")) {
            exec("CREATE UNIQUE INDEX IF NOT EXISTS mp_banner_uk_activity ON mp_banner (activity_id)")
        }
        if (added) logger.info("[SchemaMigration] mp_banner 补齐 activity_id 列与活动唯一索引。")
    }

    /**
     * customer_clinic_relation 历史数据回填（一次性）。
     *
     * 表由 `SchemaUtils.create` 建好后，若表为空则按既有业务事实回填客户-门店关系：
     * 预约、消费、充值三类事件各自推导关系。
     *
     * 只在表为空时执行：回填 SQL 的 ON CONFLICT 会累加计数，重复执行会导致计数翻倍。
     */
    private suspend fun R2dbcTransaction.migrateCustomerClinicRelations() {
        val hasRows = exec(
            "SELECT 1 FROM customer_clinic_relation LIMIT 1",
            explicitStatementType = StatementType.SELECT,
            transform = { 1 }
        )?.toList().orEmpty().isNotEmpty()
        if (hasRows) return

        // 预约（排除已取消；待确认/已确认/已完成/爽约均视为到店行为）
        exec(
            """
            INSERT INTO customer_clinic_relation
              (user_id, clinic_id, first_visit_at, last_visit_at, appointment_count, source)
            SELECT user_id, clinic_id, MIN(created_at), MAX(created_at), COUNT(*), 'appointment'
            FROM appointment
            WHERE status <> 'cancelled'
            GROUP BY user_id, clinic_id
            ON CONFLICT (user_id, clinic_id) DO UPDATE SET
              first_visit_at    = LEAST(customer_clinic_relation.first_visit_at, EXCLUDED.first_visit_at),
              last_visit_at     = GREATEST(customer_clinic_relation.last_visit_at, EXCLUDED.last_visit_at),
              appointment_count = customer_clinic_relation.appointment_count + EXCLUDED.appointment_count,
              updated_at        = now()
            """.trimIndent()
        )

        // 消费（仅已结算：历史 status 可能为 success 而尾款仍 pending）
        exec(
            """
            INSERT INTO customer_clinic_relation
              (user_id, clinic_id, first_visit_at, last_visit_at,
               consumption_count, consumption_amount, source)
            SELECT user_id, clinic_id, MIN(created_at), MAX(created_at), COUNT(*), SUM(amount), 'consumption'
            FROM consumption_record
            WHERE status = 'success' AND payment_status = 'success'
            GROUP BY user_id, clinic_id
            ON CONFLICT (user_id, clinic_id) DO UPDATE SET
              first_visit_at     = LEAST(customer_clinic_relation.first_visit_at, EXCLUDED.first_visit_at),
              last_visit_at      = GREATEST(customer_clinic_relation.last_visit_at, EXCLUDED.last_visit_at),
              consumption_count  = customer_clinic_relation.consumption_count + EXCLUDED.consumption_count,
              consumption_amount = customer_clinic_relation.consumption_amount + EXCLUDED.consumption_amount,
              updated_at         = now()
            """.trimIndent()
        )

        // 充值
        exec(
            """
            INSERT INTO customer_clinic_relation
              (user_id, clinic_id, first_visit_at, last_visit_at, source)
            SELECT user_id, clinic_id, MIN(created_at), MAX(created_at), 'recharge'
            FROM recharge_record
            WHERE status = 'success'
            GROUP BY user_id, clinic_id
            ON CONFLICT (user_id, clinic_id) DO UPDATE SET
              first_visit_at = LEAST(customer_clinic_relation.first_visit_at, EXCLUDED.first_visit_at),
              last_visit_at  = GREATEST(customer_clinic_relation.last_visit_at, EXCLUDED.last_visit_at),
              updated_at     = now()
            """.trimIndent()
        )

        logger.info("[SchemaMigration] customer_clinic_relation 历史关系回填完成。")
    }

    /**
     * 方案 C 阶段 5：删除 `user.primary_clinic_id`。
     *
     * 客户属于整个医院，门店只是就诊地点；客户-门店关系已由 customer_clinic_relation
     * 记录并完成历史回填，单一归属字段不再有读写方。DROP COLUMN IF EXISTS 保证重复启动安全。
     */
    private suspend fun R2dbcTransaction.migrateDropPrimaryClinicId() {
        if (!columnExists("user", "primary_clinic_id")) return
        exec("ALTER TABLE \"user\" DROP COLUMN IF EXISTS primary_clinic_id")
        logger.info("[SchemaMigration] user.primary_clinic_id 已删除。")
    }

    /** 索引是否已存在。 */
    private suspend fun R2dbcTransaction.indexExists(table: String, indexName: String): Boolean =
        exec(
            "SELECT indexname FROM pg_indexes WHERE tablename = '$table' AND indexname = '$indexName'",
            explicitStatementType = StatementType.SELECT,
            transform = { 1 }
        )?.toList().orEmpty().isNotEmpty()

    /** 表列是否已存在。 */
    private suspend fun R2dbcTransaction.columnExists(table: String, column: String): Boolean =
        exec(
            "SELECT column_name FROM information_schema.columns " +
                "WHERE table_name = '$table' AND column_name = '$column'",
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
        if (columnExists(table, column)) return false

        // 标识符必须加引号：`user` 是 PostgreSQL 保留字，ALTER TABLE user ... 会直接语法错误
        exec("ALTER TABLE \"$table\" ADD COLUMN IF NOT EXISTS \"$column\" $definition")
        logger.info("[SchemaMigration] ALTER TABLE $table ADD COLUMN $column $definition")
        return true
    }

    private const val POINTS_RULE_ACTION_TYPE_INDEX = "idx_points_rule_action_type"
    private const val DOCTOR_SCHEDULE_UNIQUE_INDEX = "idx_doctor_schedule_doctor_day_start"
}
