package com.dentalclinic.admin.util

/**
 * 认证异常 → HTTP 401
 */
class AuthenticationException(message: String) : RuntimeException(message)

/**
 * 权限不足 → HTTP 403
 */
class ForbiddenException(message: String = "权限不足") : RuntimeException(message)

/**
 * 参数校验异常 → HTTP 400
 */
class ValidationException(
    message: String,
    val fieldErrors: Map<String, String> = emptyMap()
) : RuntimeException(message)

/**
 * 资源不存在 → HTTP 404
 */
class NotFoundException(message: String = "资源不存在") : RuntimeException(message)

/**
 * 资源冲突 → HTTP 409
 */
class ConflictException(message: String = "资源冲突") : RuntimeException(message)
