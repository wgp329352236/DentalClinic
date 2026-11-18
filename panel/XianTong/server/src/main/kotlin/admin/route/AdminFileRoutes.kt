package com.dentalclinic.admin.route

import com.dentalclinic.admin.auth.authenticated
import com.dentalclinic.admin.auth.requirePermission
import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.admin.util.ValidationException
import com.dentalclinic.admin.util.ok
import com.dentalclinic.common.FileDeleteResult
import com.dentalclinic.common.FileUploadResult
import com.dentalclinic.common.FileUploadService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdminFileRoutes")

/**
 * 上传结果响应体。
 *
 * 不能用 `mapOf("url" to ..., "size" to ...)`：map 里 String 与 Long 混合会被推断成
 * `Map<String, Any>`，kotlinx.serialization 找不到 `Any` 的序列化器，响应阶段直接 400，
 * 前端会把已成功的上传判为失败。
 */
@Serializable
data class UploadedFileDto(
    val url: String,
    val fileName: String,
    val size: Long
)

/**
 * 管理端文件上传路由。
 *
 * 为什么不复用 C 端的 `/api/v1/files/upload`：那个端点包在 `userAuthenticated` 里，
 * 校验的是 C 端用户 JWT，管理员令牌进不去。此前管理端因此完全没有上传能力，
 * 门店封面图、医生头像、轮播图都只能手填 URL 字符串。
 *
 * 文件的读取仍走公开的 `GET /api/v1/files/{fileName}`（已含路径越界防护），
 * 因此这里返回的 url 与 C 端上传的格式一致，前端无需区分来源。
 */
fun Route.adminFileRoutes(fileUploadService: FileUploadService) {
    route("/files") {
        authenticated {
            // POST /api/admin/files/upload —— 轮播图/门店封面/医生头像等后台素材上传
            requirePermission("content:manage") {
                post("/upload") {
                    // 读取 multipart 与业务校验分两段：前者可能抛磁盘/网络类异常需包装成 500，
                    // 后者抛的 ValidationException 要原样冒泡给 StatusPages 映射成 400，
                    // 混在同一 try 里就得再写一个 catch-rethrow 分支
                    val result = try {
                        val multipart = call.receiveMultipart()
                        var uploaded: FileUploadResult? = null
            
                        multipart.forEachPart { part ->
                            if (part is PartData.FileItem) {
                                // 管理端上传没有 C 端用户身份：userId 约定写 0，归属靠 ownerType 区分
                                uploaded = fileUploadService.saveFile(
                                    userId = FileUploadService.ADMIN_OWNER_ID,
                                    part = part,
                                    ownerType = FileUploadService.OWNER_ADMIN
                                )
                            }
                            part.release()
                        }
                        uploaded
                    } catch (e: Exception) {
                        // 不回传 e.message：磁盘/权限类异常文本会暴露服务器绝对路径
                        logger.error("[AdminFileUpload]: 上传失败", e)
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ApiResponse<Unit>(code = 500, message = "上传失败，请稍后重试")
                        )
                        return@post
                    }
            
                    when (result) {
                        is FileUploadResult.Success -> call.ok(
                            UploadedFileDto(
                                url = result.url,
                                fileName = result.fileName,
                                size = result.size
                            ),
                            "上传成功"
                        )
            
                        is FileUploadResult.Error -> throw ValidationException(result.message)
                        null -> throw ValidationException("未找到上传文件")
                    }
                }
            }

            // DELETE /api/admin/files/{fileName} —— 删除后台上传的素材，避免换图后残留孤儿文件
            requirePermission("content:manage") {
                delete("/{fileName}") {
                    val fileName = call.parameters["fileName"]
                        ?: throw ValidationException("缺少文件名参数")

                    when (
                        val result = fileUploadService.deleteFile(
                            userId = FileUploadService.ADMIN_OWNER_ID,
                            fileName = fileName,
                            ownerType = FileUploadService.OWNER_ADMIN
                        )
                    ) {
                        is FileDeleteResult.Success -> call.ok<Unit>(message = "删除成功")
                        // 不存在与无权限返回同一文案：不向调用方泄露「这个文件名确实存在」
                        is FileDeleteResult.Error -> throw ValidationException(result.message)
                    }
                }
            }
        }
    }
}
