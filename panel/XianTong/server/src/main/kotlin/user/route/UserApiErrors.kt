package com.dentalclinic.user.route

import com.dentalclinic.common.wx.WechatApiException
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.r2dbc.spi.R2dbcException
import kotlinx.serialization.SerializationException
import org.slf4j.Logger

/**
 * 属于「调用方把请求写错了」的异常类型，与 admin 端 StatusPages 映射为 400 的那几类保持一致：
 * - [BadRequestException]：call.receive<T>() 无法把请求体转成目标 DTO
 * - [ContentTransformationException]：Content-Type 不对，如非 multipart 却调 receiveMultipart()
 * - [SerializationException]：JSON 结构与 DTO 不匹配
 */
private val CLIENT_INPUT_ERRORS = listOf(
    BadRequestException::class,
    ContentTransformationException::class,
    SerializationException::class
)

/** PostgreSQL 唯一约束冲突的 SQLState（integrity constraint violation 类下的 unique_violation）。 */
private const val SQLSTATE_UNIQUE_VIOLATION = "23505"

/**
 * 约束名 → 面向 C 端用户的文案，只列用户能自行修正的冲突。
 *
 * 键取自 AdminTables 里 uniqueIndex 的名字。匹配方式是查异常文本里是否含约束名：
 * PostgreSQL 的报错原文形如 `duplicate key value violates unique constraint "cuser_uk_phone"`，
 * 约束名稳定且不随数据变化，比解析列名可靠。
 */
private val UNIQUE_CONFLICT_MESSAGES = mapOf(
    "cuser_uk_phone_hash" to "该手机号已被其他账号使用",
    "cuser_uk_phone" to "该手机号已被其他账号使用",
    "cuser_uk_openid" to "该微信账号已绑定其他用户"
)

/** 未命中 [UNIQUE_CONFLICT_MESSAGES] 时的兜底文案，不暴露具体是哪个约束。 */
private const val GENERIC_CONFLICT_MESSAGE = "数据重复，请检查后重试"

/**
 * 沿 cause 链找出唯一约束冲突异常。
 *
 * 需要遍历是因为异常可能被包装：Exposed 的事务层与 R2DBC 驱动都可能把原始的
 * R2dbcDataIntegrityViolationException 裹进别的异常里再抛出。
 */
private fun findUniqueViolation(e: Exception): R2dbcException? =
    generateSequence<Throwable>(e) { it.cause }
        .filterIsInstance<R2dbcException>()
        .firstOrNull { it.sqlState == SQLSTATE_UNIQUE_VIOLATION }

/**
 * C 端路由的异常兜底：把未预期异常翻译成 (body.code, 对外提示)，同时留下可排查的日志。
 *
 * C 端的契约是 HTTP 200 + body.code（与管理端用真实状态码的风格不同），所以这里只决定 code：
 *
 * - 请求体解析一类的客户端输入错误（见 [CLIENT_INPUT_ERRORS]）回 400 并只记 WARN。
 *   小程序端字段类型写错、或客户端与服务端版本不一致时会成批出现：记成带完整堆栈的 ERROR
 *   会淹没真正的服务器故障形成告警噪音；回 500 则会误导前端「稍后重试」——
 *   重试并不会让非法的请求体变合法。管理端由 StatusPages 把同一批异常映射为 400，这里与之对齐。
 * - 唯一约束冲突（SQLState 23505）回 409 并给出用户能据以修正的文案。这类冲突是业务常态
 *   而非故障：改手机号撞上他人已用号码属预期分支，回 500 会让用户以为系统坏了并反复重试。
 * - 其余异常一律 500 + 通用提示，堆栈记 ERROR。
 *
 * 三个分支都不回传 e.message：底层异常的文本会把表名/列名/约束名乃至服务器绝对路径
 * 暴露给 C 端用户，而这些细节对调用方毫无用处。
 *
 * @param logger   调用方所在文件的 logger
 * @param scene    日志场景前缀，形如 `"[Recharge]: 用户 62 充值"`。
 *                 不要把凭证（微信 code、refreshToken）与个人敏感信息（手机号、真实姓名）拼进来。
 * @param fallback 判定为 500 时对外展示的提示，各端点用自己的动作文案
 */
internal fun translateApiError(
    logger: Logger,
    scene: String,
    fallback: String,
    e: Exception
): Pair<Int, String> =
    if (CLIENT_INPUT_ERRORS.any { it.java.isInstance(e) }) {
        // 只记 message 不记堆栈：内容是「无法把请求体转成某个 DTO」一类的定位信息，
        // 不含请求体原文，排查字段问题够用，也不会把用户提交的敏感内容写进日志
        logger.warn("$scene 请求参数不合法: ${e.message}")
        400 to "请求参数格式不正确"
    } else {
        val conflict = findUniqueViolation(e)
        if (conflict != null) {
            // 原始文本含表名/约束名，只进日志；对外仅回查表得到的业务文案
            logger.warn("$scene 唯一约束冲突: ${conflict.message}")
            val detail = conflict.message
            val hint = UNIQUE_CONFLICT_MESSAGES.entries
                .firstOrNull { (constraint, _) -> detail?.contains(constraint) == true }
                ?.value
            409 to (hint ?: GENERIC_CONFLICT_MESSAGE)
        } else {
            logger.error("$scene 失败", e)
            500 to fallback
        }
    }

/**
 * 微信登录相关错误码 → (body.code, 面向 C 端的提示)。
 *
 * 微信 errcode 是稳定契约，按「用户能否自行解决」分三类翻译：
 * - 凭证问题（40029 invalid code / 40163 code been used）：用户重新点一次登录即可，回 400 引导重试；
 * - 频率限制（45011）：短时间重复点击/多人共用同一 code，回 429 要求稍后重试；
 * - 凭证配置错误（40013 appid 无效 / 40125 appsecret 无效 / 40164 IP 未白名单）：
 *   重试不会恢复，必须运维改配置，回 500 但提示「配置异常」，避免用户反复尝试。
 *
 * 未命中的错误（网络超时、openid 缺失等）仍走 [translateApiError] 的通用兜底，
 * 原始 errcode/errmsg 只留在日志里，不回传调用方。
 */
private val WECHAT_LOGIN_ERRORS: Map<Int, Pair<Int, String>> = mapOf(
    40029 to (400 to "登录凭证已失效，请重新点击登录"),
    40163 to (400 to "登录凭证已失效，请重新点击登录"),
    45011 to (429 to "操作过于频繁，请稍后再试"),
    40013 to (500 to "微信登录服务配置异常，请联系客服"),
    40125 to (500 to "微信登录服务配置异常，请联系客服"),
    40164 to (500 to "微信登录服务配置异常，请联系客服")
)

/**
 * 微信一键登录的异常翻译：已知 errcode 走 [WECHAT_LOGIN_ERRORS]，
 * 其余（含唯一约束冲突、请求体不合法）复用 [translateApiError]。
 */
internal fun translateWechatLoginError(logger: Logger, e: Exception): Pair<Int, String> {
    val wx = e as? WechatApiException
    val mapped = wx?.errcode?.let(WECHAT_LOGIN_ERRORS::get)
    return if (mapped != null) mapped
    else translateApiError(logger, "[Auth]: 微信登录", "登录失败，请稍后重试", e)
}
