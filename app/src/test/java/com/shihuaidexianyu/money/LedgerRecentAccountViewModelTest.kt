package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.repository.DevicePreferencesRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.ui.record.RecordCashFlowViewModel
import com.shihuaidexianyu.money.ui.record.RecordCashFlowEffect
import com.shihuaidexianyu.money.ui.record.RecordTransferEffect
import com.shihuaidexianyu.money.ui.record.RecordTransferViewModel
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerRecentAccountViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun `cash defaults to recent hidden open account and excludes closed account`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val visibleId = accounts.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1))
        val hiddenId = accounts.createAccount(
            Account(name = "隐藏卡", initialBalance = 0, createdAt = 1, isHidden = true),
        )
        val closedId = accounts.createAccount(Account(name = "关闭卡", initialBalance = 0, createdAt = 1))
        accounts.closeAccount(closedId, closedAt = 2)
        val preferences = InMemoryDevicePreferencesRepository(
            DevicePreferences(recentAccountIds = listOf(closedId, hiddenId, visibleId)),
        )

        val viewModel = cashViewModel(accounts, preferences)
        advanceUntilIdle()

        assertEquals(hiddenId, viewModel.uiState.value.selectedAccountId)
        assertFalse(viewModel.uiState.value.accounts.any { it.id == closedId })
    }

    @Test
    fun `transfer defaults recent source and deterministic distinct destination`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val visibleId = accounts.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1))
        val hiddenId = accounts.createAccount(
            Account(name = "隐藏卡", initialBalance = 0, createdAt = 1, isHidden = true),
        )
        val preferences = InMemoryDevicePreferencesRepository(
            DevicePreferences(recentAccountIds = listOf(hiddenId)),
        )

        val viewModel = transferViewModel(accounts, preferences)
        advanceUntilIdle()

        assertEquals(hiddenId, viewModel.uiState.value.fromAccountId)
        assertEquals(visibleId, viewModel.uiState.value.toAccountId)
    }

    @Test
    fun `successful cash save moves selected account to recent front`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val firstId = accounts.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1))
        val secondId = accounts.createAccount(Account(name = "银行卡", initialBalance = 0, createdAt = 1))
        val preferences = InMemoryDevicePreferencesRepository(
            DevicePreferences(recentAccountIds = listOf(firstId)),
        )
        val viewModel = cashViewModel(accounts, preferences)
        advanceUntilIdle()

        viewModel.updateAccount(secondId)
        viewModel.updateAmount("1.00")
        viewModel.updateOccurredAt(60_000)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(listOf(secondId, firstId), preferences.query().recentAccountIds)
    }

    @Test
    fun `recent preference failure after cash commit still emits saved`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1))
        val transactions = InMemoryTransactionRepository()
        val delegate = InMemoryDevicePreferencesRepository()
        val preferences = object : DevicePreferencesRepository by delegate {
            override suspend fun updateRecentAccountIds(accountIds: List<Long>) {
                error("datastore unavailable")
            }
        }
        val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        val viewModel = RecordCashFlowViewModel(
            direction = CashFlowDirection.INFLOW,
            initialAccountId = accountId,
            accountRepository = accounts,
            transactionRepository = transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions),
            createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
                accounts,
                transactions,
                refresh,
                testClockProvider(),
            ),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
            devicePreferencesRepository = preferences,
        )
        advanceUntilIdle()
        viewModel.updateAmount("1.00")
        viewModel.updateOccurredAt(60_000)

        viewModel.save()
        advanceUntilIdle()
        assertEquals(FormTerminalKind.SAVED, viewModel.uiState.value.pendingTerminal?.kind)
        assertEquals(1, transactions.queryAllActiveCashFlowRecords().size)
    }

    @Test
    fun `transfer accepts blank note without confirmation`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val fromId = accounts.createAccount(Account(name = "现金", initialBalance = 100, createdAt = 1))
        val toId = accounts.createAccount(Account(name = "银行卡", initialBalance = 0, createdAt = 1))
        val transactions = InMemoryTransactionRepository()
        val viewModel = transferViewModel(
            accounts = accounts,
            preferences = InMemoryDevicePreferencesRepository(),
            transactions = transactions,
        )
        advanceUntilIdle()
        viewModel.updateFromAccount(fromId)
        viewModel.updateToAccount(toId)
        viewModel.updateAmount("1.00")
        viewModel.updateNote("   ")
        viewModel.updateOccurredAt(60_000)

        viewModel.save()
        advanceUntilIdle()
        assertEquals(FormTerminalKind.SAVED, viewModel.uiState.value.pendingTerminal?.kind)
        assertEquals("", transactions.queryAllActiveTransferRecords().single().note)
    }

    @Test
    fun `transfer trims 200 chars and rejects 201 chars at note field`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val fromId = accounts.createAccount(Account(name = "现金", initialBalance = 100, createdAt = 1))
        val toId = accounts.createAccount(Account(name = "银行卡", initialBalance = 0, createdAt = 1))
        val acceptedTransactions = InMemoryTransactionRepository()
        val accepted = transferViewModel(
            accounts,
            InMemoryDevicePreferencesRepository(),
            acceptedTransactions,
        )
        advanceUntilIdle()
        accepted.updateFromAccount(fromId)
        accepted.updateToAccount(toId)
        accepted.updateAmount("1.00")
        accepted.updateNote("  ${"a".repeat(200)}  ")
        accepted.updateOccurredAt(60_000)
        accepted.save()
        advanceUntilIdle()
        assertEquals("a".repeat(200), acceptedTransactions.queryAllActiveTransferRecords().single().note)

        val rejectedTransactions = InMemoryTransactionRepository()
        val rejected = transferViewModel(
            accounts,
            InMemoryDevicePreferencesRepository(),
            rejectedTransactions,
        )
        advanceUntilIdle()
        rejected.updateFromAccount(fromId)
        rejected.updateToAccount(toId)
        rejected.updateAmount("1.00")
        rejected.updateNote("a".repeat(201))
        rejected.updateOccurredAt(60_000)
        rejected.save()
        advanceUntilIdle()

        assertEquals("备注不能超过 200 个字符", rejected.uiState.value.noteError)
        assertEquals(0, rejectedTransactions.queryAllActiveTransferRecords().size)
    }

    private fun cashViewModel(
        accounts: InMemoryAccountRepository,
        preferences: InMemoryDevicePreferencesRepository,
    ): RecordCashFlowViewModel {
        val transactions = InMemoryTransactionRepository()
        val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        return RecordCashFlowViewModel(
            direction = CashFlowDirection.INFLOW,
            initialAccountId = null,
            accountRepository = accounts,
            transactionRepository = transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions),
            createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
                accounts,
                transactions,
                refresh,
                testClockProvider(),
            ),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
            devicePreferencesRepository = preferences,
        )
    }

    private fun transferViewModel(
        accounts: InMemoryAccountRepository,
        preferences: InMemoryDevicePreferencesRepository,
        transactions: InMemoryTransactionRepository = InMemoryTransactionRepository(),
    ): RecordTransferViewModel {
        val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        return RecordTransferViewModel(
            initialFromAccountId = null,
            accountRepository = accounts,
            transactionRepository = transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions),
            createTransferRecordUseCase = CreateTransferRecordUseCase(
                accounts,
                transactions,
                refresh,
                testClockProvider(),
            ),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
            devicePreferencesRepository = preferences,
        )
    }
}
