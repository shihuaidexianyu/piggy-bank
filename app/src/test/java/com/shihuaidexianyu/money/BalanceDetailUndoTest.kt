package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.*
import com.shihuaidexianyu.money.domain.model.*
import com.shihuaidexianyu.money.domain.usecase.*
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.ui.balance.*
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import kotlin.test.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*

@OptIn(ExperimentalCoroutinesApi::class)
class BalanceDetailUndoTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) = runnable.run()
            override fun postToMainThread(runnable: Runnable) = runnable.run()
            override fun isMainThread() = true
        })
    }
    @After fun tearDown() { Dispatchers.resetMain(); ArchTaskExecutor.getInstance().setDelegate(null) }

    @Test
    fun `balance update detail delete token survives recreation`() = runTest(dispatcher) {
        val f = Fixture(); val id = f.insertUpdate(); val handle = SavedStateHandle()
        val repository = YieldingDeleteRepository(f.transactions, f.accountId)
        val first = f.updateViewModel(id, handle, repository); advanceUntilIdle(); first.delete(); advanceUntilIdle()
        val terminal = requireNotNull(first.uiState.value.pendingTerminal)
        assertEquals(FormTerminalKind.DELETED, terminal.kind)
        assertEquals(LedgerRecordKind.BALANCE_UPDATE, terminal.ledgerUndoToken?.kind)
        first.delete(); advanceUntilIdle()
        assertEquals(1, repository.balanceUpdateDeleteCalls)
        val recreated = f.updateViewModel(id, handle, repository); advanceUntilIdle()
        assertEquals(terminal, recreated.uiState.value.pendingTerminal)
        recreated.ackTerminal("wrong"); assertEquals(terminal, recreated.uiState.value.pendingTerminal)
        recreated.ackTerminal(terminal.token); assertNull(recreated.uiState.value.pendingTerminal)
    }

    @Test
    fun `manual adjustment detail uses stale delete CAS`() = runTest(dispatcher) {
        val f = Fixture(); val id = f.insertAdjustment(); val vm = f.adjustmentViewModel(id, SavedStateHandle())
        advanceUntilIdle()
        val stored = requireNotNull(f.transactions.getBalanceAdjustmentRecordById(id))
        assertTrue(f.transactions.updateBalanceAdjustmentRecord(stored.copy(delta = 99, updatedAt = stored.updatedAt + 1), stored.updatedAt))
        vm.delete(); advanceUntilIdle()
        assertNull(vm.uiState.value.pendingTerminal)
        assertFalse(vm.uiState.value.isDeleting)
        assertEquals(99, f.transactions.getBalanceAdjustmentRecordById(id)?.delta)
    }

    @Test
    fun `balance update detail uses stale delete CAS`() = runTest(dispatcher) {
        val f = Fixture(); val id = f.insertUpdate(); val vm = f.updateViewModel(id, SavedStateHandle())
        advanceUntilIdle()
        val stored = requireNotNull(f.transactions.getBalanceUpdateRecordById(id))
        assertTrue(f.transactions.updateBalanceUpdateRecord(stored.copy(actualBalance = 88, updatedAt = stored.updatedAt + 1), stored.updatedAt))
        vm.delete(); advanceUntilIdle()
        assertNull(vm.uiState.value.pendingTerminal)
        assertFalse(vm.uiState.value.isDeleting)
        assertEquals(88, f.transactions.getBalanceUpdateRecordById(id)?.actualBalance)
    }

    @Test
    fun `manual adjustment detail delete carries fourth kind token`() = runTest(dispatcher) {
        val f = Fixture(); val id = f.insertAdjustment(); val handle = SavedStateHandle()
        val repository = YieldingDeleteRepository(f.transactions, f.accountId)
        val vm = f.adjustmentViewModel(id, handle, repository); advanceUntilIdle(); vm.delete(); advanceUntilIdle()
        val terminal = requireNotNull(vm.uiState.value.pendingTerminal)
        assertEquals(LedgerRecordKind.BALANCE_ADJUSTMENT, terminal.ledgerUndoToken?.kind)
        val recreated = f.adjustmentViewModel(id, handle, repository); advanceUntilIdle()
        assertEquals(terminal, recreated.uiState.value.pendingTerminal)
    }

    @Test
    fun `balance update load error is retryable and blocks delete without fake zero detail`() = runTest(dispatcher) {
        val f = Fixture(); val id = f.insertUpdate()
        val repository = ToggleReadRepository(f.transactions)
        val vm = f.updateViewModel(id, SavedStateHandle(), repository)
        advanceUntilIdle()

        assertEquals("核对记录加载失败，请重试", vm.uiState.value.loadErrorMessage)
        assertNull(vm.uiState.value.pendingTerminal)
        vm.delete(); advanceUntilIdle()
        assertNull(vm.uiState.value.pendingTerminal)

        repository.available = true
        vm.retryLoad(); advanceUntilIdle()
        assertNull(vm.uiState.value.loadErrorMessage)
        assertEquals(10L, vm.uiState.value.actualBalance)
    }

    @Test
    fun `adjustment load error is retryable and blocks delete without reporting deleted`() = runTest(dispatcher) {
        val f = Fixture(); val id = f.insertAdjustment()
        val repository = ToggleReadRepository(f.transactions)
        val vm = f.adjustmentViewModel(id, SavedStateHandle(), repository)
        advanceUntilIdle()

        assertEquals("调整记录加载失败，请重试", vm.uiState.value.loadErrorMessage)
        assertNull(vm.uiState.value.pendingTerminal)
        vm.delete(); advanceUntilIdle()
        assertNull(vm.uiState.value.pendingTerminal)

        repository.available = true
        vm.retryLoad(); advanceUntilIdle()
        assertNull(vm.uiState.value.loadErrorMessage)
        assertEquals(10L, vm.uiState.value.delta)
    }

    private class Fixture {
        val accounts = InMemoryAccountRepository(); val transactions = InMemoryTransactionRepository()
        val accountId = kotlinx.coroutines.runBlocking { accounts.createAccount(Account(name="现金", initialBalance=0, createdAt=1)) }
        val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        fun insertUpdate() = kotlinx.coroutines.runBlocking { transactions.insertBalanceUpdateRecord(BalanceUpdateRecord(accountId=accountId, actualBalance=10, systemBalanceBeforeUpdate=0, delta=10, occurredAt=2, createdAt=2, updatedAt=2, operationId="u")).recordId }
        fun insertAdjustment() = kotlinx.coroutines.runBlocking { transactions.insertBalanceAdjustmentRecord(BalanceAdjustmentRecord(accountId=accountId, delta=10, occurredAt=2, createdAt=2, updatedAt=2, operationId="a")).recordId }
        fun updateViewModel(id: Long, handle: SavedStateHandle, repository: TransactionRepository = transactions): BalanceUpdateDetailViewModel {
            require(repository is LedgerAggregateRepository)
            val localRefresh = RefreshAccountActivityStateUseCase(accounts, repository)
            return BalanceUpdateDetailViewModel(id, accounts, repository, DeleteBalanceUpdateRecordUseCase(accounts, repository, localRefresh, testClockProvider(100)), handle)
        }
        fun adjustmentViewModel(id: Long, handle: SavedStateHandle, repository: TransactionRepository = transactions): BalanceAdjustmentDetailViewModel {
            require(repository is LedgerAggregateRepository)
            val localRefresh = RefreshAccountActivityStateUseCase(accounts, repository)
            return BalanceAdjustmentDetailViewModel(id, accounts, repository, DeleteBalanceAdjustmentUseCase(accounts, repository, localRefresh, testClockProvider(100)), handle)
        }
    }

    private class YieldingDeleteRepository(
        private val delegate: InMemoryTransactionRepository,
        private val accountId: Long,
    ) : TransactionRepository by delegate, LedgerAggregateRepository by delegate {
        var balanceUpdateDeleteCalls: Int = 0

        override suspend fun softDeleteBalanceUpdateRecord(id: Long, operationId: String, expectedUpdatedAt: Long, deletedAt: Long): Boolean {
            balanceUpdateDeleteCalls += 1
            delegate.insertBalanceAdjustmentRecord(
                BalanceAdjustmentRecord(
                    accountId = accountId,
                    delta = 1,
                    occurredAt = 1,
                    createdAt = 1,
                    updatedAt = 1,
                    operationId = "forced-active-update-invalidation",
                ),
            )
            kotlinx.coroutines.yield()
            val result = delegate.softDeleteBalanceUpdateRecord(id, operationId, expectedUpdatedAt, deletedAt)
            kotlinx.coroutines.yield()
            return result
        }
        override suspend fun softDeleteBalanceAdjustmentRecord(id: Long, operationId: String, expectedUpdatedAt: Long, deletedAt: Long): Boolean {
            delegate.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = 1,
                    note = "",
                    occurredAt = 1,
                    createdAt = 1,
                    updatedAt = 1,
                    operationId = "forced-active-adjustment-invalidation",
                ),
            )
            kotlinx.coroutines.yield()
            val result = delegate.softDeleteBalanceAdjustmentRecord(id, operationId, expectedUpdatedAt, deletedAt)
            kotlinx.coroutines.yield()
            return result
        }
    }

    private class ToggleReadRepository(
        private val delegate: InMemoryTransactionRepository,
    ) : TransactionRepository by delegate, LedgerAggregateRepository by delegate {
        var available: Boolean = false

        override fun observeChangeVersion(): kotlinx.coroutines.flow.Flow<Long> =
            kotlinx.coroutines.flow.flowOf(0L)

        override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord? {
            if (!available) error("database unavailable")
            return delegate.getBalanceUpdateRecordById(id)
        }

        override suspend fun getBalanceAdjustmentRecordById(id: Long): BalanceAdjustmentRecord? {
            if (!available) error("database unavailable")
            return delegate.getBalanceAdjustmentRecordById(id)
        }
    }
}
