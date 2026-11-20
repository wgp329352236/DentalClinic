package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import com.dentalclinic.user.service.AppointmentCancelResult
import com.dentalclinic.user.service.AppointmentResult
import com.dentalclinic.user.service.AppointmentService
import com.dentalclinic.user.service.CreateAppointmentRequest
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.time.LocalDate

private val logger = LoggerFactory.getLogger("UserAppointmentRoutes")

/**
 * User appointment routes.
 */
fun Application.userAppointmentRoutes(appointmentService: AppointmentService) {
    routing {
        route("/api/v1") {
            // === Public routes (no auth required) ===

            /**
             * Get clinic list.
             * GET /api/v1/clinics
             */
            get("/clinics") {
                val clinics = appointmentService.getClinics()
                call.respond(ApiResponse(code = 0, message = "success", data = clinics))
            }

            /**
             * Get departments for a clinic.
             * GET /api/v1/departments?clinicId=1
             */
            get("/departments") {
                val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()
                if (clinicId == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "缺少诊所ID参数"))
                    return@get
                }
                val departments = appointmentService.getDepartments(clinicId)
                call.respond(ApiResponse(code = 0, message = "success", data = departments))
            }

            /**
             * Get doctors for a clinic.
             * GET /api/v1/doctors?clinicId=1&departmentId=2
             */
            get("/doctors") {
                val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()
                if (clinicId == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "缺少诊所ID参数"))
                    return@get
                }
                val departmentId = call.request.queryParameters["departmentId"]?.toLongOrNull()
                val doctors = appointmentService.getDoctors(clinicId, departmentId)
                call.respond(ApiResponse(code = 0, message = "success", data = doctors))
            }

            /**
             * Get available time slots for a doctor on a date.
             * GET /api/v1/appointments/slots?doctorId=1&date=2026-08-30
             */
            get("/appointments/slots") {
                val doctorId = call.request.queryParameters["doctorId"]?.toLongOrNull()
                val dateStr = call.request.queryParameters["date"]

                if (doctorId == null || dateStr == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "缺少医生ID或日期参数"))
                    return@get
                }

                val date = try {
                    LocalDate.parse(dateStr)
                } catch (e: Exception) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "日期格式错误，请使用 yyyy-MM-dd"))
                    return@get
                }

                val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()
                val slots = appointmentService.getAvailableSlots(doctorId, date, clinicId)
                call.respond(ApiResponse(code = 0, message = "success", data = slots))
            }

            // === Protected routes (auth required) ===
            userAuthenticated {

                /**
                 * Create an appointment.
                 * POST /api/v1/appointments
                 */
                post("/appointments") {
                    val userId = call.userId()

                    try {
                        val request = call.receive<CreateAppointmentRequestDto>()
                        val result = appointmentService.createAppointment(userId, CreateAppointmentRequest(
                            clinicId = request.clinicId,
                            doctorId = request.doctorId,
                            departmentId = request.departmentId,
                            appointmentDate = request.appointmentDate,
                            startTime = request.startTime,
                            remark = request.remark,
                            couponCode = request.couponCode
                        ))

                        when (result) {
                            is AppointmentResult.Success -> {
                                call.respond(ApiResponse(
                                    code = 0,
                                    message = "预约成功",
                                    data = CreateAppointmentResponseDto(result.appointmentId)
                                ))
                            }
                            is AppointmentResult.Error -> {
                                call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                            }
                        }
                    } catch (e: Exception) {
                        // 业务错误已由 AppointmentResult.Error 走 400 分支，落到这里的只能是底层异常
                        val (code, message) = translateApiError(
                            logger, "[Appointment]: 用户 $userId 创建预约", "预约失败，请稍后重试", e
                        )
                        call.respond(ApiResponse<Unit>(code = code, message = message))
                    }
                }

                /**
                 * Get my appointments.
                 * GET /api/v1/appointments/my?status=pending
                 */
                get("/appointments/my") {
                    val userId = call.userId()
                    val status = call.request.queryParameters["status"]

                    val appointments = appointmentService.getMyAppointments(userId, status)
                    call.respond(ApiResponse(code = 0, message = "success", data = appointments))
                }

                /**
                 * Cancel an appointment.
                 * PUT /api/v1/appointments/{id}/cancel
                 */
                put("/appointments/{id}/cancel") {
                    val userId = call.userId()
                    val appointmentId = call.parameters["id"]?.toLongOrNull()

                    if (appointmentId == null) {
                        call.respond(ApiResponse<Unit>(code = 400, message = "无效的预约ID"))
                        return@put
                    }

                    val result = appointmentService.cancelAppointment(userId, appointmentId)
                    when (result) {
                        is AppointmentCancelResult.Cancelled -> {
                            call.respond(ApiResponse<Unit>(code = 0, message = "取消成功"))
                        }
                        is AppointmentCancelResult.Requested -> {
                            call.respond(ApiResponse<Unit>(code = 0, message = "取消申请已提交，请等待门店确认"))
                        }
                        is AppointmentCancelResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                }

                /**
                 * 扫码到店签到。
                 * POST /api/v1/appointments/{id}/checkin
                 * Body: { "code": "dc1.1.<sig>" } —— 小程序扫描门店码后把码原文整体上报。
                 * 签名真伪由服务层用密钥验签，伪造/明文码一律拒绝；门店营业状态、
                 * 与预约门店一致性、归属/状态/当天均由服务层校验。
                 */
                post("/appointments/{id}/checkin") {
                    val userId = call.userId()
                    val appointmentId = call.parameters["id"]?.toLongOrNull()

                    if (appointmentId == null) {
                        call.respond(ApiResponse<Unit>(code = 400, message = "无效的预约ID"))
                        return@post
                    }

                    val request = try {
                        call.receive<CheckInRequestDto>()
                    } catch (e: Exception) {
                        // body 缺失/不是合法 JSON：给出业务化文案，避免抛出序列化原始异常
                        call.respond(ApiResponse<Unit>(code = 400, message = "无效的门店码，请扫描门店前台二维码"))
                        return@post
                    }

                    val code = request.code?.trim().orEmpty()
                    if (code.isEmpty()) {
                        call.respond(ApiResponse<Unit>(code = 400, message = "无效的门店码，请扫描门店前台二维码"))
                        return@post
                    }

                    val result = appointmentService.checkIn(userId, appointmentId, code)
                    when (result) {
                        is AppointmentResult.Success -> {
                            call.respond(ApiResponse<Unit>(code = 0, message = "签到成功"))
                        }
                        is AppointmentResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                        }
                    }
                }

                /**
                 * Reschedule an appointment.
                 * PUT /api/v1/appointments/{id}/reschedule
                 */
                put("/appointments/{id}/reschedule") {
                    val userId = call.userId()
                    val appointmentId = call.parameters["id"]?.toLongOrNull()

                    if (appointmentId == null) {
                        call.respond(ApiResponse<Unit>(code = 400, message = "无效的预约ID"))
                        return@put
                    }

                    try {
                        val request = call.receive<RescheduleRequest>()
                        val result = appointmentService.rescheduleAppointment(
                            userId = userId,
                            appointmentId = appointmentId,
                            newDate = request.appointmentDate,
                            newStartTime = request.startTime,
                            couponCode = request.couponCode
                        )

                        when (result) {
                            is AppointmentResult.Success -> {
                                call.respond(ApiResponse<Unit>(code = 0, message = "改约成功"))
                            }
                            is AppointmentResult.Error -> {
                                call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                            }
                        }
                    } catch (e: Exception) {
                        val (code, message) = translateApiError(
                            logger,
                            "[Appointment]: 用户 $userId 改约, appointmentId=$appointmentId",
                            "改约失败，请稍后重试",
                            e
                        )
                        call.respond(ApiResponse<Unit>(code = code, message = message))
                    }
                }
            }
        }
    }
}

@Serializable
data class RescheduleRequest(
    val appointmentDate: String,
    val startTime: String,
    /**
     * null：保留原预约优惠券；空字符串：不使用优惠券并释放原券；其他值：更换为该券。
     */
    val couponCode: String? = null
)

/**
 * 扫码签到请求体。
 * code 为门店前台张贴的 HMAC 签名码原文（格式 dc1.<clinicId>.<signature>），
 * 由后端验签并解析出门店；声明为可空是为了在缺参时返回业务化文案，
 * 而不是让 kotlinx.serialization 直接抛 400 反序列化异常。
 */
@Serializable
data class CheckInRequestDto(val code: String? = null)

@Serializable
data class CreateAppointmentRequestDto(
    val clinicId: Long,
    val doctorId: Long,
    val departmentId: Long? = null,
    val appointmentDate: String,  // yyyy-MM-dd format
    val startTime: String,
    val remark: String? = null,
    val couponCode: String? = null
)

@Serializable
data class CreateAppointmentResponseDto(val appointmentId: Long)
