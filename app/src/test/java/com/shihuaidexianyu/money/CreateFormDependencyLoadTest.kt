package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.balance.UpdateBalanceViewModel
import com.shihuaidexianyu.money.ui.record.RecordCashFlowViewModel
import com.shihuaidexianyu.money.ui.record.RecordTransferViewModel
import com.shihuaidexianyu.money.ui.reminder.CreateReminderViewModel
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNull
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
class CreateFormDependencyLoadTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `cash form keeps draft while retrying account load`() = runTest(dispatcher) {
        val fixture = fixture()
        val refresh = RefreshAccountActivityStateUseCase(fixture.accounts, fixture.transactions)
        val viewModel = RecordCashFlowViewModel(
            direction = CashFlowDirection.OUTFLOW,
            initialAccountId = null,
            accountRepository = fixture.accounts,
            transactionRepository = fixture.transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(fixture.transactions),
            createCashFlowRecordUseCase = CreateCashFlowRecordUseCase(fixture.accounts, fixture.transactions, refresh, testClockProvider),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
        )
        advanceUntilIdle()
        viewModel.updateAmount("12.34")
        assertEquals("开放账户加载失败，请重试", viewModel.uiState.value.loadErrorMessage)

        fixture.accounts.available = true
        viewModel.retryLoad()
        advanceUntilIdle()
        assertEquals("12.34", viewModel.uiState.value.amountText)
        assertNull(viewModel.uiState.value.loadErrorMessage)
    }

    @Test
    fun `transfer form keeps draft while retrying account load`() = runTest(dispatcher) {
        val fixture = fixture()
        val refresh = RefreshAccountActivityStateUseCase(fixture.accounts, fixture.transactions)
        val viewModel = RecordTransferViewModel(
            initialFromAccountId = null,
            accountRepository = fixture.accounts,
            transactionRepository = fixture.transactions,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(fixture.transactions),
            createTransferRecordUseCase = CreateTransferRecordUseCase(fixture.accounts, fixture.transactions, refresh, testClockProvider),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
        )
        advanceUntilIdle()
        viewModel.updateNote("午餐")
        assertEquals("开放账户加载失败，请重试", viewModel.uiState.value.loadErrorMessage)

        fixture.accounts.available = true
        viewModel.retryLoad()
        advanceUntilIdle()
        assertEquals("午餐", viewModel.uiState.value.note)
        assertNull(viewModel.uiState.value.loadErrorMessage)
    }

    @Test
    fun `balance form keeps draft while retrying account load`() = runTest(dispatcher) {
        val fixture = fixture()
        val refresh = RefreshAccountActivityStateUseCase(fixture.accounts, fixture.transactions)
        val resolve = ResolveBalanceUpdateContextUseCase(fixture.accounts, fixture.transactions)
        val viewModel = UpdateBalanceViewModel(
            initialAccountId = null,
            accountRepository = fixture.accounts,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(fixture.accounts, fixture.transactions),
            updateBalanceUseCase = UpdateBalanceUseCase(fixture.accounts, fixture.transactions, resolve, refresh, testClockProvider),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
        )
        advanceUntilIdle()
        viewModel.updateActualBalance("-1.00")
        assertEquals("开放账户加载失败，请重试", viewModel.uiState.value.loadErrorMessage)

        fixture.accounts.available = true
        viewModel.retryLoad()
        advanceUntilIdle()
        assertEquals("-1.00", viewModel.uiState.value.actualBalanceText)
        assertNull(viewModel.uiState.value.loadErrorMessage)
    }

    @Test
    fun `reminder form keeps draft while retrying account load`() = runTest(dispatcher) {
        val fixture = fixture()
        val reminders = InMemoryRecurringReminderRepository()
        val zone = com.shihuaidexianyu.money.domain.time.ZoneIdProvider { ZoneId.of("UTC") }
        val viewModel = CreateReminderViewModel(
            accountRepository = fixture.accounts,
            createReminderUseCase = CreateReminderUseCase(fixture.accounts, reminders, testClockProvider, zone),
            savedStateHandle = SavedStateHandle(),
            clockProvider = testClockProvider,
            zoneIdProvider = zone,
        )
        advanceUntilIdle()
        viewModel.updateName("房租")
        assertEquals("开放账户加载失败，请重试", viewModel.uiState.value.loadErrorMessage)

        fixture.accounts.available = true
        viewModel.retryLoad()
        advanceUntilIdle()
        assertEquals("房租", viewModel.uiState.value.name)
        assertNull(viewModel.uiState.value.loadErrorMessage)
    }

    private suspend fun fixture(): Fixture {
        val delegate = InMemoryAccountRepository()
        delegate.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        delegate.createAccount(Account(name = "银行卡", initialBalance = 0L, createdAt = 1L))
        return Fixture(
            accounts = FailFirstOpenAccountsRepository(delegate),
            transactions = InMemoryTransactionRepository(),
        )
    }
}

private data class Fixture(
    val accounts: FailFirstOpenAccountsRepository,
    val transactions: InMemoryTransactionRepository,
)

private class FailFirstOpenAccountsRepository(
    private val delegate: AccountRepository,
) : AccountRepository by delegate {
    var available: Boolean = false

    override suspend fun queryOpenAccounts(): List<Account> {
        if (!available) error("database unavailable")
        return delegate.queryOpenAccounts()
    }
}
