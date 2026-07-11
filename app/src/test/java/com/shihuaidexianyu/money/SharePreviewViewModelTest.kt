package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.usecase.LedgerOperationIdFactory
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.ui.common.AccountOptionUiModel
import com.shihuaidexianyu.money.ui.share.ShareCashFlowPayload
import com.shihuaidexianyu.money.ui.share.SharePreviewAccountLoader
import com.shihuaidexianyu.money.ui.share.SharePreviewSubmitter
import com.shihuaidexianyu.money.ui.share.SharePreviewViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SharePreviewViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `parsed draft is editable and survives recreation with one operation id`() = runTest(dispatcher) {
        val savedState = SavedStateHandle()
        val submitted = mutableListOf<ShareCashFlowPayload>()
        val first = viewModel(savedState) { submitted += it }
        runCurrent()

        assertEquals(CashFlowDirection.OUTFLOW, first.uiState.value.direction)
        assertEquals("1234.56", first.uiState.value.amountText)
        first.updateAmount("88.00")
        first.updateNote("晚餐")

        val recreated = viewModel(savedState) { submitted += it }
        runCurrent()
        assertEquals("88.00", recreated.uiState.value.amountText)
        assertEquals("晚餐", recreated.uiState.value.note)
        recreated.save()
        runCurrent()

        assertEquals(1, submitted.size)
        assertEquals("fixed-operation", submitted.single().operationId)
        assertEquals(8_800L, submitted.single().amount)
    }

    @Test
    fun `double submit stays single flight and ambiguous draft requires editing`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val viewModel = viewModel(SavedStateHandle(), originalText = "支付 ¥12.00，另支付 ¥18.00") {
            calls++
            gate.await()
        }
        runCurrent()

        assertEquals("", viewModel.uiState.value.amountText)
        assertTrue(viewModel.uiState.value.isUncertain)
        viewModel.updateAmount("12.00")
        viewModel.save()
        viewModel.save()
        runCurrent()

        assertEquals(1, calls)
        assertTrue(viewModel.uiState.value.isSaving)
        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.uiState.value.isSaving)
    }

    @Test
    fun `note over limit is preserved with field error and cannot save`() = runTest(dispatcher) {
        var calls = 0
        val viewModel = viewModel(SavedStateHandle()) { calls++ }
        runCurrent()
        val longNote = "备".repeat(201)

        viewModel.updateNote(longNote)
        viewModel.save()
        runCurrent()

        assertEquals(longNote, viewModel.uiState.value.note)
        assertEquals("备注不能超过 200 字", viewModel.uiState.value.fieldError)
        assertEquals(0, calls)
    }

    @Test
    fun `recreate and replay through real create use case leaves one ledger row`() = runTest(dispatcher) {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 0L),
        )
        val clock = ClockProvider { 1_700_000_000_000L }
        val create = CreateCashFlowRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            refreshAccountActivityStateUseCase = RefreshAccountActivityStateUseCase(
                accountRepository,
                transactionRepository,
            ),
            clockProvider = clock,
        )
        val savedState = SavedStateHandle()
        fun createViewModel() = SharePreviewViewModel(
            originalText = "支付 ¥12.00",
            savedStateHandle = savedState,
            accountLoader = SharePreviewAccountLoader {
                listOf(AccountOptionUiModel(id = accountId, name = "现金", balance = 0L))
            },
            submitter = SharePreviewSubmitter { payload ->
                create(
                    accountId = payload.accountId,
                    direction = payload.direction,
                    amount = payload.amount,
                    note = payload.note,
                    occurredAt = payload.occurredAt,
                    operationId = payload.operationId,
                )
            },
            operationIdFactory = LedgerOperationIdFactory { "real-operation" },
            clockProvider = clock,
        )

        createViewModel().also { first ->
            advanceUntilIdle()
            first.save()
            advanceUntilIdle()
        }
        createViewModel().also { recreated ->
            advanceUntilIdle()
            recreated.save()
            advanceUntilIdle()
        }

        assertEquals(1, transactionRepository.queryAllCashFlowRecords().size)
        assertEquals("real-operation", transactionRepository.queryAllCashFlowRecords().single().operationId)
    }

    @Test
    fun `account load failure is retryable and keeps the edited draft`() = runTest(dispatcher) {
        var loads = 0
        val savedState = SavedStateHandle()
        val viewModel = SharePreviewViewModel(
            originalText = "支付 ¥12.00",
            savedStateHandle = savedState,
            accountLoader = SharePreviewAccountLoader {
                loads += 1
                if (loads == 1) error("database unavailable")
                listOf(AccountOptionUiModel(id = 7L, name = "现金"))
            },
            submitter = SharePreviewSubmitter {},
            operationIdFactory = LedgerOperationIdFactory { "fixed-operation" },
            clockProvider = ClockProvider { 1_700_000_000_000L },
        )
        runCurrent()
        viewModel.updateAmount("88.00")

        assertEquals("无法读取开放账户", viewModel.uiState.value.loadErrorMessage)
        assertTrue(viewModel.uiState.value.accounts.isEmpty())
        viewModel.retryLoad()
        runCurrent()

        assertEquals("88.00", viewModel.uiState.value.amountText)
        assertEquals(7L, viewModel.uiState.value.selectedAccountId)
        assertEquals(null, viewModel.uiState.value.loadErrorMessage)
    }

    private fun viewModel(
        savedStateHandle: SavedStateHandle,
        originalText: String = "微信支付支出 ￥１，２３４．５６ 元",
        submit: suspend (ShareCashFlowPayload) -> Unit,
    ) = SharePreviewViewModel(
        originalText = originalText,
        savedStateHandle = savedStateHandle,
        accountLoader = SharePreviewAccountLoader {
            listOf(AccountOptionUiModel(id = 7L, name = "现金", balance = 0L))
        },
        submitter = SharePreviewSubmitter(submit),
        operationIdFactory = LedgerOperationIdFactory { "fixed-operation" },
        clockProvider = ClockProvider { 1_700_000_000_000L },
    )
}
