package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.SavingsGoal
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SavingsGoalRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.ClearSavingsGoalUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpsertSavingsGoalUseCase
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsViewModel
import com.shihuaidexianyu.money.ui.balance.BatchReconcileViewModel
import com.shihuaidexianyu.money.ui.settings.SavingsGoalViewModel
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
        assertEquals("账户顺序加载失败，请重试", viewModel.uiState.value.loadErrorMessage)

        accounts.available = true
        viewModel.retryLoad()
        viewModel.uiState.first { !it.isLoading }
        assertNull(viewModel.uiState.value.loadErrorMessage)
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
        assertEquals("批量核对加载失败，请重试", viewModel.uiState.value.loadErrorMessage)

        accounts.available = true
        viewModel.retryLoad()
        viewModel.uiState.first { !it.isLoading }
        assertNull(viewModel.uiState.value.loadErrorMessage)
        assertFalse(viewModel.uiState.value.isLoading)

        accounts.available = false
        viewModel.retryLoad(); advanceUntilIdle()
    }

    @Test
    fun `savings goal load failure is error rather than no goal and retry succeeds`() = runTest(dispatcher) {
        val delegate = InMemorySavingsGoalRepository()
        val repository = ToggleSavingsGoalRepository(delegate)
        val viewModel = SavingsGoalViewModel(
            repository,
            UpsertSavingsGoalUseCase(repository, testClockProvider),
            ClearSavingsGoalUseCase(repository),
        )
        advanceUntilIdle()
        assertEquals("净资产目标加载失败，请重试", viewModel.uiState.value.loadErrorMessage)
        assertFalse(viewModel.uiState.value.hasGoal)

        repository.available = true
        viewModel.retryLoad(); advanceUntilIdle()
        assertNull(viewModel.uiState.value.loadErrorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
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

private class ToggleSavingsGoalRepository(
    private val delegate: SavingsGoalRepository,
) : SavingsGoalRepository by delegate {
    var available: Boolean = false

    override fun observe(): Flow<SavingsGoal?> =
        if (available) flowOf(kotlinx.coroutines.runBlocking { delegate.query() })
        else flow { error("database unavailable") }
}
