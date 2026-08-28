package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.service.AdminDashboardService
import com.dentalclinic.admin.util.PageQuery
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import com.dentalclinic.admin.util.okPage
import io.ktor.server.routing.*

fun Route.dashboardRoutes(dashboardService: AdminDashboardService) {

    route("/dashboard") {
        authenticated {
            requirePermission("dashboard:view") {

                // GET /api/admin/dashboard/summary
                get("/summary") {
                    val summary = dashboardService.getSummary()
                    call.ok(summary)
                }

                // GET /api/admin/dashboard/games
                get("/games") {
                    val volumes = dashboardService.getGameDataVolumes()
                    call.ok(volumes)
                }

                // GET /api/admin/dashboard/trends
                get("/trends") {
                    val trends = dashboardService.getTrends()
                    call.ok(trends)
                }

                // GET /api/admin/dashboard/channels/{gameCode}
                get("/channels/{gameCode}") {
                    val gameCode = call.parameters["gameCode"]
                        ?: throw ValidationException("缺少游戏代码")
                    val distribution = dashboardService.getChannelDistribution(gameCode)
                    call.ok(distribution)
                }

                // GET /api/admin/dashboard/push-records
                get("/push-records") {
                    val pq = PageQuery.from(call)
                    call.okPage(emptyList<String>(), 0L, pq.page, pq.pageSize)
                }
            }
        }
    }
}
