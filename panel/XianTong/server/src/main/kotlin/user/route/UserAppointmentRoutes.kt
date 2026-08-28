package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.UserJwtConfig
import com.dentalclinic.user.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * User appointment routes.
 */
fun Application.userAppointmentRoutes() {
    val appointmentService = AppointmentService()

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

                val slots = appointmentService.getAvailableSlots(doctorId, date)
                call.respond(ApiResponse(code = 0, message = "success", data = slots))
            }

            // === Protected routes (auth required) ===

            /**
             * Create an appointment.
             * POST /api/v1/appointments
             */
            post("/appointments") {
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
                    return@post
                }

                val token = authHeader.removePrefix("Bearer ")
                val decoded = UserJwtConfig.verifyAccessToken(token)
                if (decoded == null) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
                    return@post
                }

                val userId = decoded.subject.toLong()

                try {
                    val request = call.receive<CreateAppointmentRequestDto>()
                    val result = appointmentService.createAppointment(userId, CreateAppointmentRequest(
                        clinicId = request.clinicId,
                        doctorId = request.doctorId,
                        departmentId = request.departmentId,
                        appointmentDate = request.appointmentDate,
                        startTime = request.startTime,
                        remark = request.remark
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
                    call.respond(ApiResponse<Unit>(code = 500, message = "预约失败: ${e.message}"))
                }
            }

            /**
             * Get my appointments.
             * GET /api/v1/appointments/my?status=pending
             */
            get("/appointments/my") {
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
                    return@get
                }

                val token = authHeader.removePrefix("Bearer ")
                val decoded = UserJwtConfig.verifyAccessToken(token)
                if (decoded == null) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
                    return@get
                }

                val userId = decoded.subject.toLong()
                val status = call.request.queryParameters["status"]

                val appointments = appointmentService.getMyAppointments(userId, status)
                call.respond(ApiResponse(code = 0, message = "success", data = appointments))
            }

            /**
             * Cancel an appointment.
             * PUT /api/v1/appointments/{id}/cancel
             */
            put("/appointments/{id}/cancel") {
                val authHeader = call.request.headers[HttpHeaders.Authorization]
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
                    return@put
                }

                val token = authHeader.removePrefix("Bearer ")
                val decoded = UserJwtConfig.verifyAccessToken(token)
                if (decoded == null) {
                    call.respond(ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
                    return@put
                }

                val userId = decoded.subject.toLong()
                val appointmentId = call.parameters["id"]?.toLongOrNull()

                if (appointmentId == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "无效的预约ID"))
                    return@put
                }

                val result = appointmentService.cancelAppointment(userId, appointmentId)
                when (result) {
                    is AppointmentResult.Success -> {
                        call.respond(ApiResponse<Unit>(code = 0, message = "取消成功"))
                    }
                    is AppointmentResult.Error -> {
                        call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                    }
                }
            }
        }
    }
}

@Serializable
data class CreateAppointmentRequestDto(
    val clinicId: Long,
    val doctorId: Long,
    val departmentId: Long?,
    val appointmentDate: String,  // yyyy-MM-dd format
    val startTime: String,
    val remark: String?
)

@Serializable
data class CreateAppointmentResponseDto(val appointmentId: Long)
