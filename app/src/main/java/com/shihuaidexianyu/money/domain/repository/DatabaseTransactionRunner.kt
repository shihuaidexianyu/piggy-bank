package com.shihuaidexianyu.money.domain.repository

/** Android-free port for work that must share one database transaction. */
interface DatabaseTransactionRunner {
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
