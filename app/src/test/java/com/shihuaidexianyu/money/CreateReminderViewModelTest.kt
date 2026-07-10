package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.ui.reminder.CreateReminderViewModel
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

@OptIn(ExperimentalCoroutinesApi::class)
class CreateReminderViewModelTest {
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
    fun `reminder picker includes hidden open accounts and excludes closed accounts`() = runTest(dispatcher) {
        val accountRepository = InMemoryAccountRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val visibleId = accountRepository.createAccount(
            Account(name = "显示", initialBalance = 100L, createdAt = 1L, displayOrder = 0),
        )
        val hiddenId = accountRepository.createAccount(
            Account(name = "隐藏", initialBalance = 200L, createdAt = 2L, isHidden = true, displayOrder = 1),
        )
        val closedId = accountRepository.createAccount(
            Account(name = "关闭", initialBalance = 0L, createdAt = 3L, displayOrder = 2),
        )
        accountRepository.closeAccount(closedId, 4L)
        val viewModel = CreateReminderViewModel(
            accountRepository = accountRepository,
            createReminderUseCase = CreateReminderUseCase(accountRepository, reminderRepository),
        )

        advanceUntilIdle()

        assertEquals(listOf(visibleId, hiddenId), viewModel.uiState.value.accounts.map { it.id })
        assertEquals(visibleId, viewModel.uiState.value.selectedAccountId)
    }
}
