package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.ui.accounts.AccountsViewModel
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsSnapshotQueryCountTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `account page shares one balance aggregate across cards goal and closure issues`() =
        runTest(dispatcher) {
            val accounts = InMemoryAccountRepository()
            val transactions = InMemoryTransactionRepository()
            val goals = InMemorySavingsGoalRepository()
            val openId = accounts.createAccount(
                Account(name = "开放", initialBalance = 100L, createdAt = 1L),
            )
            val closedId = accounts.createAccount(
                Account(name = "旧关闭", initialBalance = 50L, createdAt = 1L),
            )
            accounts.closeAccount(closedId, 2L)
            goals.upsert(120L, 2L)

            val viewModel = AccountsViewModel(
                accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
                accountRepository = accounts,
                portableSettingsRepository = InMemoryPortableSettingsRepository(),
                transactionRepository = transactions,
                savingsGoalRepository = goals,
                calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions) { 10L },
            )
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(openId, state.openAccounts.single().id)
            assertTrue(state.closedAccounts.single().requiresReopenAndSettle)
            assertEquals(150L, state.savingsGoal?.currentAmount)
            assertEquals(1, transactions.transactionInvocationCount)

            viewModel.toggleClosedVisibility()
            runCurrent()
            assertEquals(1, transactions.transactionInvocationCount)
        }
}
