package com.dentalclinic.common.security

import com.dentalclinic.common.config.EnvFileLoader
import com.dentalclinic.common.config.GlobalConfig.appConfig
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 门店「到店签到码」签发与验签。
 *
 * 安全模型：码内容为 `dc1.<clinicId>.<签名>`，签名 = HMAC-SHA256(secret, "dc1.<clinicId>")
 * 的 base64url（无填充）。clinicId 在码中明文可见，但没有服务端密钥就无法为任意门店伪造出
 * 合法签名——替代此前前端直接生成 `{"clinicId":1}` 明文码、任何人都可自制的方案。
 *
 * 设计成**每门店静态码**（不含时间戳、不过期）：签到码要打印张贴在前台长期使用，
 * 过期会迫使反复重贴；防伪造由 HMAC 保证。需要作废时轮换密钥（配置/环境变量）即可，
 * 轮换后全部门店需重新打印码。
 *
 * 密钥优先级：环境变量 CHECKIN_SIGNING_SECRET > yaml `checkin.signingSecret`。
 * 都缺失时生成一次性随机密钥并打 WARN：仅本地联调可用（管理端与用户端在同一进程，
 * 单次运行内签发/验签一致），重启即失效；生产必须配置稳定密钥。
 */
class CheckinCodeService {

    companion object {
        /** 码版本前缀，参与签名，防止跨版本/跨用途重放 */
        private const val SCHEME = "dc1"
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }

    private val logger = LoggerFactory.getLogger("CheckinCodeService")

    private val secret: ByteArray = resolveSecret()

    /** 为门店签发签到码（确定性输出：同一门店同一密钥多次调用结果一致，可重复打印）。 */
    fun issue(clinicId: Long): String {
        require(clinicId > 0) { "非法门店ID" }
        return "$SCHEME.$clinicId.${sign(clinicId)}"
    }

    /**
     * 校验码并返回 clinicId；结构错误/版本不符/clinicId 非法/签名不匹配一律返回 null，
     * 由上层按「无效的门店码」处理。使用恒定时间比较防止时序侧信道。
     */
    fun verify(code: String?): Long? {
        if (code.isNullOrBlank()) return null
        val parts = code.trim().split(".")
        if (parts.size != 3 || parts[0] != SCHEME) return null

        val clinicId = parts[1].toLongOrNull()
        if (clinicId == null || clinicId <= 0) return null

        val expected = sign(clinicId)
        val actual = parts[2]
        val expectedBytes = expected.toByteArray(Charsets.US_ASCII)
        val actualBytes = actual.toByteArray(Charsets.US_ASCII)
        // 长度不同直接拒绝；等长时恒定时间比较，避免逐字符短路泄露签名
        if (expectedBytes.size != actualBytes.size) return null
        return if (MessageDigest.isEqual(expectedBytes, actualBytes)) clinicId else null
    }

    private fun sign(clinicId: Long): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret, HMAC_ALGORITHM))
        val raw = mac.doFinal("$SCHEME.$clinicId".toByteArray(Charsets.UTF_8))
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
    }

    private fun resolveSecret(): ByteArray {
        val configured = EnvFileLoader.get("CHECKIN_SIGNING_SECRET")
            ?: appConfig.propertyOrNull("checkin.signingSecret")
                ?.getString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }

        if (configured != null) {
            if (configured.length < 16) {
                logger.warn(
                    "[CheckinCode]: checkin.signingSecret 长度不足 16 位，强度偏弱，" +
                        "生产环境请配置至少 32 位的随机密钥"
                )
            }
            return configured.toByteArray(Charsets.UTF_8)
        }

        // 未配置：一次性随机密钥，仅限本地联调。同一进程内 admin 签发 / user 验签仍然一致。
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        logger.warn(
            "[CheckinCode]: 未配置 checkin.signingSecret（或环境变量 CHECKIN_SIGNING_SECRET），" +
                "已生成一次性随机密钥，重启后所有已打印签到码失效。仅限本地开发，上线前必须配置稳定密钥"
        )
        return random
    }
}
