package com.dentalclinic.common

import com.dentalclinic.admin.entity.UploadedFiles
import io.ktor.http.content.*
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * File upload service for local storage.
 *
 * 位于 common 包而不是 user.service：管理端（轮播图、门店封面、医生头像）与 C 端（用户头像）
 * 都要上传，而 user 包已大量依赖 admin 包，若把它留在 user.service 会让 admin 反向依赖 user
 * 形成包循环。与 BaseService、ReferralRewardService 迁到 common 的理由一致。
 */
class FileUploadService : BaseService() {

    private val logger = LoggerFactory.getLogger("FileUploadService")

    private val uploadDir = System.getProperty("user.dir") + "/uploads"
    private val baseUrl = "/api/v1/files"
    private val maxSizeBytes = 5 * 1024 * 1024L // 5MB
    private val allowedExtensions = setOf("jpg", "jpeg", "png", "gif", "webp")

    init {
        File(uploadDir).mkdirs()
    }

    /**
     * Save uploaded file and return the access URL.
     *
     * @param ownerType user=C 端用户上传（默认），admin=管理端上传。
     *                  管理端上传时 [userId] 约定传 0（uploaded_file.user_id 为 NOT NULL），
     *                  归属靠 ownerType 区分，避免与「匿名 C 端用户」混淆。
     */
    suspend fun saveFile(
        userId: Long,
        part: PartData.FileItem,
        ownerType: String = OWNER_USER
    ): FileUploadResult {
        return withContext(Dispatchers.IO) {
            // Validate file size
            // 改用非阻塞的 provider()：已废弃的 streamProvider() 把上传流包成阻塞式
            // InputStream，读它时线程会一直卡在 socket 上；toByteArray() 是
            // ByteReadChannel 的 suspend 扩展，等数据时能让出线程。
            // 注意 withContext(Dispatchers.IO) 不能随之去掉：下面的 Files.write
            // 仍是阻塞文件 IO。
            val bytes = part.provider().toByteArray()
            val size = bytes.size.toLong()
            
            if (size > maxSizeBytes) {
                return@withContext FileUploadResult.Error("文件大小不能超过5MB")
            }

            // Validate file extension
            val originalFileName = part.originalFileName ?: ""
            val extension = originalFileName.substringAfterLast('.', "").lowercase()
            if (extension !in allowedExtensions) {
                return@withContext FileUploadResult.Error("不支持的文件类型，仅支持: ${allowedExtensions.joinToString(", ")}")
            }

            // Generate unique filename
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            val randomSuffix = UUID.randomUUID().toString().take(8)
            val newFileName = "${timestamp}_${randomSuffix}.$extension"
            val filePath = Paths.get(uploadDir, newFileName)

            // Save file
            try {
                Files.write(filePath, bytes)
                val url = "$baseUrl/$newFileName"

                // Record in database
                dbQuery {
                    UploadedFiles.insert {
                        it[UploadedFiles.userId] = userId
                        it[UploadedFiles.ownerType] = ownerType
                        it[UploadedFiles.fileName] = newFileName
                        it[UploadedFiles.originalName] = originalFileName
                        it[UploadedFiles.fileSize] = size
                        it[UploadedFiles.contentType] = part.contentType?.toString()
                        it[UploadedFiles.url] = url
                    }
                }

                FileUploadResult.Success(url, newFileName, size)
            } catch (e: Exception) {
                // 不回传 e.message：磁盘/权限类异常的文本会把服务器绝对路径等细节暴露给 C 端用户
                logger.error("[FileUpload]: $ownerType/$userId 保存文件失败", e)
                FileUploadResult.Error("文件保存失败，请稍后重试")
            }
        }
    }

    /**
     * Delete a file by fileName, verifying ownership.
     *
     * 归属校验同时比对 userId 与 ownerType：管理端文件的 userId 统一是 0，
     * 只比 userId 会让任意两个管理员互相删对方上传的文件，也会让
     * C 端用户（若其 id 恰为 0）碰到管理端文件。
     */
    suspend fun deleteFile(
        userId: Long,
        fileName: String,
        ownerType: String = OWNER_USER
    ): FileDeleteResult {
        // Pre-check ownership
        val fileRecord = dbQuery {
            UploadedFiles.selectAll()
                .where {
                    (UploadedFiles.fileName eq fileName) and
                    (UploadedFiles.userId eq userId) and
                    (UploadedFiles.ownerType eq ownerType)
                }
                .toList()
                .singleOrNull()
        }

        if (fileRecord == null) {
            return FileDeleteResult.Error("文件不存在或无权限删除")
        }

        return withContext(Dispatchers.IO) {
            // 复用 getFilePath 的越界校验做纵深防御：DB 里的 fileName 虽由服务端生成，
            // 但一旦出现异常数据（如后台导入），也不该让删除动作落到 uploads 之外
            val filePath = getFilePath(fileName)
                ?: return@withContext FileDeleteResult.Error("文件不存在或无权限删除")
            val file = File(filePath)
            if (file.exists()) {
                file.delete()
            }

            // Delete from database
            dbQuery {
                UploadedFiles.deleteWhere {
                    (UploadedFiles.fileName eq fileName) and
                    (UploadedFiles.userId eq userId) and
                    (UploadedFiles.ownerType eq ownerType)
                }
            }

            FileDeleteResult.Success
        }
    }

    /**
     * Batch delete files, verifying ownership for each.
     */
    suspend fun batchDeleteFiles(
        userId: Long,
        fileNames: List<String>,
        ownerType: String = OWNER_USER
    ): BatchDeleteResult {
        val results = mutableListOf<FileDeleteItemResult>()
        
        for (fileName in fileNames) {
            val result = deleteFile(userId, fileName, ownerType)
            results.add(FileDeleteItemResult(
                fileName = fileName,
                success = result is FileDeleteResult.Success,
                message = when (result) {
                    is FileDeleteResult.Success -> "删除成功"
                    is FileDeleteResult.Error -> result.message
                }
            ))
        }
        
        val successCount = results.count { it.success }
        val failCount = results.size - successCount
        
        return BatchDeleteResult(
            total = fileNames.size,
            successCount = successCount,
            failCount = failCount,
            details = results
        )
    }

    /**
     * 解析已存文件的绝对路径，越界时返回 null。
     *
     * fileName 直接取自 URL 路径参数，Ktor 会对其做百分号解码：
     * `..%2Fbuild.gradle.kts` 解码后即含 `../`（未编码的 `../` 反而会被路由挡下）。
     * 若不经校验就 Paths.get(uploadDir, fileName)，未认证的
     * GET /api/v1/files/{fileName} 就能读到 uploads 之外的任意文件。
     * 这里规范化后确认仍落在 uploadDir 内，越界一律返回 null。
     */
    fun getFilePath(fileName: String): String? {
        // 空字节先拦掉：文件名里出现 \u0000 只可能是攻击（经典的 C 字符串截断尝试），
        // 且 Java 的 Path/File 会直接抛 IllegalArgumentException，不拦住就会冒泡成 500
        if (fileName.indexOf('\u0000') >= 0) return null

        return try {
            val base = File(uploadDir).canonicalFile
            val target = File(base, fileName).canonicalFile
            // 比较时带上分隔符，否则 /uploads-evil 这类同前缀目录会被误判为合法
            if (!target.path.startsWith(base.path + File.separator)) null else target.path
        } catch (e: java.io.IOException) {
            // canonicalFile 要访问文件系统，异常同样按「拿不到这个文件」处理，与越界一致回 null
            logger.warn("[FileUpload]: 解析文件路径失败", e)
            null
        }
    }

    companion object {
        /** uploaded_file.owner_type 取值：C 端用户上传 */
        const val OWNER_USER = "user"

        /** uploaded_file.owner_type 取值：管理端上传 */
        const val OWNER_ADMIN = "admin"

        /** 管理端上传没有 C 端用户身份，user_id 约定写 0，归属靠 ownerType 区分 */
        const val ADMIN_OWNER_ID = 0L
    }
}

sealed class FileUploadResult {
    data class Success(val url: String, val fileName: String, val size: Long) : FileUploadResult()
    data class Error(val message: String) : FileUploadResult()
}

sealed class FileDeleteResult {
    object Success : FileDeleteResult()
    data class Error(val message: String) : FileDeleteResult()
}

data class FileDeleteItemResult(
    val fileName: String,
    val success: Boolean,
    val message: String
)

data class BatchDeleteResult(
    val total: Int,
    val successCount: Int,
    val failCount: Int,
    val details: List<FileDeleteItemResult>
)
