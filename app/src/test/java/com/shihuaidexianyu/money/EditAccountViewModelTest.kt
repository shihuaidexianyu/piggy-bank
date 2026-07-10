package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.AccountLifecycleCoordinator
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.ui.accounts.EditAccountEffect
import com.shihuaidexianyu.money.ui.accounts.EditAccountViewModel
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
class EditAccountViewModelTest {
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
    fun `load with missing account emits Closed once`() = runTest(dispatcher) {
        val vm = buildViewModel(accountId = 999L)
        vm.effectFlow.test {
            advanceUntilIdle()
            assertEquals(EditAccountEffect.Closed, awaitItem())
        }
    }

    @Test
    fun `save with closed account emits ShowMessage without invoking use case`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val accountId = accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        accountRepo.closeAccount(accountId, closedAt = 100L)
        val vm = buildViewModel(accountId = accountId, accountRepo = accountRepo)
        advanceUntilIdle()

        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is EditAccountEffect.ShowMessage)
            assertEquals("关闭账户不能修改账户", effect.message)
        }
    }

    @Test
    fun `save with empty name emits ShowMessage from use case`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val accountId = accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val vm = buildViewModel(accountId = accountId, accountRepo = accountRepo)
        advanceUntilIdle()

        vm.updateName("   ")
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is EditAccountEffect.ShowMessage)
            assertEquals("账户名称不能为空", effect.message)
        }
    }

    @Test
    fun `save success emits Saved and updates account`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val accountId = accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val vm = buildViewModel(accountId = accountId, accountRepo = accountRepo)
        advanceUntilIdle()

        vm.updateName("微信零钱")
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            assertEquals(EditAccountEffect.Saved, awaitItem())
        }
        val account = accountRepo.getAccountById(accountId)
        assertEquals("微信零钱", account?.name)
    }

    @Test
    fun `close success emits AccountClosed and marks account closed`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val accountId = accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val vm = buildViewModel(accountId = accountId, accountRepo = accountRepo)
        advanceUntilIdle()

        vm.effectFlow.test {
            vm.closeAccount()
            advanceUntilIdle()
            assertEquals(EditAccountEffect.AccountClosed, awaitItem())
        }
        val account = accountRepo.getAccountById(accountId)
        assertTrue(account?.isClosed == true)
    }

    @Test
    fun `close with nonzero balance surfaces failure and keeps account open`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val accountId = accountRepo.createAccount(Account(name = "现金", initialBalance = 100, createdAt = 1L))
        val vm = buildViewModel(accountId = accountId, accountRepo = accountRepo)
        advanceUntilIdle()

        vm.effectFlow.test {
            vm.closeAccount()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is EditAccountEffect.ShowMessage)
            assertEquals("账户余额必须为 0 才能关闭", effect.message)
        }
        assertTrue(accountRepo.getAccountById(accountId)?.isClosed == false)
    }

    private fun buildViewModel(
        accountId: Long,
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository(),
    ): EditAccountViewModel {
        val reminderSettingsRepo = InMemoryAccountReminderSettingsRepository()
        val reminderRepo = InMemoryRecurringReminderRepository()
        val txnRepo = InMemoryTransactionRepository()
        val accountLifecycleCoordinator = AccountLifecycleCoordinator()
        val closeUseCase = CloseAccountUseCase(
            accountRepository = accountRepo,
            reminderRepository = reminderRepo,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(
                accountRepo,
                txnRepo,
                testClockProvider,
            ),
            transactionRunner = txnRepo,
            clockProvider = testClockProvider,
            accountLifecycleCoordinator = accountLifecycleCoordinator,
            accountReminderSettingsRepository = reminderSettingsRepo,
        )
        val updateUseCase = UpdateAccountUseCase(
            accountRepo,
            reminderSettingsRepo,
            txnRepo,
            accountLifecycleCoordinator,
        )
        return EditAccountViewModel(
            accountId = accountId,
            accountRepository = accountRepo,
            accountReminderSettingsRepository = reminderSettingsRepo,
            closeAccountUseCase = closeUseCase,
            updateAccountUseCase = updateUseCase,
        )
    }
}
