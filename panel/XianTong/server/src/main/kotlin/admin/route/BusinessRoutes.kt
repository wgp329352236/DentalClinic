package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.response.*
import com.dentalclinic.admin.entity.*
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import com.dentalclinic.config.DBConfig
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.r2dbc.deleteWhere
import org.jetbrains.exposed.v1.r2dbc.insert
import org.jetbrains.exposed.v1.r2dbc.selectAll
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.r2dbc.update

@Serializable data class ClinicRequest(val name: String, val address: String, val phone: String, val openTime: String, val closeTime: String, val description: String? = null, val coverImage: String? = null, val status: Int = 1, val sortOrder: Int = 0)
@Serializable data class DepartmentRequest(val clinicId: Long, val name: String, val description: String? = null)
@Serializable data class DoctorRequest(val name: String, val title: String, val specialties: String, val introduction: String? = null, val avatar: String? = null, val clinicIds: List<Long> = emptyList())
@Serializable data class ScheduleRequest(val doctorId: Long, val dayOfWeek: Int, val startTime: String, val endTime: String, val maxAppointments: Int = 1)

fun Route.clinicRoutes() {
    route("/clinics") {
        authenticated {
            requirePermission("clinic:list") {
                get {
                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Clinics.selectAll()
                                .orderBy(Clinics.sortOrder, SortOrder.ASC)
                                .toList()
                                .map { r ->
                                    ClinicResponse(
                                        id = r[Clinics.id],
                                        name = r[Clinics.name],
                                        address = r[Clinics.address],
                                        phone = r[Clinics.phone],
                                        openTime = r[Clinics.openTime],
                                        closeTime = r[Clinics.closeTime],
                                        description = r[Clinics.description],
                                        coverImage = r[Clinics.coverImage],
                                        status = r[Clinics.status],
                                        sortOrder = r[Clinics.sortOrder]
                                    )
                                }
                        }
                    }
                    call.ok(data)
                }
            }
            requirePermission("clinic:create") {
                post {
                    val req = call.receive<ClinicRequest>()
                    val id = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Clinics.insert {
                                it[name] = req.name
                                it[address] = req.address
                                it[phone] = req.phone
                                it[openTime] = req.openTime
                                it[closeTime] = req.closeTime
                                it[description] = req.description
                                it[coverImage] = req.coverImage
                                it[status] = req.status
                                it[sortOrder] = req.sortOrder
                            } get Clinics.id
                        }
                    }
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("clinic:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<ClinicRequest>()
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Clinics.update({ Clinics.id eq id }) {
                                it[name] = req.name
                                it[address] = req.address
                                it[phone] = req.phone
                                it[openTime] = req.openTime
                                it[closeTime] = req.closeTime
                                it[description] = req.description
                                it[coverImage] = req.coverImage
                                it[status] = req.status
                                it[sortOrder] = req.sortOrder
                            }
                        }
                    }
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("clinic:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Clinics.deleteWhere { Clinics.id eq id }
                        }
                    }
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

fun Route.departmentRoutes() {
    route("/departments") {
        authenticated {
            requirePermission("department:list") {
                get {
                    val cid = call.request.queryParameters["clinicId"]?.toLongOrNull()
                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            val q = Departments.selectAll().where { Departments.status eq 1 }
                            val rows = if (cid != null) {
                                q.where { Departments.clinicId eq cid }.toList()
                            } else {
                                q.toList()
                            }
                            rows.map { r ->
                                DepartmentResponse(
                                    id = r[Departments.id],
                                    clinicId = r[Departments.clinicId],
                                    name = r[Departments.name],
                                    description = r[Departments.description]
                                )
                            }
                        }
                    }
                    call.ok(data)
                }
            }
            requirePermission("department:create") {
                post {
                    val req = call.receive<DepartmentRequest>()
                    val id = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Departments.insert {
                                it[clinicId] = req.clinicId
                                it[name] = req.name
                                it[description] = req.description
                                it[status] = 1
                            } get Departments.id
                        }
                    }
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("department:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<DepartmentRequest>()
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Departments.update({ Departments.id eq id }) {
                                it[clinicId] = req.clinicId
                                it[name] = req.name
                                it[description] = req.description
                            }
                        }
                    }
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("department:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Departments.deleteWhere { Departments.id eq id }
                        }
                    }
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

fun Route.doctorRoutes() {
    route("/doctors") {
        authenticated {
            requirePermission("doctor:list") {
                get {
                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Doctors.selectAll().where { Doctors.status eq 1 }.toList().map { row ->
                                val cids = DoctorClinicRels.selectAll()
                                    .where { DoctorClinicRels.doctorId eq row[Doctors.id] }
                                    .toList()
                                    .map { it[DoctorClinicRels.clinicId] }
                                DoctorResponse(
                                    id = row[Doctors.id],
                                    name = row[Doctors.name],
                                    title = row[Doctors.title],
                                    specialties = row[Doctors.specialties],
                                    introduction = row[Doctors.introduction],
                                    avatar = row[Doctors.avatar],
                                    clinicIds = cids
                                )
                            }
                        }
                    }
                    call.ok(data)
                }
            }
            requirePermission("doctor:create") {
                post {
                    val req = call.receive<DoctorRequest>()
                    val did = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            val id = Doctors.insert {
                                it[name] = req.name
                                it[title] = req.title
                                it[specialties] = req.specialties
                                it[introduction] = req.introduction
                                it[avatar] = req.avatar
                                it[status] = 1
                            } get Doctors.id
                            for (c in req.clinicIds) {
                                DoctorClinicRels.insert {
                                    it[doctorId] = id
                                    it[clinicId] = c
                                }
                            }
                            id
                        }
                    }
                    call.ok(mapOf("id" to did), "创建成功")
                }
            }
            requirePermission("doctor:update") {
                put("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    val req = call.receive<DoctorRequest>()
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            Doctors.update({ Doctors.id eq id }) {
                                it[name] = req.name
                                it[title] = req.title
                                it[specialties] = req.specialties
                                it[introduction] = req.introduction
                                it[avatar] = req.avatar
                            }
                            DoctorClinicRels.deleteWhere { DoctorClinicRels.doctorId eq id }
                            for (c in req.clinicIds) {
                                DoctorClinicRels.insert {
                                    it[doctorId] = id
                                    it[clinicId] = c
                                }
                            }
                        }
                    }
                    call.ok<Unit>(message = "更新成功")
                }
            }
            requirePermission("doctor:delete") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            DoctorClinicRels.deleteWhere { DoctorClinicRels.doctorId eq id }
                            Doctors.deleteWhere { Doctors.id eq id }
                        }
                    }
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}

fun Route.scheduleRoutes() {
    route("/schedules") {
        authenticated {
            requirePermission("doctor:list") {
                get {
                    val did = call.request.queryParameters["doctorId"]?.toLongOrNull()
                        ?: throw ValidationException("缺少医生ID")
                    val data = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            DoctorSchedules.selectAll()
                                .where { DoctorSchedules.doctorId eq did }
                                .orderBy(DoctorSchedules.dayOfWeek, SortOrder.ASC)
                                .orderBy(DoctorSchedules.startTime, SortOrder.ASC)
                                .toList()
                                .map { r ->
                                    ScheduleResponse(
                                        id = r[DoctorSchedules.id],
                                        doctorId = r[DoctorSchedules.doctorId],
                                        dayOfWeek = r[DoctorSchedules.dayOfWeek],
                                        startTime = r[DoctorSchedules.startTime],
                                        endTime = r[DoctorSchedules.endTime],
                                        maxAppointments = r[DoctorSchedules.maxAppointments],
                                        isEnabled = r[DoctorSchedules.isEnabled]
                                    )
                                }
                        }
                    }
                    call.ok(data)
                }
            }
            requirePermission("doctor:update") {
                post {
                    val req = call.receive<ScheduleRequest>()
                    val id = withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            DoctorSchedules.insert {
                                it[doctorId] = req.doctorId
                                it[dayOfWeek] = req.dayOfWeek
                                it[startTime] = req.startTime
                                it[endTime] = req.endTime
                                it[maxAppointments] = req.maxAppointments
                                it[isEnabled] = 1
                            } get DoctorSchedules.id
                        }
                    }
                    call.ok(mapOf("id" to id), "创建成功")
                }
            }
            requirePermission("doctor:update") {
                delete("/{id}") {
                    val id = call.parameters["id"]?.toLongOrNull() ?: throw ValidationException("无效的ID")
                    withContext(Dispatchers.IO) {
                        suspendTransaction(DBConfig.database) {
                            DoctorSchedules.deleteWhere { DoctorSchedules.id eq id }
                        }
                    }
                    call.ok<Unit>(message = "删除成功")
                }
            }
        }
    }
}
