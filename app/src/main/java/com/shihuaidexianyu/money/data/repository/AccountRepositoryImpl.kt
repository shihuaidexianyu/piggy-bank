package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.AccountDao
import com.shihuaidexianyu.money.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepositoryImpl(
    private val accountDao: AccountDao,
) : AccountRepository {
    override fun observeActiveAccounts(): Flow<List<Account>> =
        accountDao.observeActiveAccounts().map { accounts -> accounts.map { it.toDomain() } }

    override fun observeArchivedAccounts(): Flow<List<Account>> =
        accountDao.observeArchivedAccounts().map { accounts -> accounts.map { it.toDomain() } }

    override suspend fun queryActiveAccounts(): List<Account> =
        accountDao.queryActiveAccounts().map { it.toDomain() }

    override suspend fun queryArchivedAccounts(): List<Account> =
        accountDao.queryArchivedAccounts().map { it.toDomain() }

    override suspend fun getAccountById(id: Long): Account? = accountDao.queryById(id)?.toDomain()

    override suspend fun isActiveNameAvailable(name: String, excludeId: Long): Boolean {
        return accountDao.countActiveAccountsByName(name.trim(), excludeId) == 0
    }

    override suspend fun createAccount(account: Account): Long = accountDao.insert(account.toEntity())

    override suspend fun updateAccount(account: Account) = accountDao.update(account.toEntity())

    override suspend fun archiveAccount(accountId: Long, archivedAt: Long) {
        accountDao.archiveAccount(accountId, archivedAt)
    }

    override suspend fun updateLastUsedAt(accountId: Long, timestamp: Long) {
        accountDao.updateLastUsedAt(accountId, timestamp)
    }

    override suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?) {
        accountDao.updateLastBalanceUpdateAt(accountId, timestamp)
    }

    override suspend fun nextDisplayOrder(): Int = accountDao.nextDisplayOrder()
}
