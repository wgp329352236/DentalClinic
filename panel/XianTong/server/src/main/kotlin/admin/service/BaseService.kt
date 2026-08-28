package com.dentalclinic.admin.service

import com.dentalclinic.config.DBConfig
import org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction

open class BaseService {

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        suspendTransaction(DBConfig.database) { block() }
}