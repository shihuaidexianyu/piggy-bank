package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import com.shihuaidexianyu.money.ui.record.EditTransferEffect
import com.shihuaidexianyu.money.ui.record.EditTransferViewModel
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
class EditTransferNotePolicyTest {
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
    fun `legacy long note is readable and untouched save preserves it`() = runTest(dispatcher) {
        val fixture = fixture("旧".repeat(201))
        val handle = SavedStateHandle()
        val viewModel = fixture.viewModel(handle)
        advanceUntilIdle()

        assertEquals(fixture.note, viewModel.uiState.value.note)
        assertEquals(null, viewModel.uiState.value.noteError)
        viewModel.updateAmount("2.00")
        viewModel.save()
        advanceUntilIdle()
        val terminal = requireNotNull(viewModel.uiState.value.pendingTerminal)
        assertEquals(FormTerminalKind.SAVED, terminal.kind)
        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals(terminal, recreated.uiState.value.pendingTerminal)
        recreated.ackTerminal(terminal.token)
        assertEquals(null, recreated.uiState.value.pendingTerminal)
        assertEquals(fixture.note, fixture.transactions.queryTransferRecordById(fixture.recordId)?.note)
    }

    @Test
    fun `touched transfer note over 200 stays in draft and does not update`() = runTest(dispatcher) {
        val fixture = fixture("旧备注")
        val handle = SavedStateHandle()
        val viewModel = fixture.viewModel(handle)
        advanceUntilIdle()

        viewModel.updateNote("a".repeat(201))
        viewModel.save()
        advanceUntilIdle()

        assertEquals("备注不能超过 200 个字符", viewModel.uiState.value.noteError)
        assertEquals("旧备注", fixture.transactions.queryTransferRecordById(fixture.recordId)?.note)
        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals("备注不能超过 200 个字符", recreated.uiState.value.noteError)
        assertEquals("a".repeat(201), recreated.uiState.value.note)
    }

    @Test
    fun `transfer edit draft survives recreation and stale save enters conflict`() = runTest(dispatcher) {
        val fixture = fixture("旧备注")
        val handle = SavedStateHandle()
        val first = fixture.viewModel(handle)
        advanceUntilIdle()
        first.updateAmount("8.88")
        first.updateNote("新备注")
        first.updateOccurredAt(120_000)

        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals("8.88", recreated.uiState.value.amountText)
        assertEquals("新备注", recreated.uiState.value.note)
        assertEquals(120_000, recreated.uiState.value.occurredAtMillis)
        assertTrue(recreated.uiState.value.isDirty)

        val stored = requireNotNull(fixture.transactions.queryTransferRecordById(fixture.recordId))
        assertTrue(
            fixture.transactions.updateTransferRecord(
                stored.copy(amount = 333, updatedAt = stored.updatedAt + 1),
                expectedUpdatedAt = stored.updatedAt,
            ),
        )
        recreated.effectFlow.test {
            recreated.save()
            advanceUntilIdle()
            assertTrue(awaitItem() is EditTransferEffect.ShowMessage)
        }
        assertTrue(recreated.uiState.value.hasConflict)
        assertEquals(333, fixture.transactions.queryTransferRecordById(fixture.recordId)?.amount)

        val conflictRecreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertTrue(conflictRecreated.uiState.value.hasConflict)
        conflictRecreated.reload()
        advanceUntilIdle()
        assertFalse(conflictRecreated.uiState.value.hasConflict)
    }

    @Test
    fun `transfer note only edit survives recreation and stale delete enters conflict`() = runTest(dispatcher) {
        val fixture = fixture("旧备注")
        val handle = SavedStateHandle()
        val first = fixture.viewModel(handle)
        advanceUntilIdle()
        first.updateNote("仅改备注")

        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals("仅改备注", recreated.uiState.value.note)
        val stored = requireNotNull(fixture.transactions.queryTransferRecordById(fixture.recordId))
        assertTrue(
            fixture.transactions.updateTransferRecord(
                stored.copy(amount = 444, updatedAt = stored.updatedAt + 1),
                expectedUpdatedAt = stored.updatedAt,
            ),
        )
        recreated.effectFlow.test {
            recreated.delete()
            advanceUntilIdle()
            assertTrue(awaitItem() is EditTransferEffect.ShowMessage)
        }
        assertTrue(recreated.uiState.value.hasConflict)
        assertEquals(444, fixture.transactions.queryTransferRecordById(fixture.recordId)?.amount)
    }

    @Test
    fun `transfer amount error survives recreation`() = runTest(dispatcher) {
        val fixture = fixture("旧备注")
        val handle = SavedStateHandle()
        val first = fixture.viewModel(handle)
        advanceUntilIdle()
        first.updateAmount("")
        first.save()
        advanceUntilIdle()

        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals("金额不能为空", recreated.uiState.value.amountError)
    }

    @Test
    fun `transfer delete is single flight`() = runTest(dispatcher) {
        val fixture = fixture("备注")
        val viewModel = fixture.viewModel(SavedStateHandle())
        advanceUntilIdle()

        viewModel.delete()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.delete()
        advanceUntilIdle()
        assertEquals(FormTerminalKind.DELETED, viewModel.uiState.value.pendingTerminal?.kind)
        assertEquals(com.shihuaidexianyu.money.domain.model.LedgerRecordKind.TRANSFER, viewModel.uiState.value.pendingTerminal?.ledgerUndoToken?.kind)
        assertEquals(null, fixture.transactions.queryTransferRecordById(fixture.recordId))
    }

    @Test
    fun `failed transfer save keeps draft when follow-up lookup also fails`() = runTest(dispatcher) {
        val fixture = fixture("备注")
        val repository = FailingTransferUpdateAndLookupRepository(fixture.transactions)
        val viewModel = fixture.viewModel(transactions = repository)
        advanceUntilIdle()
        viewModel.updateAmount("8.88")

        viewModel.effectFlow.test {
            viewModel.save()
            advanceUntilIdle()
            val effect = awaitItem() as EditTransferEffect.ShowMessage
            assertEquals("原始转账保存失败", effect.message)
        }
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals("8.88", viewModel.uiState.value.amountText)
        assertEquals(100L, fixture.transactions.queryTransferRecordById(fixture.recordId)?.amount)
    }

    @Test
    fun `initial transfer load exception is retryable and preserves saved draft`() = runTest(dispatcher) {
        val fixture = fixture("备注")
        val repository = ToggleTransferLoadRepository(fixture.transactions)
        val viewModel = fixture.viewModel(SavedStateHandle(), repository)
        advanceUntilIdle()
        assertEquals("记录加载失败，请重试", viewModel.uiState.value.loadErrorMessage)
        viewModel.updateNote("重试草稿")

        repository.available = true
        viewModel.retryLoad(); advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.loadErrorMessage)
        assertEquals("重试草稿", viewModel.uiState.value.note)
    }

    private suspend fun fixture(note: String): Fixture {
        val accounts = InMemoryAccountRepository()
        val fromId = accounts.createAccount(Account(name = "现金", initialBalance = 500, createdAt = 1))
        val toId = accounts.createAccount(Account(name = "银行卡", initialBalance = 0, createdAt = 1))
        val transactions = InMemoryTransactionRepository()
        val recordId = transactions.insertTransferRecord(
            TransferRecord(
                fromAccountId = fromId,
                toAccountId = toId,
                amount = 100,
                note = note,
                occurredAt = 60_000,
                createdAt = 60_000,
                updatedAt = 60_000,
                operationId = testOperationId(),
            ),
        ).recordId
        return Fixture(accounts, transactions, recordId, note)
    }

    private data class Fixture(
        val accounts: InMemoryAccountRepository,
        val transactions: InMemoryTransactionRepository,
        val recordId: Long,
        val note: String,
    ) {
        fun viewModel(
            savedStateHandle: SavedStateHandle = SavedStateHandle(),
            transactions: TransactionRepository = this.transactions,
        ): EditTransferViewModel {
            val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
            return EditTransferViewModel(
                recordId = recordId,
                accountRepository = accounts,
                transactionRepository = transactions,
                calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions),
                updateTransferRecordUseCase = UpdateTransferRecordUseCase(
                    accounts,
                    transactions,
                    refresh,
                    testClockProvider(),
                ),
                deleteTransferRecordUseCase = DeleteTransferRecordUseCase(
                    accounts,
                    transactions,
                    refresh,
                    testClockProvider(),
                ),
                savedStateHandle = savedStateHandle,
            )
        }
    }

    private class FailingTransferUpdateAndLookupRepository(
        private val delegate: InMemoryTransactionRepository,
    ) : TransactionRepository by delegate, LedgerAggregateRepository by delegate {
        private var failLookup = false

        override suspend fun updateTransferRecord(
            record: TransferRecord,
            expectedUpdatedAt: Long,
        ): Boolean {
            failLookup = true
            error("原始转账保存失败")
        }

        override suspend fun queryTransferRecordById(id: Long): TransferRecord? {
            if (failLookup) error("二次查询失败")
            return delegate.queryTransferRecordById(id)
        }
    }

    private class ToggleTransferLoadRepository(
        private val delegate: InMemoryTransactionRepository,
    ) : TransactionRepository by delegate, LedgerAggregateRepository by delegate {
        var available: Boolean = false

        override suspend fun queryTransferRecordById(id: Long): TransferRecord? {
            if (!available) error("database unavailable")
            return delegate.queryTransferRecordById(id)
        }
    }
}
