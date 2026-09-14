package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.common.FileDeleteResult
import com.dentalclinic.common.FileUploadResult
import com.dentalclinic.common.FileUploadService
import com.dentalclinic.user.auth.userAuthenticated
import com.dentalclinic.user.auth.userId
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.io.File

private val logger = LoggerFactory.getLogger("FileUploadRoutes")

/**
 * File upload routes.
 */
fun Application.fileUploadRoutes(fileUploadService: FileUploadService) {
    routing {
        route("/api/v1/files") {
            userAuthenticated {
            /**
             * Upload a file.
             * POST /api/v1/files/upload
             */
            post("/upload") {
                val userId = call.userId()

                try {
                    val multipart = call.receiveMultipart()
                    var result: FileUploadResult? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            result = fileUploadService.saveFile(userId, part)
                        }
                        part.release()
                    }

                    when (val res = result) {
                        is FileUploadResult.Success -> {
                            call.respond(ApiResponse(
                                code = 0,
                                message = "上传成功",
                                data = FileUploadResponseDto(
                                    url = res.url,
                                    fileName = res.fileName,
                                    size = res.size
                                )
                            ))
                        }
                        is FileUploadResult.Error -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = res.message))
                        }
                        null -> {
                            call.respond(ApiResponse<Unit>(code = 400, message = "未找到上传文件"))
                        }
                    }
                } catch (e: Exception) {
                    val (code, message) = translateApiError(
                        logger, "[FileUpload]: 用户 $userId 上传", "上传失败，请稍后重试", e
                    )
                    call.respond(ApiResponse<Unit>(code = code, message = message))
                }
            }

            /**
             * Delete a file.
             * DELETE /api/v1/files/{fileName}
             */
            delete("/{fileName}") {
                val userId = call.userId()
                val fileName = call.parameters["fileName"]

                if (fileName == null) {
                    call.respond(ApiResponse<Unit>(code = 400, message = "无效的文件名"))
                    return@delete
                }

                val result = fileUploadService.deleteFile(userId, fileName)
                when (result) {
                    is FileDeleteResult.Success -> {
                        call.respond(ApiResponse<Unit>(code = 0, message = "删除成功"))
                    }
                    is FileDeleteResult.Error -> {
                        call.respond(ApiResponse<Unit>(code = 400, message = result.message))
                    }
                }
            }
            }

            /**
             * Serve uploaded files.
             * GET /api/v1/files/{fileName}
             */
            get("/{fileName}") {
                val fileName = call.parameters["fileName"] ?: return@get

                // getFilePath 返回 null 意味着路径穿越或越出 uploads 目录，与「文件不存在」
                // 同样按 404 处理，不向调用方透露目录外是否有文件
                val filePath = fileUploadService.getFilePath(fileName)
                if (filePath == null) {
                    call.respond(HttpStatusCode.NotFound, "文件不存在")
                    return@get
                }

                // 扩展名白名单做纵深防御：本端点只用于回显上传的图片，
                // 即便路径校验将来被绕过，也不该把非图片内容读出去
                val contentType = when (fileName.substringAfterLast('.', "").lowercase()) {
                    "jpg", "jpeg" -> ContentType.Image.JPEG
                    "png" -> ContentType.Image.PNG
                    "gif" -> ContentType.Image.GIF
                    "webp" -> ContentType.Image.WEBP
                    else -> {
                        call.respond(HttpStatusCode.NotFound, "文件不存在")
                        return@get
                    }
                }

                val file = File(filePath)
                if (!file.exists()) {
                    call.respond(HttpStatusCode.NotFound, "文件不存在")
                    return@get
                }

                call.respondBytes(file.readBytes(), contentType)
            }
        }
    }
}

@Serializable
data class FileUploadResponseDto(val url: String, val fileName: String, val size: Long)
