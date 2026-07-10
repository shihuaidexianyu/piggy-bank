package com.shihuaidexianyu.money.domain.repository

import com.shihuaidexianyu.money.domain.model.Account
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAllAccounts(): Flow<List<Account>>
    fun observeOpenAccounts(): Flow<List<Account>>
    fun observeVisibleOpenAccounts(): Flow<List<Account>>
    fun observeHiddenOpenAccounts(): Flow<List<Account>>
    fun observeClosedAccounts(): Flow<List<Account>>
    suspend fun queryAllAccounts(): List<Account>
    suspend fun queryOpenAccounts(): List<Account>
    suspend fun queryVisibleOpenAccounts(): List<Account>
    suspend fun queryHiddenOpenAccounts(): List<Account>
    suspend fun queryClosedAccounts(): List<Account>
    suspend fun getAccountById(id: Long): Account?
    suspend fun isOpenNameAvailable(name: String, excludeId: Long = -1): Boolean
    suspend fun createAccount(account: Account): Long
    suspend fun updateAccount(account: Account)
    suspend fun setHidden(accountId: Long, hidden: Boolean)
    suspend fun closeAccount(accountId: Long, closedAt: Long)
    suspend fun reopenAccount(accountId: Long)
    suspend fun updateLastUsedAt(accountId: Long, timestamp: Long)
    suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?)
    suspend fun nextDisplayOrder(): Int
}
