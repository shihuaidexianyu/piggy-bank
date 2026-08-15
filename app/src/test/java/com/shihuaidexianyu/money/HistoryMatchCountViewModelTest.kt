package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryMatchCountViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `match count appears with an active filter and clears with the filters`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        val ledger = InMemoryTransactionRepository()
        insertOutflow(ledger, accountId, note = "咖啡", occurredAt = 3L)
        insertOutflow(ledger, accountId, note = "咖啡加奶", occurredAt = 2L)
        insertOutflow(ledger, accountId, note = "工资", occurredAt = 1L)
        val viewModel = viewModel(accounts, ledger)
        runCurrent()

        // No filter: the matched count stays hidden and the loaded count drives the label.
        assertNull(viewModel.uiState.value.filterMatchCount)
        assertEquals(3, viewModel.uiState.value.records.size)

        viewModel.updateKeyword("咖啡")
        advanceTimeBy(300L)
        runCurrent()

        assertEquals(2, viewModel.uiState.value.filterMatchCount)
        assertEquals(2, viewModel.uiState.value.records.size)

        viewModel.clearFilters()
        runCurrent()

        assertNull(viewModel.uiState.value.filterMatchCount)
        assertEquals(3, viewModel.uiState.value.records.size)
    }

    @Test
    fun `match count covers hits beyond the first page`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        val ledger = InMemoryTransactionRepository()
        repeat(120) { index ->
            insertOutflow(ledger, accountId, note = "咖啡$index", occurredAt = index.toLong() + 1L)
        }
        val viewModel = viewModel(accounts, ledger)
        runCurrent()

        viewModel.updateKeyword("咖啡")
        advanceTimeBy(300L)
        runCurrent()

        // The first page holds 100 records; the hit count spans the whole filtered set.
        assertEquals(100, viewModel.uiState.value.records.size)
        assertEquals(120, viewModel.uiState.value.filterMatchCount)
    }

    private fun viewModel(
        accounts: InMemoryAccountRepository,
        ledger: InMemoryTransactionRepository,
    ) = HistoryViewModel(
        accountRepository = accounts,
        transactionRepository = ledger,
        portableSettingsRepository = InMemoryPortableSettingsRepository(),
        devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
    )

    private suspend fun insertOutflow(
        ledger: InMemoryTransactionRepository,
        accountId: Long,
        note: String,
        occurredAt: Long,
    ) {
        ledger.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 100L,
                note = note,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = "match-count-$note-$occurredAt",
            ),
        )
    }
}
