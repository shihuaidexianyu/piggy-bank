package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceEffect
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceViewModel
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
    fun `save with no account emits ShowMessage`() = runTest(dispatcher) {
        val vm = buildViewModel(accountRepo = InMemoryAccountRepository())
        advanceUntilIdle()
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is UpdateBalanceEffect.ShowMessage)
            assertEquals("请选择账户", effect.message)
        }
    }

    @Test
    fun `save with blank actual balance emits ShowMessage`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val vm = buildViewModel(accountRepo = accountRepo)
        advanceUntilIdle()

        vm.updateActualBalance("")
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is UpdateBalanceEffect.ShowMessage)
        }
    }

    @Test
    fun `save with matching actual balance emits Saved with zero delta`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val vm = buildViewModel(accountRepo, txnRepo)
        advanceUntilIdle()

        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertEquals(UpdateBalanceEffect.Saved, effect)
        }
        val updates = txnRepo.queryAllBalanceUpdateRecords()
        assertEquals(1, updates.size)
        assertEquals(0L, updates[0].delta)
    }

    private fun buildViewModel(
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository(),
        txnRepo: InMemoryTransactionRepository = InMemoryTransactionRepository(),
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
            initialAccountId = null,
            accountRepository = accountRepo,
            calculateCurrentBalanceUseCase = calculateUseCase,
            updateBalanceUseCase = updateBalanceUseCase,
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
        )
    }
}
