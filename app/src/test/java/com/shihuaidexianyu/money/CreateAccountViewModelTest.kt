package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.usecase.CreateAccountUseCase
import com.shihuaidexianyu.money.ui.accounts.CreateAccountEffect
import com.shihuaidexianyu.money.ui.accounts.CreateAccountViewModel
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
class CreateAccountViewModelTest {
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
    fun `save with blank amount emits ShowMessage`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.updateName("现金")
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is CreateAccountEffect.ShowMessage)
            assertEquals("金额不能为空", effect.message)
        }
    }

    @Test
    fun `save with empty name emits ShowMessage from use case`() = runTest(dispatcher) {
        val vm = buildViewModel()
        vm.updateAmountText("1000")
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is CreateAccountEffect.ShowMessage)
            assertEquals("账户名称不能为空", effect.message)
        }
    }

    @Test
    fun `save with duplicate name emits ShowMessage`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val firstVm = buildViewModel(accountRepo)
        firstVm.updateName("现金")
        firstVm.updateAmountText("1000")
        firstVm.effectFlow.test {
            firstVm.save()
            advanceUntilIdle()
            awaitItem() // drain Saved
        }

        val secondVm = buildViewModel(accountRepo)
        secondVm.updateName("现金")
        secondVm.updateAmountText("2000")
        secondVm.effectFlow.test {
            secondVm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is CreateAccountEffect.ShowMessage)
            assertEquals("已存在同名账户", effect.message)
        }
    }

    @Test
    fun `save success emits Saved and creates account`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val vm = buildViewModel(accountRepo)
        vm.updateName("微信零钱")
        vm.updateAmountText("1234.56")
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertEquals(CreateAccountEffect.Saved, effect)
        }
        val accounts = accountRepo.queryActiveAccounts()
        assertEquals(1, accounts.size)
        assertEquals("微信零钱", accounts[0].name)
        assertEquals(123_456L, accounts[0].initialBalance)
    }

    @Test
    fun `updateName truncates to MAX_ACCOUNT_NAME_LENGTH`() = runTest(dispatcher) {
        val vm = buildViewModel()
        val longName = "a".repeat(com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH + 5)
        vm.updateName(longName)
        assertEquals(
            com.shihuaidexianyu.money.domain.model.MAX_ACCOUNT_NAME_LENGTH,
            vm.uiState.value.name.length,
        )
    }

    private fun buildViewModel(
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository(),
    ): CreateAccountViewModel {
        val reminderSettingsRepo = InMemoryAccountReminderSettingsRepository()
        val createUseCase = CreateAccountUseCase(accountRepo, reminderSettingsRepo)
        return CreateAccountViewModel(createUseCase)
    }
}
