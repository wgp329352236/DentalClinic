package com.dentalclinic.admin.auth

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * bcrypt 密码编码器，cost factor = 10
 */
object PasswordEncoder {

    private const val COST = 10

    /**
     * 对明文密码进行 bcrypt 哈希
     */
    fun hash(password: String): String {
        return BCrypt.withDefaults().hashToString(COST, password.toCharArray())
    }

    /**
     * 验证明文密码是否匹配哈希
     */
    fun verify(password: String, hash: String): Boolean {
        return BCrypt.verifyer().verify(password.toCharArray(), hash).verified
    }
}
