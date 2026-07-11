package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.ui.record.EditCashFlowEffect
import com.shihuaidexianyu.money.ui.record.EditCashFlowViewModel
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
class EditCashFlowViewModelTest {
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
    fun `load with missing record emits Deleted once`() = runTest(dispatcher) {
        val vm = buildViewModel(recordId = 999L)
        advanceUntilIdle()
        assertEquals(FormTerminalKind.DELETED, vm.uiState.value.pendingTerminal?.kind)
        assertEquals(null, vm.uiState.value.pendingTerminal?.ledgerUndoToken)
    }

    @Test
    fun `save with blank amount persists field error`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = "早餐",
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                operationId = testOperationId(),
            ),
        ).recordId

        val handle = SavedStateHandle()
        val vm = buildViewModel(recordId = recordId, accountRepo = accountRepo, txnRepo = txnRepo, savedStateHandle = handle)
        advanceUntilIdle()

        vm.updateAmount("")
        vm.save()
        advanceUntilIdle()
        assertEquals("金额不能为空", vm.uiState.value.amountError)
        val recreated = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()
        assertEquals("金额不能为空", recreated.uiState.value.amountError)
    }

    @Test
    fun `save success emits Saved and updates record`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = "早餐",
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                operationId = testOperationId(),
            ),
        ).recordId

        val handle = SavedStateHandle()
        val vm = buildViewModel(
            recordId = recordId,
            accountRepo = accountRepo,
            txnRepo = txnRepo,
            savedStateHandle = handle,
        )
        advanceUntilIdle()

        vm.updateAmount("999")
        vm.updateNote("午餐")
        vm.save()
        advanceUntilIdle()
        val terminal = requireNotNull(vm.uiState.value.pendingTerminal)
        assertEquals(FormTerminalKind.SAVED, terminal.kind)
        val recreated = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()
        assertEquals(terminal, recreated.uiState.value.pendingTerminal)
        recreated.ackTerminal(terminal.token)
        assertEquals(null, recreated.uiState.value.pendingTerminal)
        val record = txnRepo.queryCashFlowRecordById(recordId)
        assertEquals(99_900L, record?.amount)
        assertEquals("午餐", record?.note)
    }

    @Test
    fun `edited note over 200 chars stays in draft and does not update record`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = "旧备注",
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                operationId = testOperationId(),
            ),
        ).recordId
        val handle = SavedStateHandle()
        val vm = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()

        vm.updateNote("a".repeat(201))
        vm.save()
        advanceUntilIdle()

        assertEquals("备注不能超过 200 个字符", vm.uiState.value.noteError)
        assertEquals("旧备注", txnRepo.queryCashFlowRecordById(recordId)?.note)
        val recreated = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()
        assertEquals("备注不能超过 200 个字符", recreated.uiState.value.noteError)
        assertEquals("a".repeat(201), recreated.uiState.value.note)
    }

    @Test
    fun `legacy long note remains readable and untouched save preserves it`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val legacyNote = "旧".repeat(201)
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = legacyNote,
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                operationId = testOperationId(),
            ),
        ).recordId
        val vm = buildViewModel(recordId, accountRepo, txnRepo)
        advanceUntilIdle()

        assertEquals(legacyNote, vm.uiState.value.note)
        assertEquals(null, vm.uiState.value.noteError)
        vm.updateAmount("6.00")
        vm.save()
        advanceUntilIdle()
        assertEquals(FormTerminalKind.SAVED, vm.uiState.value.pendingTerminal?.kind)

        assertEquals(legacyNote, txnRepo.queryCashFlowRecordById(recordId)?.note)
    }

    @Test
    fun `edit draft survives recreation and stale save enters conflict`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = "早餐",
                occurredAt = 60_000L,
                createdAt = 60_000L,
                updatedAt = 60_000L,
                operationId = testOperationId(),
            ),
        ).recordId
        val handle = SavedStateHandle()
        val first = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()
        first.updateAmount("8.88")
        first.updateNote("午餐")
        first.updateOccurredAt(120_000)

        val recreated = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()
        assertEquals("8.88", recreated.uiState.value.amountText)
        assertEquals("午餐", recreated.uiState.value.note)
        assertEquals(120_000, recreated.uiState.value.occurredAtMillis)
        assertTrue(recreated.uiState.value.isDirty)

        val stored = requireNotNull(txnRepo.queryCashFlowRecordById(recordId))
        assertTrue(
            txnRepo.updateCashFlowRecord(
                stored.copy(amount = 777, updatedAt = stored.updatedAt + 1),
                expectedUpdatedAt = stored.updatedAt,
            ),
        )
        recreated.effectFlow.test {
            recreated.save()
            advanceUntilIdle()
            assertTrue(awaitItem() is EditCashFlowEffect.ShowMessage)
        }
        assertTrue(recreated.uiState.value.hasConflict)
        assertEquals(777, txnRepo.queryCashFlowRecordById(recordId)?.amount)

        val conflictRecreated = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()
        assertTrue(conflictRecreated.uiState.value.hasConflict)
        conflictRecreated.reload()
        advanceUntilIdle()
        assertFalse(conflictRecreated.uiState.value.hasConflict)
    }

    @Test
    fun `note only edit survives recreation and stale delete enters conflict`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = "早餐",
                occurredAt = 60_000L,
                createdAt = 60_000L,
                updatedAt = 60_000L,
                operationId = testOperationId(),
            ),
        ).recordId
        val handle = SavedStateHandle()
        val first = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()
        first.updateNote("仅改备注")

        val recreated = buildViewModel(recordId, accountRepo, txnRepo, handle)
        advanceUntilIdle()
        assertEquals("仅改备注", recreated.uiState.value.note)
        val stored = requireNotNull(txnRepo.queryCashFlowRecordById(recordId))
        assertTrue(
            txnRepo.updateCashFlowRecord(
                stored.copy(amount = 999, updatedAt = stored.updatedAt + 1),
                expectedUpdatedAt = stored.updatedAt,
            ),
        )

        recreated.effectFlow.test {
            recreated.delete()
            advanceUntilIdle()
            assertTrue(awaitItem() is EditCashFlowEffect.ShowMessage)
        }
        assertTrue(recreated.uiState.value.hasConflict)
        assertEquals(999, txnRepo.queryCashFlowRecordById(recordId)?.amount)
    }

    @Test
    fun `delete emits Deleted and soft-deletes record`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = "早餐",
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
                operationId = testOperationId(),
            ),
        ).recordId

        val vm = buildViewModel(recordId = recordId, accountRepo = accountRepo, txnRepo = txnRepo)
        advanceUntilIdle()

        vm.delete()
        assertTrue(vm.uiState.value.isSaving)
        vm.delete()
        advanceUntilIdle()
        assertEquals(FormTerminalKind.DELETED, vm.uiState.value.pendingTerminal?.kind)
        assertEquals(com.shihuaidexianyu.money.domain.model.LedgerRecordKind.CASH_FLOW, vm.uiState.value.pendingTerminal?.ledgerUndoToken?.kind)
        assertEquals(null, txnRepo.queryCashFlowRecordById(recordId))
    }

    @Test
    fun `failed save keeps draft when follow-up lookup also fails`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val delegate = InMemoryTransactionRepository()
        accounts.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = delegate.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = "早餐",
                occurredAt = 60_000L,
                createdAt = 60_000L,
                updatedAt = 60_000L,
                operationId = testOperationId(),
            ),
        ).recordId
        val repository = FailingCashUpdateAndLookupRepository(delegate)
        val viewModel = buildViewModel(recordId, accounts, repository)
        advanceUntilIdle()
        viewModel.updateAmount("8.88")

        viewModel.effectFlow.test {
            viewModel.save()
            advanceUntilIdle()
            val effect = awaitItem() as EditCashFlowEffect.ShowMessage
            assertEquals("原始保存失败", effect.message)
        }
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("8.88", viewModel.uiState.value.amountText)
        assertEquals(500L, delegate.queryCashFlowRecordById(recordId)?.amount)
    }

    @Test
    fun `initial load exception is retryable and preserves saved draft`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val delegate = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = delegate.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                note = "早餐",
                occurredAt = 60_000L,
                createdAt = 60_000L,
                updatedAt = 60_000L,
                operationId = testOperationId(),
            ),
        ).recordId
        val repository = ToggleCashLoadRepository(delegate)
        val viewModel = buildViewModel(recordId, accounts, repository, SavedStateHandle())
        advanceUntilIdle()
        assertEquals("记录加载失败，请重试", viewModel.uiState.value.loadErrorMessage)
        viewModel.updateAmount("8.88")

        repository.available = true
        viewModel.retryLoad(); advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.loadErrorMessage)
        assertEquals("8.88", viewModel.uiState.value.amountText)
    }

    private fun buildViewModel(
        recordId: Long,
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository(),
        txnRepo: TransactionRepository = InMemoryTransactionRepository(),
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): EditCashFlowViewModel {
        val refreshUseCase = RefreshAccountActivityStateUseCase(accountRepo, txnRepo)
        val calculateUseCase = CalculateAccountBalancesUseCase(txnRepo)
        val updateUseCase = UpdateCashFlowRecordUseCase(
            accountRepo,
            txnRepo,
            refreshUseCase,
            testClockProvider,
        )
        val deleteUseCase = DeleteCashFlowRecordUseCase(accountRepo, txnRepo, refreshUseCase, testClockProvider)
        return EditCashFlowViewModel(
            recordId = recordId,
            accountRepository = accountRepo,
            transactionRepository = txnRepo,
            calculateAccountBalancesUseCase = calculateUseCase,
            updateCashFlowRecordUseCase = updateUseCase,
            deleteCashFlowRecordUseCase = deleteUseCase,
            savedStateHandle = savedStateHandle,
        )
    }

    private class FailingCashUpdateAndLookupRepository(
        private val delegate: InMemoryTransactionRepository,
    ) : TransactionRepository by delegate, LedgerAggregateRepository by delegate {
        private var failLookup = false

        override suspend fun updateCashFlowRecord(
            record: CashFlowRecord,
            expectedUpdatedAt: Long,
        ): Boolean {
            failLookup = true
            error("原始保存失败")
        }

        override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord? {
            if (failLookup) error("二次查询失败")
            return delegate.queryCashFlowRecordById(id)
        }
    }

    private class ToggleCashLoadRepository(
        private val delegate: InMemoryTransactionRepository,
    ) : TransactionRepository by delegate, LedgerAggregateRepository by delegate {
        var available: Boolean = false

        override suspend fun queryCashFlowRecordById(id: Long): CashFlowRecord? {
            if (!available) error("database unavailable")
            return delegate.queryCashFlowRecordById(id)
        }
    }
}
