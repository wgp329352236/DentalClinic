package com.dentalclinic.user.route

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
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

/**
 * C 端路由的异常兜底：把未预期异常翻译成 (body.code, 对外提示)，同时留下可排查的日志。
 *
 * C 端的契约是 HTTP 200 + body.code（与管理端用真实状态码的风格不同），所以这里只决定 code：
 *
 * - 请求体解析一类的客户端输入错误（见 [CLIENT_INPUT_ERRORS]）回 400 并只记 WARN。
 *   小程序端字段类型写错、或客户端与服务端版本不一致时会成批出现：记成带完整堆栈的 ERROR
 *   会淹没真正的服务器故障形成告警噪音；回 500 则会误导前端「稍后重试」——
 *   重试并不会让非法的请求体变合法。管理端由 StatusPages 把同一批异常映射为 400，这里与之对齐。
 * - 其余异常一律 500 + 通用提示，堆栈记 ERROR。
 *
 * 两种情况都不回传 e.message：底层异常的文本会把表名/列名/约束名乃至服务器绝对路径
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
        logger.error("$scene 失败", e)
        500 to fallback
    }
