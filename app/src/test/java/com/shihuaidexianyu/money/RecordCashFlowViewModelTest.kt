package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.ui.record.RecordCashFlowEffect
import com.shihuaidexianyu.money.ui.record.RecordCashFlowViewModel
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
class RecordCashFlowViewModelTest {
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
    fun `save with no account shows account field error`() = runTest(dispatcher) {
        // Use an empty account repository so init finds no accounts and selectedAccountId stays null.
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        val vm = buildViewModel(accountRepo, txnRepo)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals("请选择账户", vm.uiState.value.accountError)
    }

    @Test
    fun `save with blank amount shows amount field error`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.updateAccount(accountId = 1L)
        vm.updateAmount("")
        vm.save()
        advanceUntilIdle()
        assertEquals("金额不能为空", vm.uiState.value.amountError)
    }

    @Test
    fun `save with zero amount shows amount field error`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.updateAccount(accountId = 1L)
        vm.updateAmount("0")
        vm.save()
        advanceUntilIdle()
        assertEquals("金额必须大于 0", vm.uiState.value.amountError)
    }

    @Test
    fun `save with blank note succeeds without confirmation`() = runTest(dispatcher) {
        val repository = InMemoryTransactionRepository()
        val vm = buildViewModel(txnRepo = repository)
        advanceUntilIdle()
        vm.updateAccount(accountId = 1L)
        vm.updateAmount("100")
        vm.updateNote("")
        vm.save()
        advanceUntilIdle()
        assertEquals(FormTerminalKind.SAVED, vm.uiState.value.pendingTerminal?.kind)
        val records = repository.queryAllActiveCashFlowRecords()
        assertEquals(1, records.size)
        assertEquals("", records.single().note)
    }

    @Test
    fun `account picker loads all open balances with one aggregate read`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        repeat(2) { accounts.createAccount(Account(name = "账户$it", initialBalance = 100L, createdAt = 0L)) }
        val ledger = InMemoryTransactionRepository()
        val aggregate = CountingLedgerAggregateRepository(ledger)
        val refresh = RefreshAccountActivityStateUseCase(accounts, ledger)
        RecordCashFlowViewModel(
            direction = CashFlowDirection.INFLOW,
            initialAccountId = null,
            accountRepository = accounts,
            transactionRepository = ledger,
            calculateAccountBalancesUseCase =
                com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase(
                    aggregate,
                    testClockProvider(1L),
                ),
            createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
                accounts,
                ledger,
                refresh,
                testClockProvider(1L),
            ),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
        )

        advanceUntilIdle()

        assertEquals(1, aggregate.beforeCalls)
    }

    @Test
    fun `current mutation picker includes hidden open accounts and excludes closed accounts`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val hiddenId = accountRepo.createAccount(
            Account(name = "隐藏账户", initialBalance = 0, createdAt = 1, isHidden = true),
        )
        val closedId = accountRepo.createAccount(Account(name = "关闭账户", initialBalance = 0, createdAt = 1))
        accountRepo.closeAccount(closedId, closedAt = 2)

        val vm = buildViewModel(accountRepo, InMemoryTransactionRepository())
        advanceUntilIdle()

        assertTrue(vm.uiState.value.accounts.any { it.id == hiddenId })
        assertTrue(vm.uiState.value.accounts.none { it.id == closedId })
    }

    @Test
    fun `reminder save uses the account picker and view model direction`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val reminderAccountId = accountRepo.createAccount(Account(name = "原账户", initialBalance = 0, createdAt = 1L))
        val selectedAccountId = accountRepo.createAccount(Account(name = "所选账户", initialBalance = 0, createdAt = 1L))
        val txnRepo = InMemoryTransactionRepository()
        val reminderRepo = InMemoryRecurringReminderRepository()
        val dueAt = testClockProvider.nowMillis()
        val reminderId = reminderRepo.insertReminder(
            RecurringReminder(
                name = "订阅",
                type = ReminderType.SUBSCRIPTION.value,
                accountId = reminderAccountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 1_200,
                periodType = ReminderPeriodType.CUSTOM_DAYS.value,
                periodValue = 1,
                periodMonth = null,
                nextDueAt = dueAt,
                anchorDueAt = dueAt,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        val refresh = RefreshAccountActivityStateUseCase(accountRepo, txnRepo)
        val processReminder = ProcessDueReminderUseCase(
            accountRepository = accountRepo,
            transactionRepository = txnRepo,
            reminderRepository = reminderRepo,
            refreshAccountActivityStateUseCase = refresh,
            clockProvider = testClockProvider,
            zoneIdProvider = { java.time.ZoneId.of("UTC") },
        )
        val vm = RecordCashFlowViewModel(
            direction = CashFlowDirection.INFLOW,
            initialAccountId = reminderAccountId,
            prefillAmount = 1_200,
            prefillNote = "会员续费",
            reminderId = reminderId,
            expectedDueAt = dueAt,
            accountRepository = accountRepo,
            transactionRepository = txnRepo,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(txnRepo),
            createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
                accountRepo,
                txnRepo,
                refresh,
                testClockProvider,
            ),
            processDueReminderUseCase = processReminder,
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
        )
        advanceUntilIdle()
        vm.updateAccount(selectedAccountId)

        vm.save()
        advanceUntilIdle()
        assertEquals(FormTerminalKind.SAVED, vm.uiState.value.pendingTerminal?.kind)

        val stored = txnRepo.queryAllCashFlowRecords().single()
        assertEquals(selectedAccountId, stored.accountId)
        assertEquals(CashFlowDirection.INFLOW.value, stored.direction)
    }

    @Test
    fun `updateOccurredAt floors to minute boundary`() = runTest(dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.updateOccurredAt(1_000_042L) // 16m 42s → floor to 960_000
        assertEquals(960_000L, vm.uiState.value.occurredAtMillis)
    }

    private fun buildViewModel(
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository().also { repo ->
            kotlinx.coroutines.runBlocking { repo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L)) }
        },
        txnRepo: InMemoryTransactionRepository = InMemoryTransactionRepository(),
    ): RecordCashFlowViewModel {
        val refreshUseCase = RefreshAccountActivityStateUseCase(accountRepo, txnRepo)
        val calculateUseCase = CalculateAccountBalancesUseCase(txnRepo)
        val createUseCase = CreateCashFlowRecordUseCase(
            accountRepo,
            txnRepo,
            refreshUseCase,
            testClockProvider,
        )
        return RecordCashFlowViewModel(
            direction = CashFlowDirection.INFLOW,
            initialAccountId = null,
            prefillAmount = null,
            prefillNote = null,
            reminderId = null,
            accountRepository = accountRepo,
            transactionRepository = txnRepo,
            calculateAccountBalancesUseCase = calculateUseCase,
            createCashFlowRecordUseCase = createUseCase,
            processDueReminderUseCase = null,
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
        )
    }
}
