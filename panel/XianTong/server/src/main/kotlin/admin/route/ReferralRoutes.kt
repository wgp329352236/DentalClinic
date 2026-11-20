package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.adminPrincipal
import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.clinicScope
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.service.AdminReferralService
import com.dentalclinic.admin.service.GrantReferralRewardRequest
import com.dentalclinic.admin.service.GrantReferralRewardResult
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import io.ktor.server.request.*
import io.ktor.server.routing.*

/**
 * 后台推荐活动管理路由。
 */
fun Route.referralRoutes(referralService: AdminReferralService) {
    route("/referrals") {
        authenticated {
            // GET /api/admin/referrals/tree?userId=1 - 推荐链路树（需求 4.4 谁推荐了谁）
            requirePermission("referral:view") {
                get("/tree") {
                    val userId = call.request.queryParameters["userId"]?.toLongOrNull()
                        ?: throw ValidationException("缺少用户ID")
                    val depth = call.request.queryParameters["depth"]?.toIntOrNull()?.coerceIn(1, 5) ?: 3
                    val tree = referralService.getReferralTree(userId, depth, call.clinicScope())
                        ?: throw ValidationException("用户不存在")
                    call.ok(tree)
                }
            }

            // GET /api/admin/referrals/relations - 分页查询推荐关系列表
            requirePermission("referral:view") {
                get("/relations") {
                    val referrerId = call.request.queryParameters["referrerId"]?.toLongOrNull()
                    val rewardStatus = call.request.queryParameters["rewardStatus"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                    val response = referralService.getReferralRelations(
                        referrerId = referrerId,
                        rewardStatus = rewardStatus,
                        page = page.coerceAtLeast(1),
                        pageSize = pageSize.coerceIn(1, 100),
                        scope = call.clinicScope()
                    )
                    call.ok(response)
                }
            }

            // GET /api/admin/referrals/rewards - 分页查询推荐奖励发放记录
            requirePermission("referral:view") {
                get("/rewards") {
                    val referrerId = call.request.queryParameters["referrerId"]?.toLongOrNull()
                    val stage = call.request.queryParameters["stage"]
                    val status = call.request.queryParameters["status"]
                    val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
                    val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20

                    val response = referralService.getReferralRewards(
                        referrerId = referrerId,
                        stage = stage,
                        status = status,
                        page = page.coerceAtLeast(1),
                        pageSize = pageSize.coerceIn(1, 100),
                        scope = call.clinicScope()
                    )
                    call.ok(response)
                }
            }

            // GET /api/admin/referrals/statistics - 推荐活动汇总统计
            requirePermission("referral:view") {
                get("/statistics") {
                    val statistics = referralService.getReferralStatistics(call.clinicScope())
                    call.ok(statistics)
                }
            }

            // POST /api/admin/referrals/rewards/grant - 管理员手动发放推荐奖励
            requirePermission("referral:manage") {
                post("/rewards/grant") {
                    val request = call.receive<GrantReferralRewardRequest>()
                    val principal = call.adminPrincipal()

                    // 校验输入参数
                    if (request.rewardValue <= 0) {
                        throw ValidationException("奖励值必须大于0")
                    }
                    if (request.rewardType != "points" && request.rewardType != "balance") {
                        throw ValidationException("奖励类型仅支持 points 或 balance")
                    }

                    val result = referralService.grantReferralReward(
                        request = request,
                        operator = principal.username,
                        scope = call.clinicScope()
                    )

                    when (result) {
                        is GrantReferralRewardResult.Success -> {
                            call.ok(mapOf("rewardId" to result.rewardId), "发放成功")
                        }
                        is GrantReferralRewardResult.Error -> {
                            throw ValidationException(result.message)
                        }
                    }
                }
            }
        }
    }
}
