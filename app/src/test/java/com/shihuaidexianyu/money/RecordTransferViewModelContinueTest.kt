package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.ui.record.RecordTransferViewModel
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
class RecordTransferViewModelContinueTest {
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
    fun `continue recording keeps the transfer page open and resets the amount`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        accounts.createAccount(Account(name = "银行卡", initialBalance = 0L, createdAt = 1L))
        val ledger = InMemoryTransactionRepository()
        val refresh = RefreshAccountActivityStateUseCase(accounts, ledger)
        val vm = RecordTransferViewModel(
            initialFromAccountId = null,
            allowContinueRecording = true,
            accountRepository = accounts,
            transactionRepository = ledger,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(ledger),
            createTransferRecordUseCase = CreateTransferRecordUseCase(
                accounts,
                ledger,
                refresh,
                testClockProvider,
            ),
            savedStateHandle = SavedStateHandle(),
            operationIdFactory = LedgerOperationIdFactory { testOperationId() },
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
        )
        advanceUntilIdle()

        val fromId = vm.uiState.value.fromAccountId ?: error("转出账户未加载")
        val toId = vm.uiState.value.toAccountId ?: error("转入账户未加载")
        assertNotEquals(fromId, toId)
        vm.updateAmount("100")
        vm.updateContinueRecording(true)
        vm.save()
        advanceUntilIdle()

        assertEquals(null, vm.uiState.value.pendingTerminal)
        assertEquals("", vm.uiState.value.amountText)
        assertEquals(fromId, vm.uiState.value.fromAccountId)
        assertEquals(toId, vm.uiState.value.toAccountId)
        assertEquals(false, vm.uiState.value.isDirty)
        assertEquals(false, vm.uiState.value.isSaving)

        // The second save must create a distinct transfer with a fresh operation ID.
        vm.updateAmount("200")
        vm.save()
        advanceUntilIdle()

        val transfers = ledger.queryAllActiveTransferRecords()
        assertEquals(2, transfers.size)
        assertNotEquals(transfers[0].operationId, transfers[1].operationId)
    }
}
