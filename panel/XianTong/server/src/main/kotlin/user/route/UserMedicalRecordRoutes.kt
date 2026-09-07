package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.dto.response.PageResponse
import com.dentalclinic.user.service.MedicalRecordService
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * User medical record routes (protected by JWT).
 */
fun Application.userMedicalRecordRoutes() {
    val medicalRecordService = MedicalRecordService()

    routing {
        route("/api/v1/medical-records") {
            /**
             * Get user's medical records (completed appointments).
             * GET /api/v1/medical-records?page=1&pageSize=20
             */
            get("") {
                val userId = authenticate(call) ?: return@get
                val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                val response = medicalRecordService.getMedicalRecords(userId, page, pageSize)
                call.respond(ApiResponse(
                    code = 0,
                    message = "success",
                    data = PageResponse(
                        list = response.list,
                        total = response.total,
                        page = response.page,
                        pageSize = response.pageSize
                    )
                ))
            }

            /**
             * Get medical record detail by appointment ID.
             * GET /api/v1/medical-records/{id}
             */
            get("/{id}") {
                val userId = authenticate(call) ?: return@get
                val appointmentId = call.parameters["id"]?.toLongOrNull()

                if (appointmentId == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "无效的预约ID"))
                    return@get
                }

                val detail = medicalRecordService.getMedicalRecordDetail(userId, appointmentId)
                if (detail != null) {
                    call.respond(ApiResponse(code = 0, message = "success", data = detail))
                } else {
                    call.respond(ApiResponse<Unit>(code = 404, message = "就诊记录不存在"))
                }
            }
        }
    }
}
