package com.shihuaidexianyu.money

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import app.cash.turbine.test
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.ui.record.EditCashFlowEffect
import com.shihuaidexianyu.money.ui.record.EditCashFlowViewModel
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
class EditCashFlowViewModelTest {
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
    fun `load with missing record emits Deleted once`() = runTest(dispatcher) {
        val vm = buildViewModel(recordId = 999L)
        vm.effectFlow.test {
            advanceUntilIdle()
            assertEquals(EditCashFlowEffect.Deleted, awaitItem())
        }
    }

    @Test
    fun `save with blank amount emits ShowMessage`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                purpose = "早餐",
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )

        val vm = buildViewModel(recordId = recordId, accountRepo = accountRepo, txnRepo = txnRepo)
        advanceUntilIdle()

        vm.updateAmount("")
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            val effect = awaitItem()
            assertTrue(effect is EditCashFlowEffect.ShowMessage)
            assertEquals("金额不能为空", effect.message)
        }
    }

    @Test
    fun `save success emits Saved and updates record`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                purpose = "早餐",
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )

        val vm = buildViewModel(recordId = recordId, accountRepo = accountRepo, txnRepo = txnRepo)
        advanceUntilIdle()

        vm.updateAmount("999")
        vm.updatePurpose("午餐")
        vm.effectFlow.test {
            vm.save()
            advanceUntilIdle()
            assertEquals(EditCashFlowEffect.Saved, awaitItem())
        }
        val record = txnRepo.queryCashFlowRecordById(recordId)
        assertEquals(99_900L, record?.amount)
        assertEquals("午餐", record?.purpose)
    }

    @Test
    fun `delete emits Deleted and soft-deletes record`() = runTest(dispatcher) {
        val accountRepo = InMemoryAccountRepository()
        val txnRepo = InMemoryTransactionRepository()
        accountRepo.createAccount(Account(name = "现金", initialBalance = 0, createdAt = 1L))
        val recordId = txnRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 500L,
                purpose = "早餐",
                occurredAt = 1_000L,
                createdAt = 1_000L,
                updatedAt = 1_000L,
            ),
        )

        val vm = buildViewModel(recordId = recordId, accountRepo = accountRepo, txnRepo = txnRepo)
        advanceUntilIdle()

        vm.effectFlow.test {
            vm.delete()
            advanceUntilIdle()
            assertEquals(EditCashFlowEffect.Deleted, awaitItem())
        }
        assertEquals(null, txnRepo.queryCashFlowRecordById(recordId))
    }

    private fun buildViewModel(
        recordId: Long,
        accountRepo: InMemoryAccountRepository = InMemoryAccountRepository(),
        txnRepo: InMemoryTransactionRepository = InMemoryTransactionRepository(),
    ): EditCashFlowViewModel {
        val refreshUseCase = RefreshAccountActivityStateUseCase(accountRepo, txnRepo)
        val calculateUseCase = CalculateCurrentBalanceUseCase(accountRepo, txnRepo)
        val updateUseCase = UpdateCashFlowRecordUseCase(accountRepo, txnRepo, refreshUseCase)
        val deleteUseCase = DeleteCashFlowRecordUseCase(accountRepo, txnRepo, refreshUseCase)
        return EditCashFlowViewModel(
            recordId = recordId,
            accountRepository = accountRepo,
            transactionRepository = txnRepo,
            calculateCurrentBalanceUseCase = calculateUseCase,
            updateCashFlowRecordUseCase = updateUseCase,
            deleteCashFlowRecordUseCase = deleteUseCase,
        )
    }
}
