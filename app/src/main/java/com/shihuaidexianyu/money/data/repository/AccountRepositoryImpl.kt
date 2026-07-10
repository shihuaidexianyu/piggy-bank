package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.AccountDao
import com.shihuaidexianyu.money.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AccountRepositoryImpl(
    private val accountDao: AccountDao,
) : AccountRepository {
    override fun observeAllAccounts(): Flow<List<Account>> =
        accountDao.observeAllAccounts().map { accounts -> accounts.map { it.toDomain() } }

    override fun observeOpenAccounts(): Flow<List<Account>> =
        accountDao.observeOpenAccounts().map { accounts -> accounts.map { it.toDomain() } }

    override fun observeVisibleOpenAccounts(): Flow<List<Account>> =
        accountDao.observeVisibleOpenAccounts().map { accounts -> accounts.map { it.toDomain() } }

    override fun observeHiddenOpenAccounts(): Flow<List<Account>> =
        accountDao.observeHiddenOpenAccounts().map { accounts -> accounts.map { it.toDomain() } }

    override fun observeClosedAccounts(): Flow<List<Account>> =
        accountDao.observeClosedAccounts().map { accounts -> accounts.map { it.toDomain() } }

    override suspend fun queryAllAccounts(): List<Account> = accountDao.queryAllAccounts().map { it.toDomain() }

    override suspend fun queryOpenAccounts(): List<Account> = accountDao.queryOpenAccounts().map { it.toDomain() }

    override suspend fun queryVisibleOpenAccounts(): List<Account> =
        accountDao.queryVisibleOpenAccounts().map { it.toDomain() }

    override suspend fun queryHiddenOpenAccounts(): List<Account> =
        accountDao.queryHiddenOpenAccounts().map { it.toDomain() }

    override suspend fun queryClosedAccounts(): List<Account> = accountDao.queryClosedAccounts().map { it.toDomain() }

    override suspend fun getAccountById(id: Long): Account? = accountDao.queryById(id)?.toDomain()

    override suspend fun isOpenNameAvailable(name: String, excludeId: Long): Boolean {
        return accountDao.countOpenAccountsByName(name.trim(), excludeId) == 0
    }

    override suspend fun createAccount(account: Account): Long = accountDao.insert(account.toEntity())

    override suspend fun updateAccount(account: Account) = accountDao.update(account.toEntity())

    override suspend fun setHidden(accountId: Long, hidden: Boolean) {
        accountDao.setHidden(accountId, hidden)
    }

    override suspend fun closeAccount(accountId: Long, closedAt: Long) {
        accountDao.closeAccount(accountId, closedAt)
    }

    override suspend fun reopenAccount(accountId: Long) {
        accountDao.reopenAccount(accountId)
    }

    override suspend fun updateLastUsedAt(accountId: Long, timestamp: Long) {
        accountDao.updateLastUsedAt(accountId, timestamp)
    }

    override suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?) {
        accountDao.updateLastBalanceUpdateAt(accountId, timestamp)
    }

    override suspend fun nextDisplayOrder(): Int = accountDao.nextDisplayOrder()
}
