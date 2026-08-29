package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.service.AdminDashboardService
import com.dentalclinic.admin.util.ok
import io.ktor.server.routing.*

fun Route.dashboardRoutes(dashboardService: AdminDashboardService) {

    route("/dashboard") {
        authenticated {
            requirePermission("dashboard:view") {

                // GET /api/admin/dashboard/summary — 实时概览
                get("/summary") {
                    val summary = dashboardService.getSummary()
                    call.ok(summary)
                }

                // GET /api/admin/dashboard/clinic-comparison — 门店对比
                get("/clinic-comparison") {
                    val comparison = dashboardService.getClinicComparison()
                    call.ok(comparison)
                }

                // GET /api/admin/dashboard/trends?days=30 — 趋势数据
                get("/trends") {
                    val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
                    val trends = dashboardService.getTrends(days.coerceIn(7, 90))
                    call.ok(trends)
                }

                // GET /api/admin/dashboard/customer-levels — 客户等级分布
                get("/customer-levels") {
                    val distribution = dashboardService.getCustomerLevelDistribution()
                    call.ok(distribution)
                }

                // GET /api/admin/dashboard/customer-sources — 客户来源分布
                get("/customer-sources") {
                    val distribution = dashboardService.getCustomerSourceDistribution()
                    call.ok(distribution)
                }

                // GET /api/admin/dashboard/referral — 推荐统计
                get("/referral") {
                    val stats = dashboardService.getReferralStats()
                    call.ok(stats)
                }

                // GET /api/admin/dashboard/retention — 客户留存
                get("/retention") {
                    val retention = dashboardService.getCustomerRetention()
                    call.ok(retention)
                }
            }
        }
    }
}
