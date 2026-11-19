package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.clinicScope
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.service.AdminDashboardService
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * 数据看板路由（需求 4.10）。
 *
 * 全部端点为只读统计，统一要求 dashboard:view 权限。该权限由 DataSeedService 播种，
 * 且 RbacInterceptor 没有 super_admin 通配绕过——权限码不在 JWT 的 permissions 里就是 403。
 */
fun Route.dashboardRoutes(dashboardService: AdminDashboardService) {

    route("/dashboard") {
        authenticated {
            requirePermission("dashboard:view") {

                // GET /api/admin/dashboard/summary — 实时概览
                get("/summary") {
                    val summary = dashboardService.getSummary(call.clinicScope())
                    call.ok(summary)
                }

                // GET /api/admin/dashboard/clinic-comparison — 门店对比
                get("/clinic-comparison") {
                    val comparison = dashboardService.getClinicComparison(call.clinicScope())
                    call.ok(comparison)
                }

                // GET /api/admin/dashboard/trends?days=30 — 趋势数据
                get("/trends") {
                    // days 非法或缺省时回落到 30；上限 90 防止把趋势接口变成全表扫描
                    val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 30
                    val trends = dashboardService.getTrends(days.coerceIn(7, 90), call.clinicScope())
                    call.ok(trends)
                }

                // GET /api/admin/dashboard/customer-levels — 客户等级分布
                get("/customer-levels") {
                    val distribution = dashboardService.getCustomerLevelDistribution(call.clinicScope())
                    call.ok(distribution)
                }

                // GET /api/admin/dashboard/customer-sources — 客户来源分布
                get("/customer-sources") {
                    val distribution = dashboardService.getCustomerSourceDistribution(call.clinicScope())
                    call.ok(distribution)
                }

                // GET /api/admin/dashboard/referral — 推荐统计
                get("/referral") {
                    val stats = dashboardService.getReferralStats(call.clinicScope())
                    call.ok(stats)
                }

                // GET /api/admin/dashboard/retention — 客户留存
                get("/retention") {
                    val retention = dashboardService.getCustomerRetention(call.clinicScope())
                    call.ok(retention)
                }
            }
        }
    }
}
