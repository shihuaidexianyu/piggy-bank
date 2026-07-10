package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class InMemoryAccountRepository : AccountRepository {
    private val accounts = MutableStateFlow<List<Account>>(emptyList())
    private var nextId = 1L

    override fun observeAllAccounts(): Flow<List<Account>> {
        return accounts.map(::allAccounts)
    }

    override fun observeOpenAccounts(): Flow<List<Account>> {
        return accounts.map(::openAccounts)
    }

    override fun observeVisibleOpenAccounts(): Flow<List<Account>> {
        return accounts.map { visibleOpenAccounts(it) }
    }

    override fun observeHiddenOpenAccounts(): Flow<List<Account>> {
        return accounts.map { hiddenOpenAccounts(it) }
    }

    override fun observeClosedAccounts(): Flow<List<Account>> {
        return accounts.map(::closedAccounts)
    }

    override suspend fun queryAllAccounts(): List<Account> = allAccounts(accounts.value)

    override suspend fun queryOpenAccounts(): List<Account> = openAccounts(accounts.value)

    override suspend fun queryVisibleOpenAccounts(): List<Account> = visibleOpenAccounts(accounts.value)

    override suspend fun queryHiddenOpenAccounts(): List<Account> = hiddenOpenAccounts(accounts.value)

    override suspend fun queryClosedAccounts(): List<Account> {
        return closedAccounts(accounts.value)
    }

    override suspend fun getAccountById(id: Long): Account? {
        return accounts.value.firstOrNull { it.id == id }
    }

    override suspend fun isOpenNameAvailable(name: String, excludeId: Long): Boolean {
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

    override suspend fun setHidden(accountId: Long, hidden: Boolean) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId && !existing.isClosed) existing.copy(isHidden = hidden) else existing
        }
    }

    override suspend fun closeAccount(accountId: Long, closedAt: Long) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId && !existing.isClosed) {
                existing.copy(closedAt = closedAt)
            } else {
                existing
            }
        }
    }

    override suspend fun reopenAccount(accountId: Long) {
        accounts.value = accounts.value.map { existing ->
            if (existing.id == accountId) existing.copy(closedAt = null) else existing
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

    private fun allAccounts(list: List<Account>): List<Account> {
        return list.sortedWith(compareBy<Account> { it.displayOrder }.thenBy { it.createdAt })
    }

    private fun openAccounts(list: List<Account>): List<Account> {
        return list.filterNot(Account::isClosed)
            .sortedWith(compareBy<Account> { it.displayOrder }.thenBy { it.createdAt })
    }

    private fun visibleOpenAccounts(list: List<Account>): List<Account> = openAccounts(list).filterNot { it.isHidden }

    private fun hiddenOpenAccounts(list: List<Account>): List<Account> = openAccounts(list).filter { it.isHidden }

    private fun closedAccounts(list: List<Account>): List<Account> {
        return list.filter(Account::isClosed)
            .sortedWith(compareByDescending<Account> { it.closedAt }.thenByDescending { it.createdAt })
    }
}
