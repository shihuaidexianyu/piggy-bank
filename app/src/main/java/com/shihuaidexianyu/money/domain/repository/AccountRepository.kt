package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeActiveAccounts(): Flow<List<Account>>
    fun observeArchivedAccounts(): Flow<List<Account>>
    suspend fun queryActiveAccounts(): List<Account>
    suspend fun queryArchivedAccounts(): List<Account>
    suspend fun getAccountById(id: Long): Account?
    suspend fun isActiveNameAvailable(name: String, excludeId: Long = -1): Boolean
    suspend fun createAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun archiveAccount(accountId: Long, archivedAt: Long)
    suspend fun updateLastUsedAt(accountId: Long, timestamp: Long)
    suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?)
    suspend fun nextDisplayOrder(): Int
}
