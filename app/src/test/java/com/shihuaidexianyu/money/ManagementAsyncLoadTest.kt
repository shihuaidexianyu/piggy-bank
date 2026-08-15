package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsViewModel
import com.shihuaidexianyu.money.ui.balance.BatchReconcileViewModel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManagementAsyncLoadTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `reorder load failure is error rather than empty and retry restores accounts`() = runTest(dispatcher) {
        val delegate = accountRepository()
        val accounts = ToggleOpenAccountsRepository(delegate)
        val transactions = InMemoryTransactionRepository()
        val viewModel = ReorderAccountsViewModel(
            accountRepository = accounts,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions),
            updateAccountDisplayOrderUseCase = UpdateAccountDisplayOrderUseCase(accounts, transactions),
        )
        advanceUntilIdle()
        assertEquals(R.string.account_order_load_failed, viewModel.uiState.value.loadErrorMessageRes)

        accounts.available = true
        viewModel.retryLoad()
        viewModel.uiState.first { !it.isLoading }
        assertNull(viewModel.uiState.value.loadErrorMessageRes)
        assertEquals(1, viewModel.uiState.value.accounts.size)
    }

    @Test
    fun `batch reconcile load failure is error rather than no stale accounts`() = runTest(dispatcher) {
        val delegate = accountRepository()
        val accounts = ToggleOpenAccountsRepository(delegate)
        val transactions = InMemoryTransactionRepository()
        val reminderSettings = InMemoryAccountReminderSettingsRepository()
        val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        val resolve = ResolveBalanceUpdateContextUseCase(accounts, transactions)
        val viewModel = BatchReconcileViewModel(
            accountReminderSettingsRepository = reminderSettings,
            accountRepository = accounts,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            transactionRepository = transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions),
            updateBalanceUseCase = UpdateBalanceUseCase(accounts, transactions, resolve, refresh, testClockProvider),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
            clockProvider = testClockProvider,
        )
        advanceUntilIdle()
        assertEquals(R.string.batch_reconcile_load_failed, viewModel.uiState.value.loadErrorMessageRes)

        accounts.available = true
        viewModel.retryLoad()
        viewModel.uiState.first { !it.isLoading }
        assertNull(viewModel.uiState.value.loadErrorMessageRes)
        assertFalse(viewModel.uiState.value.isLoading)

        accounts.available = false
        viewModel.retryLoad(); advanceUntilIdle()
    }

    private suspend fun accountRepository() = InMemoryAccountRepository().also {
        it.createAccount(Account(name = "现金", initialBalance = 100L, createdAt = 1L))
    }
}

private class ToggleOpenAccountsRepository(
    private val delegate: AccountRepository,
) : AccountRepository by delegate {
    var available: Boolean = false

    override suspend fun queryOpenAccounts(): List<Account> {
        if (!available) error("database unavailable")
        return delegate.queryOpenAccounts()
    }

    override fun observeOpenAccounts(): Flow<List<Account>> =
        if (available) flowOf(kotlinx.coroutines.runBlocking { delegate.queryOpenAccounts() })
        else flow { error("database unavailable") }
}
