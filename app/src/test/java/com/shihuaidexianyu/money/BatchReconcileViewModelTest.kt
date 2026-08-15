package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.ui.balance.BatchReconcileUiState
import com.shihuaidexianyu.money.ui.balance.BatchReconcileViewModel
import com.shihuaidexianyu.money.ui.common.FormTerminalKind
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BatchReconcileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `confirm time is pre-filled from the clock when the draft is fresh`() = runBlocking {
        val now = 4_102_444_800_000L
        val vm = buildViewModel(clockProvider = testClockProvider(now))

        val state = awaitLoaded(vm)

        assertEquals(DateTimeTextFormatter.floorToMinute(now), state.confirmTimeMillis)
    }

    @Test
    fun `updateConfirmTime floors to minute and does not touch selection dirtiness`() = runBlocking {
        val accountRepo = InMemoryAccountRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val vm = buildViewModel(accountRepo = accountRepo)
        val loaded = awaitLoaded(vm)
        assertEquals(false, loaded.isDirty)

        vm.updateConfirmTime(1_000_042L) // 16m 42s → floor to 960_000

        assertEquals(960_000L, vm.uiState.value.confirmTimeMillis)
        assertEquals(false, vm.uiState.value.isDirty)
    }

    @Test
    fun `save writes balance updates at the picked confirm time`() = runBlocking {
        val accountRepo = InMemoryAccountRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 10_000, createdAt = 1L))
        val txnRepo = InMemoryTransactionRepository()
        val vm = buildViewModel(accountRepo = accountRepo, txnRepo = txnRepo)
        val loaded = awaitLoaded(vm)
        assertEquals(1, loaded.accounts.size)
        vm.updateConfirmTime(1_200_000L)

        vm.saveSelected()
        val terminal = withTimeout(5_000L) {
            vm.uiState.first { it.pendingTerminal != null }
        }

        assertEquals(FormTerminalKind.SAVED, terminal.pendingTerminal?.kind)
        val records = txnRepo.queryAllBalanceUpdateRecords()
        assertEquals(1, records.size)
        assertEquals(1_200_000L, records.single().occurredAt)
    }

    // buildItems hops to Dispatchers.Default (a real thread), so advanceUntilIdle alone can
    // return before the first emission lands — await the loaded state with a real timeout.
    private suspend fun awaitLoaded(vm: BatchReconcileViewModel): BatchReconcileUiState =
        withTimeout(5_000L) {
            vm.uiState.first { !it.isLoading || it.loadErrorMessageRes != null }
        }.also { state ->
            assertEquals(null, state.loadErrorMessageRes)
        }

    private fun buildViewModel(
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository(),
        txnRepo: InMemoryTransactionRepository = InMemoryTransactionRepository(),
        clockProvider: ClockProvider = testClockProvider,
    ): BatchReconcileViewModel {
        val refreshUseCase = RefreshAccountActivityStateUseCase(accountRepo, txnRepo)
        val resolveUseCase = ResolveBalanceUpdateContextUseCase(accountRepo, txnRepo)
        return BatchReconcileViewModel(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accountRepo,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            transactionRepository = txnRepo,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(txnRepo, clockProvider),
            updateBalanceUseCase = UpdateBalanceUseCase(
                accountRepo,
                txnRepo,
                resolveUseCase,
                refreshUseCase,
                clockProvider,
            ),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
            clockProvider = clockProvider,
        )
    }
}
