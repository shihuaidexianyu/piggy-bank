package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemorySettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.balance.BatchReconcileViewModel
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceViewModel
import com.shihuaidexianyu.money.ui.record.RecordCashFlowViewModel
import com.shihuaidexianyu.money.ui.record.RecordTransferViewModel
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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerFormSavedStateTest {
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
    fun `cash form reuses saved id across double click failure retry and recreation`() = runTest(dispatcher) {
        val accountRepository = InMemoryAccountRepository()
        val delegate = InMemoryTransactionRepository()
        val repository = RecordingTransactionRepository(delegate).apply { cashFailures = 1 }
        accountRepository.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1))
        val handle = SavedStateHandle()
        val ids = SequenceOperationIdFactory()

        val first = cashViewModel(accountRepository, repository, handle, ids)
        advanceUntilIdle()
        configureCash(first)
        first.save()
        first.save()
        advanceUntilIdle()
        first.save()
        advanceUntilIdle()

        val recreated = cashViewModel(accountRepository, repository, handle, ids)
        advanceUntilIdle()
        configureCash(recreated)
        recreated.save()
        advanceUntilIdle()

        assertEquals(listOf("form-op-1", "form-op-1", "form-op-1"), repository.cashCalls.map { it.operationId })
        assertEquals(1, ids.createCount)
        assertEquals(1, delegate.queryAllCashFlowRecords().size)
    }

    @Test
    fun `transfer form reuses saved id across double click failure retry and recreation`() = runTest(dispatcher) {
        val accountRepository = InMemoryAccountRepository()
        val delegate = InMemoryTransactionRepository()
        val repository = RecordingTransactionRepository(delegate).apply { transferFailures = 1 }
        val fromId = accountRepository.createAccount(Account(name = "银行卡", initialBalance = 100, createdAt = 1))
        val toId = accountRepository.createAccount(Account(name = "零钱", initialBalance = 0, createdAt = 1))
        val handle = SavedStateHandle()
        val ids = SequenceOperationIdFactory()

        val first = transferViewModel(accountRepository, repository, handle, ids, fromId)
        advanceUntilIdle()
        configureTransfer(first, fromId, toId)
        first.save()
        first.save()
        advanceUntilIdle()
        first.save()
        advanceUntilIdle()

        val recreated = transferViewModel(accountRepository, repository, handle, ids, fromId)
        advanceUntilIdle()
        configureTransfer(recreated, fromId, toId)
        recreated.save()
        advanceUntilIdle()

        assertEquals(listOf("form-op-1", "form-op-1", "form-op-1"), repository.transferCalls.map { it.operationId })
        assertEquals(1, ids.createCount)
        assertEquals(1, delegate.queryAllTransferRecords().size)
    }

    @Test
    fun `transfer picker includes hidden open accounts and excludes closed accounts`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val visibleId = accounts.createAccount(
            Account(name = "显示", initialBalance = 100L, createdAt = 1L, displayOrder = 0),
        )
        val hiddenId = accounts.createAccount(
            Account(name = "隐藏", initialBalance = 200L, createdAt = 2L, isHidden = true, displayOrder = 1),
        )
        val closedId = accounts.createAccount(
            Account(name = "关闭", initialBalance = 0L, createdAt = 3L, displayOrder = 2),
        )
        accounts.closeAccount(closedId, 4L)

        val viewModel = transferViewModel(
            accounts = accounts,
            transactions = transactions,
            handle = SavedStateHandle(),
            ids = SequenceOperationIdFactory(),
            fromId = visibleId,
        )
        advanceUntilIdle()

        assertEquals(listOf(visibleId, hiddenId), viewModel.uiState.value.accounts.map { it.id })
    }

    @Test
    fun `balance form reuses saved id across double click failure retry and recreation`() = runTest(dispatcher) {
        val accountRepository = InMemoryAccountRepository()
        val delegate = InMemoryTransactionRepository()
        val repository = RecordingTransactionRepository(delegate)
        val accountId = accountRepository.createAccount(Account(name = "现金", initialBalance = 100, createdAt = 1))
        repository.balanceFailuresByAccount[accountId] = 1
        val handle = SavedStateHandle()
        val ids = SequenceOperationIdFactory()
        val clock = MutableClock(System.currentTimeMillis())

        val first = balanceViewModel(accountRepository, repository, handle, ids, clock, accountId)
        advanceUntilIdle()
        configureBalance(first)
        first.save()
        first.save()
        advanceUntilIdle()
        first.save()
        advanceUntilIdle()

        val recreated = balanceViewModel(accountRepository, repository, handle, ids, clock, accountId)
        advanceUntilIdle()
        configureBalance(recreated)
        recreated.save()
        advanceUntilIdle()

        assertEquals(listOf("form-op-1", "form-op-1", "form-op-1"), repository.balanceCalls.map { it.operationId })
        assertEquals(1, ids.createCount)
        assertEquals(1, delegate.queryAllBalanceUpdateRecords().size)
    }

    @Test
    fun `batch keeps distinct per-account ids and occurrence time across partial retry recreation`() = runTest(dispatcher) {
        val accountRepository = InMemoryAccountRepository()
        val delegate = InMemoryTransactionRepository()
        val repository = RecordingTransactionRepository(delegate)
        val firstAccountId = accountRepository.createAccount(Account(name = "一号", initialBalance = 100, createdAt = 1))
        val secondAccountId = accountRepository.createAccount(Account(name = "二号", initialBalance = 200, createdAt = 1))
        repository.balanceFailuresByAccount[secondAccountId] = 1
        val handle = SavedStateHandle()
        val ids = SequenceOperationIdFactory()
        val clock = MutableClock(System.currentTimeMillis())

        val first = batchViewModel(accountRepository, repository, handle, ids, clock)
        var attempts = 0
        while (first.uiState.value.isLoading && attempts < 100) {
            advanceUntilIdle()
            Thread.sleep(2)
            attempts += 1
        }
        assertEquals(setOf(firstAccountId, secondAccountId), first.uiState.value.accounts.map { it.accountId }.toSet())
        first.saveSelected()
        first.saveSelected()
        advanceUntilIdle()
        assertEquals(listOf(firstAccountId, secondAccountId), repository.balanceCalls.map { it.accountId })
        assertTrue(accountRepository.getAccountById(firstAccountId)?.lastBalanceUpdateAt != null)

        delegate.insertCashFlowRecord(
            CashFlowRecord(
                accountId = secondAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 50,
                note = "重试前新增收入",
                occurredAt = clock.now - 60_000,
                createdAt = clock.now,
                updatedAt = clock.now,
                operationId = "batch-balance-change",
            ),
        )
        clock.now += 120_000
        val recreated = batchViewModel(accountRepository, repository, handle, ids, clock)
        attempts = 0
        while (recreated.uiState.value.isLoading && attempts < 100) {
            advanceUntilIdle()
            Thread.sleep(2)
            attempts += 1
        }
        assertEquals(listOf(secondAccountId), recreated.uiState.value.accounts.map { it.accountId })
        assertEquals(250L, recreated.uiState.value.accounts.single().systemBalance)
        recreated.saveSelected()
        advanceUntilIdle()

        val firstCalls = repository.balanceCalls.filter { it.accountId == firstAccountId }
        val secondCalls = repository.balanceCalls.filter { it.accountId == secondAccountId }
        assertEquals(1, firstCalls.size)
        assertEquals(2, secondCalls.size)
        assertEquals(1, secondCalls.map { it.operationId }.distinct().size)
        assertTrue(firstCalls.single().operationId != secondCalls.first().operationId)
        assertEquals(1, secondCalls.map { it.occurredAt }.distinct().size)
        assertEquals(listOf(200L, 200L), secondCalls.map { it.actualBalance })
        assertEquals(2, ids.createCount)
        assertEquals(2, delegate.queryAllBalanceUpdateRecords().size)
    }

    private fun cashViewModel(
        accounts: InMemoryAccountRepository,
        transactions: TransactionRepository,
        handle: SavedStateHandle,
        ids: LedgerOperationIdFactory,
    ): RecordCashFlowViewModel {
        val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        return RecordCashFlowViewModel(
            direction = CashFlowDirection.INFLOW,
            initialAccountId = 1,
            accountRepository = accounts,
            transactionRepository = transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions, testClockProvider()),
            createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(
                accounts,
                transactions,
                refresh,
                testClockProvider(),
            ),
            savedStateHandle = handle,
            operationIdFactory = ids,
        )
    }

    private fun configureCash(viewModel: RecordCashFlowViewModel) {
        viewModel.updateAccount(1)
        viewModel.updateAmount("1.00")
        viewModel.updateNote("工资")
        viewModel.updateOccurredAt(60_000)
    }

    private fun transferViewModel(
        accounts: InMemoryAccountRepository,
        transactions: TransactionRepository,
        handle: SavedStateHandle,
        ids: LedgerOperationIdFactory,
        fromId: Long,
    ): RecordTransferViewModel {
        val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        return RecordTransferViewModel(
            initialFromAccountId = fromId,
            accountRepository = accounts,
            transactionRepository = transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions, testClockProvider()),
            createTransferRecordUseCase = CreateTransferRecordUseCase(
                accounts,
                transactions,
                refresh,
                testClockProvider(),
            ),
            savedStateHandle = handle,
            operationIdFactory = ids,
        )
    }

    private fun configureTransfer(viewModel: RecordTransferViewModel, fromId: Long, toId: Long) {
        viewModel.updateFromAccount(fromId)
        viewModel.updateToAccount(toId)
        viewModel.updateAmount("1.00")
        viewModel.updateNote("调拨")
        viewModel.updateOccurredAt(60_000)
    }

    private fun balanceViewModel(
        accounts: InMemoryAccountRepository,
        transactions: TransactionRepository,
        handle: SavedStateHandle,
        ids: LedgerOperationIdFactory,
        clock: ClockProvider,
        accountId: Long,
    ): UpdateBalanceViewModel {
        return UpdateBalanceViewModel(
            initialAccountId = accountId,
            accountRepository = accounts,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, transactions, clock),
            updateBalanceUseCase = updateBalanceUseCase(accounts, transactions, clock),
            savedStateHandle = handle,
            operationIdFactory = ids,
        )
    }

    private fun configureBalance(viewModel: UpdateBalanceViewModel) {
        viewModel.updateOccurredAt(60_000)
        viewModel.updateActualBalance("1.00")
    }

    private fun batchViewModel(
        accounts: InMemoryAccountRepository,
        transactions: TransactionRepository,
        handle: SavedStateHandle,
        ids: LedgerOperationIdFactory,
        clock: ClockProvider,
    ): BatchReconcileViewModel {
        return BatchReconcileViewModel(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            settingsRepository = InMemorySettingsRepository(),
            transactionRepository = transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions, clock),
            updateBalanceUseCase = updateBalanceUseCase(accounts, transactions, clock),
            savedStateHandle = handle,
            operationIdFactory = ids,
            clockProvider = clock,
        )
    }

    private fun updateBalanceUseCase(
        accounts: InMemoryAccountRepository,
        transactions: TransactionRepository,
        clock: ClockProvider,
    ): UpdateBalanceUseCase {
        return UpdateBalanceUseCase(
            accountRepository = accounts,
            transactionRepository = transactions,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(accounts, transactions),
            refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(accounts, transactions),
            clockProvider = clock,
        )
    }

    private class MutableClock(var now: Long) : ClockProvider {
        override fun nowMillis(): Long = now
    }

    private class SequenceOperationIdFactory : LedgerOperationIdFactory {
        var createCount = 0
            private set

        override fun create(): String {
            createCount += 1
            return "form-op-$createCount"
        }
    }

    private class RecordingTransactionRepository(
        private val delegate: InMemoryTransactionRepository,
    ) : TransactionRepository by delegate, LedgerAggregateRepository by delegate {
        val cashCalls = mutableListOf<CashFlowRecord>()
        val transferCalls = mutableListOf<TransferRecord>()
        val balanceCalls = mutableListOf<BalanceUpdateRecord>()
        var cashFailures = 0
        var transferFailures = 0
        val balanceFailuresByAccount = mutableMapOf<Long, Int>()

        override suspend fun insertCashFlowRecord(record: CashFlowRecord): LedgerInsertResult {
            cashCalls += record
            if (cashFailures > 0) {
                cashFailures -= 1
                error("cash failure")
            }
            return delegate.insertCashFlowRecord(record)
        }

        override suspend fun insertTransferRecord(record: TransferRecord): LedgerInsertResult {
            transferCalls += record
            if (transferFailures > 0) {
                transferFailures -= 1
                error("transfer failure")
            }
            return delegate.insertTransferRecord(record)
        }

        override suspend fun insertBalanceUpdateRecord(record: BalanceUpdateRecord): LedgerInsertResult {
            balanceCalls += record
            val remaining = balanceFailuresByAccount[record.accountId] ?: 0
            if (remaining > 0) {
                balanceFailuresByAccount[record.accountId] = remaining - 1
                error("balance failure")
            }
            return delegate.insertBalanceUpdateRecord(record)
        }
    }
}
