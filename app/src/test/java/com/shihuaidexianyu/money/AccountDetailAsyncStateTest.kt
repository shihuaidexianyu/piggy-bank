package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveAccountDetailUseCase
import com.shihuaidexianyu.money.ui.accounts.AccountDetailViewModel
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailAsyncStateTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `account detail error is not missing and retry restores real balance`() = runTest(dispatcher) {
        val delegate = InMemoryAccountRepository()
        val accountId = delegate.createAccount(Account(name = "现金", initialBalance = 12_345L, createdAt = 1L))
        val accounts = ToggleObserveAccountRepository(delegate)
        val transactions = InMemoryTransactionRepository()
        val useCase = ObserveAccountDetailUseCase(
            accountId = accountId,
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accounts,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            transactionRepository = transactions,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, transactions),
            clockProvider = testClockProvider(1_700_000_000_000L),
            zoneIdProvider = { ZoneId.of("UTC") },
        )
        val viewModel = AccountDetailViewModel(accountId, useCase)
        viewModel.uiState.first { !it.isLoading }

        assertEquals("账户详情加载失败，请重试", viewModel.uiState.value.loadErrorMessage)
        assertFalse(viewModel.uiState.value.isMissing)

        accounts.available = true
        viewModel.retry()
        viewModel.uiState.first { !it.isLoading }

        assertNull(viewModel.uiState.value.loadErrorMessage)
        assertEquals(12_345L, viewModel.uiState.value.currentBalance)
        assertFalse(viewModel.uiState.value.isMissing)
    }
}

private class ToggleObserveAccountRepository(
    private val delegate: AccountRepository,
) : AccountRepository by delegate {
    var available: Boolean = false

    override fun observeAllAccounts(): Flow<List<Account>> {
        if (!available) return flow { error("database unavailable") }
        return flow { emit(delegate.queryAllAccounts()) }
    }
}
