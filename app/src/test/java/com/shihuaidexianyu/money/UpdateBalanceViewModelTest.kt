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
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceEffect
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceViewModel
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
class UpdateBalanceViewModelTest {
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
        val vm = buildViewModel(accountRepo = InMemoryAccountRepository())
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()
        assertEquals("请选择账户", vm.uiState.value.accountError)
    }

    @Test
    fun `save with blank actual balance shows amount field error`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val vm = buildViewModel(accountRepo = accountRepo)
        advanceUntilIdle()

        vm.updateActualBalance("")
        vm.save()
        advanceUntilIdle()
        assertEquals("金额不能为空", vm.uiState.value.actualBalanceError)
    }

    @Test
    fun `save with matching actual balance emits Saved with zero delta`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val vm = buildViewModel(accountRepo, txnRepo)
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()
        assertEquals(FormTerminalKind.SAVED, vm.uiState.value.pendingTerminal?.kind)
        val updates = txnRepo.queryAllBalanceUpdateRecords()
        assertEquals(1, updates.size)
        assertEquals(0L, updates[0].delta)
    }

    @Test
    fun `balance picker includes hidden open accounts and excludes closed accounts`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val visibleId = accountRepo.createAccount(
            Account(name = "显示", initialBalance = 100L, createdAt = 1L, displayOrder = 0),
        )
        val hiddenId = accountRepo.createAccount(
            Account(name = "隐藏", initialBalance = 200L, createdAt = 2L, isHidden = true, displayOrder = 1),
        )
        val closedId = accountRepo.createAccount(
            Account(name = "关闭", initialBalance = 0L, createdAt = 3L, displayOrder = 2),
        )
        accountRepo.closeAccount(closedId, 4L)

        val viewModel = buildViewModel(accountRepo = accountRepo)
        advanceUntilIdle()

        assertEquals(listOf(visibleId, hiddenId), viewModel.uiState.value.accounts.map { it.id })
    }

    @Test
    fun `explicitly closed account is normalized to an open fallback`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val openId = accountRepo.createAccount(Account(name = "开放", initialBalance = 10L, createdAt = 1L))
        val closedId = accountRepo.createAccount(Account(name = "关闭", initialBalance = 0L, createdAt = 1L))
        accountRepo.closeAccount(closedId, 2L)

        val viewModel = buildViewModel(accountRepo = accountRepo, initialAccountId = closedId)
        advanceUntilIdle()

        assertEquals(openId, viewModel.uiState.value.selectedAccountId)
        assertTrue(viewModel.uiState.value.accounts.none { it.id == closedId })
    }

    @Test
    fun `returning from supplemental entry re-reads book balance and retains actual draft`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1))
        val viewModel = buildViewModel(accounts, transactions)
        advanceUntilIdle()
        viewModel.updateActualBalance("200.00")
        transactions.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100,
                note = "补记",
                occurredAt = 60_000,
                createdAt = 60_000,
                updatedAt = 60_000,
                operationId = testOperationId(),
            ),
        )

        viewModel.refreshLedgerBalanceAfterSupplementalEntry()
        advanceUntilIdle()

        assertEquals(10_100, viewModel.uiState.value.systemBalanceBeforeUpdate)
        assertEquals("200.00", viewModel.uiState.value.actualBalanceText)
        assertEquals(9_900, viewModel.uiState.value.deltaPreview)
    }

    private fun buildViewModel(
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository(),
        txnRepo: InMemoryTransactionRepository = InMemoryTransactionRepository(),
        initialAccountId: Long? = null,
    ): UpdateBalanceViewModel {
        val refreshUseCase = RefreshAccountActivityStateUseCase(accountRepo, txnRepo)
        val calculateUseCase = CalculateCurrentBalanceUseCase(accountRepo, txnRepo)
        val resolveUseCase = ResolveBalanceUpdateContextUseCase(accountRepo, txnRepo)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepo,
            txnRepo,
            resolveUseCase,
            refreshUseCase,
            testClockProvider,
        )
        return UpdateBalanceViewModel(
            initialAccountId = initialAccountId,
            accountRepository = accountRepo,
            calculateCurrentBalanceUseCase = calculateUseCase,
            updateBalanceUseCase = updateBalanceUseCase,
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
        )
    }
}
