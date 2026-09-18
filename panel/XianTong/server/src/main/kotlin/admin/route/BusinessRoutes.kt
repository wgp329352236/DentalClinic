package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.clinicScope
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.service.*
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import com.dentalclinic.common.security.CheckinCodeService
import io.ktor.server.request.*
import io.ktor.server.routing.*

// ==================== 门诊管理 ====================

fun Route.clinicRoutes(clinicService: ClinicService, checkinCodeService: CheckinCodeService) {
    route("/clinics") {
        authenticated {
            requirePermission("clinic:list") {
                get {
                    val data = clinicService.listClinics(call.clinicScope())
                    call.ok(data)
                }
            }
            // 取门店到店签到码（HMAC 签名，确定性输出，可重复打印）
            requirePermission("clinic:list") {
                get("/{id}/checkin-code") {
                    val id = call.parameters["id"]?.toLongOrNull()
                        ?: throw ValidationException("无效的ID")
                    val clinic = clinicService.getClinic(id)
                        ?: throw ValidationException("门诊不存在")
                    val code = checkinCodeService.issue(id)
                    call.ok(
                        mapOf(
                            "clinicId" to id,
                            "name" to clinic.name,
                            "code" to code
                        )
                    )
                }
            }
            requirePermission("clinic:create") {
                post {
                    val req = call.receive<ClinicRequest>()
                    val id = clinicService.createClinic(req)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("clinic:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<ClinicRequest>()
                    clinicService.updateClinic(id, req)
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("clinic:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    clinicService.deleteClinic(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

// ==================== 科室管理 ====================

fun Route.departmentRoutes(departmentService: DepartmentService) {
    route("/departments") {
        authenticated {
            requirePermission("department:list") {
                get {
                    val cid = call.request.queryParameters["clinicId"]?.toLongOrNull()
                    val data = departmentService.listDepartments(cid)
                    call.ok(data)
                }
            }
            requirePermission("department:create") {
                post {
                    val req = call.receive<DepartmentRequest>()
                    val id = departmentService.createDepartment(req)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("department:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<DepartmentRequest>()
                    departmentService.updateDepartment(id, req)
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("department:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    departmentService.deleteDepartment(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

// ==================== 医生管理 ====================

fun Route.doctorRoutes(doctorService: DoctorService) {
    route("/doctors") {
        authenticated {
            requirePermission("doctor:list") {
                get {
                    val data = doctorService.listDoctors()
                    call.ok(data)
                }
            }
            requirePermission("doctor:create") {
                post {
                    val req = call.receive<DoctorRequest>()
                    val id = doctorService.createDoctor(req)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("doctor:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<DoctorRequest>()
                    doctorService.updateDoctor(id, req)
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("doctor:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    doctorService.deleteDoctor(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

// ==================== 排班管理 ====================

fun Route.scheduleRoutes(scheduleService: ScheduleService) {
    route("/schedules") {
        authenticated {
            requirePermission("doctor:list") {
                get {
                    val did = call.request.queryParameters["doctorId"]?.toLongOrNull()
                        ?: throw ValidationException("缺少医生ID")
                    val data = scheduleService.listSchedules(did)
                    call.ok(data)
                }
            }
            requirePermission("doctor:update") {
                post {
                    val req = call.receive<ScheduleRequest>()
                    val id = scheduleService.createSchedule(req)
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("doctor:update") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    scheduleService.deleteSchedule(id)
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

// ==================== 预约管理 ====================

fun Route.appointmentAdminRoutes(appointmentService: AppointmentAdminService) {
    route("/appointments") {
        authenticated {
            requirePermission("appointment:list") {
                get {
                    val status = call.request.queryParameters["status"]
                    val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()
                    val doctorId = call.request.queryParameters["doctorId"]?.toLongOrNull()
                    // userId：供「录入消费」弹窗按所选客户联动其预约列表
                    val userId = call.request.queryParameters["userId"]?.toLongOrNull()
                    // dateFrom/dateTo：按就诊日期过滤（yyyy-MM-dd），时间轴看板按天查询时两者传同一天
                    val dateFrom = call.request.queryParameters["dateFrom"]
                    val dateTo = call.request.queryParameters["dateTo"]
                    // 分页参数保持可选：不传则返回全量，下拉选择器依赖这一行为一次拿全
                    val page = call.request.queryParameters["page"]?.toIntOrNull()
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()
                    val data = appointmentService.listAppointments(
                        status = status,
                        clinicId = clinicId,
                        doctorId = doctorId,
                        userId = userId,
                        dateFrom = dateFrom,
                        dateTo = dateTo,
                        scope = call.clinicScope(),
                        page = page?.coerceAtLeast(1),
                        pageSize = pageSize?.coerceIn(1, 100)
                    )
                    call.ok(data)
                }
            }

            // POST /api/admin/appointments - 代客预约（前台/客服替客户下单，默认 confirmed）
            requirePermission("appointment:create") {
                post {
                    val req = call.receive<AdminCreateAppointmentRequest>()
                    val id = appointmentService.createAppointment(req, call.clinicScope())
                    call.ok(mapOf("id" to id), "代客预约成功")
                }
            }

            requirePermission("appointment:update") {
                put("/{id}/status") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<AppointmentStatusRequest>()
                    appointmentService.updateStatus(id, req)
                    call.ok<Unit>(message = "状态更新成功")
                }
            }

            requirePermission("appointment:update") {
                put("/{id}/cancel") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<Map<String, String?>>()
                    appointmentService.cancelAppointment(id, req["reason"])
                    call.ok<Unit>(message = "预约已取消")
                }
            }

            // PUT /api/admin/appointments/{id}/confirm - 确认预约（仅 pending 可确认）
            requirePermission("appointment:update") {
                put("/{id}/confirm") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    appointmentService.confirmAppointment(id)
                    call.ok<Unit>(message = "预约已确认")
                }
            }

            // PUT /api/admin/appointments/{id}/reject - 拒绝预约（仅 pending 可拒绝，需填原因）
            requirePermission("appointment:update") {
                put("/{id}/reject") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<Map<String, String?>>()
                    val reason = req["reason"]?.trim().orEmpty()
                    if (reason.isEmpty()) throw ValidationException("请填写拒绝原因")
                    appointmentService.rejectAppointment(id, reason)
                    call.ok<Unit>(message = "预约已拒绝")
                }
            }

            // GET /api/admin/appointments/{id} - 查看预约详情（含病历）
            requirePermission("appointment:view") {
                get("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val response = appointmentService.getAppointmentDetail(id)
                    call.ok(response)
                }
            }

            // PUT /api/admin/appointments/{id}/medical-record - 编辑病历摘要
            requirePermission("appointment:update") {
                put("/{id}/medical-record") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<MedicalRecordUpdateRequest>()
                    appointmentService.updateMedicalRecord(id, req)
                    call.ok<Unit>(message = "病历更新成功")
                }
            }
        }
    }
}

// ==================== 客户管理 ====================

fun Route.customerRoutes(customerService: CustomerService) {
    route("/customers") {
        authenticated {
            requirePermission("customer:list") {
                get {
                    val keyword = call.request.queryParameters["keyword"]
                    val clinicId = call.request.queryParameters["clinicId"]?.toLongOrNull()
                    val tagId = call.request.queryParameters["tagId"]?.toLongOrNull()
                    // 分页参数保持可选：不传则返回全量，「录入消费」弹窗的客户下拉依赖这一行为
                    val page = call.request.queryParameters["page"]?.toIntOrNull()
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull()
                    val data = customerService.listCustomers(
                        keyword = keyword,
                        clinicId = clinicId,
                        scope = call.clinicScope(),
                        page = page?.coerceAtLeast(1),
                        pageSize = pageSize?.coerceIn(1, 100),
                        tagId = tagId
                    )
                    call.ok(data)
                }
            }

            requirePermission("customer:view") {
                get("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的客户ID")
                    val data = customerService.getCustomerDetail(id)
                    call.ok(data)
                }
            }

            requirePermission("customer:update") {
                put("/{id}/wallet") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的客户ID")
                    val req = call.receive<WalletAdjustRequest>()
                    // 资金调整必须留痕：reason 落入 wallet_transaction，operator 记录是谁调的
                    val principal = call.adminPrincipal()
                    val result = customerService.adjustWallet(id, req.amount, req.reason, principal.username)
                    call.ok(result, "余额调整成功")
                }
            }

            requirePermission("customer:update") {
                put("/{id}/level") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的客户ID")
                    val req = call.receive<LevelAdjustRequest>()
                    customerService.adjustLevel(id, req.levelCode)
                    call.ok<Unit>(message = "会员等级调整成功")
                }
            }

            requirePermission("customer:update") {
                put("/{id}/points") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的客户ID")
                    val req = call.receive<PointsAdjustRequest>()
                    val principal = call.adminPrincipal()
                    customerService.adjustPoints(id, req, principal.username)
                    call.ok<Unit>(message = "积分调整成功")
                }
            }
        }
    }
}
