package com.dentalclinic.user.service

import com.dentalclinic.admin.entity.UploadedFiles
import com.dentalclinic.common.BaseService
import io.ktor.http.content.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.r2dbc.*
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * File upload service for local storage.
 */
class FileUploadService : BaseService() {

    private val uploadDir = System.getProperty("user.dir") + "/uploads"
    private val baseUrl = "/api/v1/files"
    private val maxSizeBytes = 5 * 1024 * 1024L // 5MB
    private val allowedExtensions = setOf("jpg", "jpeg", "png", "gif", "webp")

    init {
        File(uploadDir).mkdirs()
    }

    /**
     * Save uploaded file and return the access URL.
     */
    suspend fun saveFile(userId: Long, part: PartData.FileItem): FileUploadResult {
        return withContext(Dispatchers.IO) {
            // Validate file size
            val inputStream = part.streamProvider()
            val bytes = inputStream.readBytes()
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
                        it[UploadedFiles.fileName] = newFileName
                        it[UploadedFiles.originalName] = originalFileName
                        it[UploadedFiles.fileSize] = size
                        it[UploadedFiles.contentType] = part.contentType?.toString()
                        it[UploadedFiles.url] = url
                    }
                }

                FileUploadResult.Success(url, newFileName, size)
            } catch (e: Exception) {
                FileUploadResult.Error("文件保存失败: ${e.message}")
            }
        }
    }

    /**
     * Delete a file by fileName, verifying ownership.
     */
    suspend fun deleteFile(userId: Long, fileName: String): FileDeleteResult {
        // Pre-check ownership
        val fileRecord = dbQuery {
            UploadedFiles.selectAll()
                .where {
                    (UploadedFiles.fileName eq fileName) and
                    (UploadedFiles.userId eq userId)
                }
                .toList()
                .singleOrNull()
        }

        if (fileRecord == null) {
            return FileDeleteResult.Error("文件不存在或无权限删除")
        }

        return withContext(Dispatchers.IO) {
            // Delete from disk
            val filePath = Paths.get(uploadDir, fileName)
            val file = filePath.toFile()
            if (file.exists()) {
                file.delete()
            }

            // Delete from database
            dbQuery {
                UploadedFiles.deleteWhere {
                    (UploadedFiles.fileName eq fileName) and
                    (UploadedFiles.userId eq userId)
                }
            }

            FileDeleteResult.Success
        }
    }

    /**
     * Batch delete files, verifying ownership for each.
     */
    suspend fun batchDeleteFiles(userId: Long, fileNames: List<String>): BatchDeleteResult {
        val results = mutableListOf<FileDeleteItemResult>()
        
        for (fileName in fileNames) {
            val result = deleteFile(userId, fileName)
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
     * Get absolute path for a stored file.
     */
    fun getFilePath(fileName: String): String {
        return Paths.get(uploadDir, fileName).toString()
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
