package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import com.shihuaidexianyu.money.domain.usecase.ReopenAccountUseCase
import com.shihuaidexianyu.money.ui.accounts.AccountDetailViewModel
import com.shihuaidexianyu.money.ui.accounts.canMutateLedger
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailAsyncStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `detail snapshots do not clear reopen progress while transaction is pending`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(Account(name = "旧账户", initialBalance = 100L, createdAt = 1L))
        accounts.closeAccount(accountId, 2L)
        val gate = CompletableDeferred<Unit>()
        val delayedRunner = object : DatabaseTransactionRunner {
            override suspend fun <T> runInTransaction(block: suspend () -> T): T {
                gate.await()
                return transactions.runInTransaction(block)
            }
        }
        val useCase = ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            transactionRepository = transactions,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, transactions),
            clockProvider = testClockProvider(3L),
            zoneIdProvider = { ZoneId.of("UTC") },
        )
        val viewModel = AccountDetailViewModel(
            accountId,
            useCase,
            ReopenAccountUseCase(accounts, delayedRunner),
        )
        viewModel.uiState.first { !it.isLoading && it.isClosed }

        viewModel.reopenAccount()
        runCurrent()
        assertTrue(viewModel.uiState.value.isReopening)
        transactions.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = 1L,
                occurredAt = 3L,
                createdAt = 3L,
                updatedAt = 3L,
                operationId = "reopen-snapshot",
            ),
        )
        viewModel.uiState.first { it.currentBalance == 101L }

        assertTrue(viewModel.uiState.value.isReopening)
        gate.complete(Unit)
        viewModel.uiState.first { !it.isClosed }
    }

    @Test
    fun `closed detail is read-only until explicit reopen`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(Account(name = "旧账户", initialBalance = 100L, createdAt = 1L))
        accounts.closeAccount(accountId, 2L)
        val useCase = ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            transactionRepository = transactions,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, transactions),
            clockProvider = testClockProvider(3L),
            zoneIdProvider = { ZoneId.of("UTC") },
        )
        val viewModel = AccountDetailViewModel(
            accountId,
            useCase,
            ReopenAccountUseCase(accounts, transactions),
        )
        viewModel.uiState.first { !it.isLoading && it.isClosed }

        assertTrue(viewModel.uiState.value.isClosed)
        assertFalse(viewModel.uiState.value.canMutateLedger())

        viewModel.reopenAccount()
        viewModel.uiState.first { !it.isLoading && !it.isClosed }

        assertFalse(viewModel.uiState.value.isClosed)
        assertTrue(viewModel.uiState.value.canMutateLedger())
    }

    @Test
    fun `account detail error is not missing and retry restores real balance`() = runTest(dispatcher) {
        val delegate = InMemoryAccountRepository()
        val accountId = delegate.createAccount(Account(name = "现金", initialBalance = 12_345L, createdAt = 1L))
        val accounts = ToggleObserveAccountRepository(delegate)
        val transactions = InMemoryTransactionRepository()
        val useCase = ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            transactionRepository = transactions,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, transactions),
            clockProvider = testClockProvider(1_700_000_000_000L),
            zoneIdProvider = { ZoneId.of("UTC") },
        )
        val viewModel = AccountDetailViewModel(
            accountId,
            useCase,
            ReopenAccountUseCase(accounts, transactions),
        )
        viewModel.uiState.first { !it.isLoading }

        assertEquals("账户详情加载失败，请重试", viewModel.uiState.value.loadErrorMessage)
        assertFalse(viewModel.uiState.value.isMissing)

        accounts.available = true
        viewModel.retry()
        viewModel.uiState.first { !it.isLoading }

        assertNull(viewModel.uiState.value.loadErrorMessage)
        assertEquals(12_345L, viewModel.uiState.value.currentBalance)
        assertFalse(viewModel.uiState.value.isMissing)
    }
}

private class ToggleObserveAccountRepository(
    private val delegate: AccountRepository,
) : AccountRepository by delegate {
    var available: Boolean = false

    override fun observeAllAccounts(): Flow<List<Account>> {
        if (!available) return flow { error("database unavailable") }
        return flow { emit(delegate.queryAllAccounts()) }
    }
}
