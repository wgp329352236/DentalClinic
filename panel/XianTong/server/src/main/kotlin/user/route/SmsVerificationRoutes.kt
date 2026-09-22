package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.common.security.SmsVerificationService
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * 短信验证码路由（P1-10：手机号修改验证码校验）。
 */
fun Application.smsVerificationRoutes(smsService: SmsVerificationService) {
    routing {
        route("/api/v1/sms") {
            userAuthenticated {
                // 发送验证码
                post("/send") {
                    val request = call.receive<SendSmsRequest>()
                    val result = smsService.sendVerificationCode(request.phone)

                    when (result) {
                        is SmsVerificationService.SendResult.Success -> {
                            call.respond(ApiResponse.ok<Any>(message = "验证码已发送"))
                        }
                        is SmsVerificationService.SendResult.Error -> {
                            call.respond(ApiResponse.error<Any>(400, result.message))
                        }
                    }
                }

                // 验证验证码
                post("/verify") {
                    val request = call.receive<VerifySmsRequest>()
                    val result = smsService.verifyCode(request.phone, request.code)

                    when (result) {
                        is SmsVerificationService.VerifyResult.Success -> {
                            call.respond(ApiResponse.ok<Any>(message = "验证成功"))
                        }
                        is SmsVerificationService.VerifyResult.Error -> {
                            call.respond(ApiResponse.error<Any>(400, result.message))
                        }
                    }
                }
            }
        }
    }
}

@Serializable
data class SendSmsRequest(val phone: String)

@Serializable
data class VerifySmsRequest(val phone: String, val code: String)
