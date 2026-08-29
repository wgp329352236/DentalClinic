package com.dentalclinic.user.route

import com.dentalclinic.admin.dto.response.ApiResponse
import com.dentalclinic.user.auth.UserJwtConfig
import com.dentalclinic.user.service.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.io.File

/**
 * File upload routes.
 */
fun Application.fileUploadRoutes() {
    val fileUploadService = FileUploadService()

    routing {
        route("/api/v1/files") {
            /**
             * Upload a file.
             * POST /api/v1/files/upload
             */
            post("/upload") {
                val userId = authenticate(call) ?: return@post

                try {
                    val multipart = call.receiveMultipart()
                    var result: FileUploadResult? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            result = fileUploadService.saveFile(userId, part)
                        }
                        part.dispose()
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
                    call.respond(ApiResponse<Unit>(code = 500, message = "上传失败: ${e.message}"))
                }
            }

            /**
             * Delete a file.
             * DELETE /api/v1/files/{fileName}
             */
            delete("/{fileName}") {
                val userId = authenticate(call) ?: return@delete
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

            /**
             * Serve uploaded files.
             * GET /api/v1/files/{fileName}
             */
            get("/{fileName}") {
                val fileName = call.parameters["fileName"] ?: return@get
                val filePath = fileUploadService.getFilePath(fileName)
                val file = File(filePath)

                if (!file.exists()) {
                    call.respond(HttpStatusCode.NotFound, "文件不存在")
                    return@get
                }

                val contentType = when (fileName.substringAfterLast('.', "").lowercase()) {
                    "jpg", "jpeg" -> ContentType.Image.JPEG
                    "png" -> ContentType.Image.PNG
                    "gif" -> ContentType.Image.GIF
                    else -> ContentType.Application.OctetStream
                }

                call.respondBytes(file.readBytes(), contentType)
            }
        }
    }
}

private suspend fun authenticate(call: io.ktor.server.application.ApplicationCall): Long? {
    val authHeader = call.request.headers[HttpHeaders.Authorization]
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        call.respond(ApiResponse<Unit>(code = 401, message = "缺少认证令牌"))
        return null
    }

    val token = authHeader.removePrefix("Bearer ")
    val decoded = UserJwtConfig.verifyAccessToken(token)
    if (decoded == null) {
        call.respond(ApiResponse<Unit>(code = 401, message = "无效的访问令牌"))
        return null
    }

    return decoded.subject.toLong()
}

@Serializable
data class FileUploadResponseDto(val url: String, val fileName: String, val size: Long)
