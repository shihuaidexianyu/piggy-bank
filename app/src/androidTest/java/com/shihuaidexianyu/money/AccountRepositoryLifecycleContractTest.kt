package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountRepositoryLifecycleContractTest {
    private lateinit var database: MoneyDatabase
    private lateinit var room: AccountRepository
    private lateinit var memory: AccountRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        room = AccountRepositoryImpl(database.accountDao())
        memory = InMemoryAccountRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun roomAndMemory_partitionsHideCloseAndReopenWithEquivalentResults() = runBlocking {
        listOf(room, memory).forEach { repository ->
            repository.createAccount(account("显示", order = 2, createdAt = 20))
            repository.createAccount(account("隐藏", order = 1, createdAt = 10, hidden = true))
            repository.createAccount(account("关闭", order = 0, createdAt = 5))
            repository.closeAccount(3, 99)
            repository.setHidden(1, true)
            repository.setHidden(1, true)
        }

        assertEquivalent()
        assertEquals(listOf(2L, 1L), room.queryHiddenOpenAccounts().map { it.id })
        assertEquals(listOf(3L), room.queryClosedAccounts().map { it.id })

        listOf(room, memory).forEach { repository ->
            repository.setHidden(1, false)
            repository.setHidden(1, false)
            repository.reopenAccount(3)
        }
        assertEquivalent()
        assertEquals(listOf(3L, 2L, 1L), room.queryOpenAccounts().map { it.id })
    }

    private suspend fun assertEquivalent() {
        assertEquals(memory.queryAllAccounts(), room.queryAllAccounts())
        assertEquals(memory.queryOpenAccounts(), room.queryOpenAccounts())
        assertEquals(memory.queryVisibleOpenAccounts(), room.queryVisibleOpenAccounts())
        assertEquals(memory.queryHiddenOpenAccounts(), room.queryHiddenOpenAccounts())
        assertEquals(memory.queryClosedAccounts(), room.queryClosedAccounts())
    }

    private fun account(name: String, order: Int, createdAt: Long, hidden: Boolean = false) = Account(
        name = name,
        initialBalance = order * 100L,
        createdAt = createdAt,
        isHidden = hidden,
        lastUsedAt = createdAt,
        displayOrder = order,
    )
}
