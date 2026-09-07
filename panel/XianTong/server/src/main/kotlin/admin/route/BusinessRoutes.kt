package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.service.*
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

// ==================== 门诊管理 ====================

fun Route.clinicRoutes(clinicService: ClinicService) {
    route("/clinics") {
        authenticated {
            requirePermission("clinic:list") {
                get {
                    val data = clinicService.listClinics()
                    call.ok(data)
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
                    val data = appointmentService.listAppointments(status, clinicId, doctorId)
                    call.ok(data)
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
                    val data = customerService.listCustomers(keyword, clinicId)
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
                    customerService.adjustWallet(id, req.amount)
                    call.ok<Unit>(message = "余额调整成功")
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
