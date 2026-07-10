package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryAccountRepository : AccountRepository {
    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    private var nextId = 1L

    override fun observeActiveAccounts(): Flow<List<Account>> {
        return accounts.map(::activeAccounts)
    }

    override fun observeArchivedAccounts(): Flow<List<Account>> {
        return accounts.map(::archivedAccounts)
    }

    override suspend fun queryActiveAccounts(): List<Account> {
        return activeAccounts(accounts.value)
    }

    override suspend fun queryArchivedAccounts(): List<Account> {
        return archivedAccounts(accounts.value)
    }

    override suspend fun getAccountById(id: Long): Account? {
        return accounts.value.firstOrNull { it.id == id }
    }

    override suspend fun isActiveNameAvailable(name: String, excludeId: Long): Boolean {
        val normalizedName = name.trim()
        return accounts.value.none {
            !it.isClosed && it.name == normalizedName && (excludeId < 0 || it.id != excludeId)
        }
    }

    override suspend fun createAccount(account: Account): Long {
        val id = nextId++
        accounts.value = accounts.value + account.copy(id = id)
        return id
    }

    override suspend fun updateAccount(account: Account) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == account.id) account else existing
        }
    }

    override suspend fun archiveAccount(accountId: Long, archivedAt: Long) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId) {
                existing.copy(closedAt = archivedAt)
            } else {
                existing
            }
        }
    }

    override suspend fun updateLastUsedAt(accountId: Long, timestamp: Long) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId) existing.copy(lastUsedAt = timestamp) else existing
        }
    }

    override suspend fun updateLastBalanceUpdateAt(accountId: Long, timestamp: Long?) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId) existing.copy(lastBalanceUpdateAt = timestamp) else existing
        }
    }

    override suspend fun nextDisplayOrder(): Int {
        return (accounts.value.filterNot(Account::isClosed).maxOfOrNull { it.displayOrder } ?: -1) + 1
    }

    private fun activeAccounts(list: List<Account>): List<Account> {
        return list.filterNot(Account::isClosed)
            .sortedWith(compareBy<Account> { it.displayOrder }.thenBy { it.createdAt })
    }

    private fun archivedAccounts(list: List<Account>): List<Account> {
        return list.filter(Account::isClosed)
            .sortedWith(compareByDescending<Account> { it.closedAt }.thenByDescending { it.createdAt })
    }
}
