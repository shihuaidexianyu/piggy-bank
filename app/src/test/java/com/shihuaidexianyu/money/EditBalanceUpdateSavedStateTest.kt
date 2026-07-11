package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.ui.balance.EditBalanceUpdateEffect
import com.shihuaidexianyu.money.ui.balance.EditBalanceUpdateViewModel
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
class EditBalanceUpdateSavedStateTest {
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
    fun `balance edit draft survives recreation and stale save enters conflict`() = runTest(dispatcher) {
        val fixture = fixture()
        val handle = SavedStateHandle()
        val first = fixture.viewModel(handle)
        advanceUntilIdle()
        first.updateActualBalance("8.88")
        first.updateOccurredAt(120_000)

        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals("8.88", recreated.uiState.value.actualBalanceText)
        assertEquals(120_000, recreated.uiState.value.occurredAtMillis)
        assertTrue(recreated.uiState.value.isDirty)

        val stored = requireNotNull(fixture.transactions.getBalanceUpdateRecordById(fixture.recordId))
        assertTrue(
            fixture.transactions.updateBalanceUpdateRecord(
                stored.copy(actualBalance = 777, updatedAt = stored.updatedAt + 1),
                expectedUpdatedAt = stored.updatedAt,
            ),
        )
        recreated.effectFlow.test {
            recreated.save()
            advanceUntilIdle()
            assertTrue(awaitItem() is EditBalanceUpdateEffect.ShowMessage)
        }
        assertTrue(recreated.uiState.value.hasConflict)
        assertEquals(777, fixture.transactions.getBalanceUpdateRecordById(fixture.recordId)?.actualBalance)

        val conflictRecreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertTrue(conflictRecreated.uiState.value.hasConflict)
        conflictRecreated.reload()
        advanceUntilIdle()
        assertFalse(conflictRecreated.uiState.value.hasConflict)
    }

    @Test
    fun `stale balance delete keeps record and enters conflict`() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel(SavedStateHandle())
        advanceUntilIdle()
        val stored = requireNotNull(fixture.transactions.getBalanceUpdateRecordById(fixture.recordId))
        assertTrue(
            fixture.transactions.updateBalanceUpdateRecord(
                stored.copy(actualBalance = 666, updatedAt = stored.updatedAt + 1),
                expectedUpdatedAt = stored.updatedAt,
            ),
        )

        viewModel.effectFlow.test {
            viewModel.delete()
            advanceUntilIdle()
            assertTrue(awaitItem() is EditBalanceUpdateEffect.ShowMessage)
        }
        assertTrue(viewModel.uiState.value.hasConflict)
        assertEquals(666, fixture.transactions.getBalanceUpdateRecordById(fixture.recordId)?.actualBalance)
    }

    @Test
    fun `balance actual error survives recreation`() = runTest(dispatcher) {
        val fixture = fixture()
        val handle = SavedStateHandle()
        val first = fixture.viewModel(handle)
        advanceUntilIdle()
        first.updateActualBalance("")
        first.save()
        advanceUntilIdle()

        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals("金额不能为空", recreated.uiState.value.actualBalanceError)
    }

    @Test
    fun `balance delete is single flight`() = runTest(dispatcher) {
        val fixture = fixture()
        val handle = SavedStateHandle()
        val viewModel = fixture.viewModel(handle)
        advanceUntilIdle()

        viewModel.delete()
        assertTrue(viewModel.uiState.value.isSaving)
        viewModel.delete()
        advanceUntilIdle()
        val terminal = requireNotNull(viewModel.uiState.value.pendingTerminal)
        assertEquals(FormTerminalKind.DELETED, terminal.kind)
        assertEquals(com.shihuaidexianyu.money.domain.model.LedgerRecordKind.BALANCE_UPDATE, terminal.ledgerUndoToken?.kind)
        assertEquals(null, fixture.transactions.getBalanceUpdateRecordById(fixture.recordId))
        val recreated = fixture.viewModel(handle)
        advanceUntilIdle()
        assertEquals(terminal, recreated.uiState.value.pendingTerminal)
        recreated.ackTerminal(terminal.token)
        assertEquals(null, recreated.uiState.value.pendingTerminal)
    }

    @Test
    fun `preview failure becomes time field error and clears stale delta`() = runTest(dispatcher) {
        val fixture = fixture()
        val accounts = FailingLookupAccountRepository(fixture.accounts)
        val viewModel = fixture.viewModel(accountRepository = accounts)
        advanceUntilIdle()
        val oldDelta = viewModel.uiState.value.deltaPreview
        assertEquals(-10L, oldDelta)

        accounts.failLookup = true
        viewModel.updateOccurredAt(120_000)
        advanceUntilIdle()

        assertEquals("预览失败", viewModel.uiState.value.occurredAtError)
        assertEquals(null, viewModel.uiState.value.deltaPreview)
        assertEquals("0.90", viewModel.uiState.value.actualBalanceText)
    }

    @Test
    fun `initial balance update load exception is retryable and preserves saved draft`() = runTest(dispatcher) {
        val fixture = fixture()
        val repository = ToggleBalanceLoadRepository(fixture.transactions)
        val viewModel = fixture.viewModel(handle = SavedStateHandle(), transactions = repository)
        advanceUntilIdle()
        assertEquals("核对记录加载失败，请重试", viewModel.uiState.value.loadErrorMessage)
        viewModel.updateActualBalance("8.88")

        repository.available = true
        viewModel.retryLoad(); advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.loadErrorMessage)
        assertEquals("8.88", viewModel.uiState.value.actualBalanceText)
    }

    @Test
    fun `concurrent delete before save becomes deleted terminal`() = runTest(dispatcher) {
        val fixture = fixture()
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        viewModel.updateActualBalance("1.23")
        val stored = requireNotNull(fixture.transactions.getBalanceUpdateRecordById(fixture.recordId))
        val refresh = RefreshAccountActivityStateUseCase(fixture.accounts, fixture.transactions)
        val delete = DeleteBalanceUpdateRecordUseCase(
            fixture.accounts,
            fixture.transactions,
            refresh,
            testClockProvider(),
        )
        requireNotNull(delete(fixture.recordId, stored.updatedAt))

        viewModel.save()
        advanceUntilIdle()

        assertEquals(FormTerminalKind.DELETED, viewModel.uiState.value.pendingTerminal?.kind)
        assertFalse(viewModel.uiState.value.isSaving)
    }

    private suspend fun fixture(): Fixture {
        val accounts = InMemoryAccountRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 100, createdAt = 1))
        val transactions = InMemoryTransactionRepository()
        val recordId = transactions.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 90,
                systemBalanceBeforeUpdate = 100,
                delta = -10,
                occurredAt = 60_000,
                createdAt = 60_000,
                updatedAt = 60_000,
                operationId = testOperationId(),
            ),
        ).recordId
        return Fixture(accounts, transactions, recordId)
    }

    private data class Fixture(
        val accounts: InMemoryAccountRepository,
        val transactions: InMemoryTransactionRepository,
        val recordId: Long,
    ) {
        fun viewModel(
            handle: SavedStateHandle = SavedStateHandle(),
            accountRepository: AccountRepository = accounts,
            transactions: TransactionRepository = this.transactions,
        ): EditBalanceUpdateViewModel {
            val refresh = RefreshAccountActivityStateUseCase(accountRepository, transactions)
            val resolve = ResolveBalanceUpdateContextUseCase(accountRepository, transactions)
            return EditBalanceUpdateViewModel(
                recordId = recordId,
                accountRepository = accountRepository,
                transactionRepository = transactions,
                resolveBalanceUpdateContextUseCase = resolve,
                updateBalanceUpdateRecordUseCase = UpdateBalanceUpdateRecordUseCase(
                    accounts,
                    transactions,
                    resolve,
                    refresh,
                    testClockProvider(),
                ),
                deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
                    accounts,
                    transactions,
                    refresh,
                    testClockProvider(),
                ),
                savedStateHandle = handle,
            )
        }
    }

    private class FailingLookupAccountRepository(
        private val delegate: AccountRepository,
    ) : AccountRepository by delegate {
        var failLookup: Boolean = false

        override suspend fun getAccountById(id: Long): Account? {
            if (failLookup) error("预览失败")
            return delegate.getAccountById(id)
        }
    }

    private class ToggleBalanceLoadRepository(
        private val delegate: InMemoryTransactionRepository,
    ) : TransactionRepository by delegate, LedgerAggregateRepository by delegate {
        var available: Boolean = false

        override suspend fun getBalanceUpdateRecordById(id: Long): BalanceUpdateRecord? {
            if (!available) error("database unavailable")
            return delegate.getBalanceUpdateRecordById(id)
        }
    }
}
