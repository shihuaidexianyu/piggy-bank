package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import kotlin.test.assertEquals
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

/** The history row shows a bank-statement before → after balance pair (both accounts for
 * transfers); the ViewModel must thread them (and the data-layer account names) into the UiModel
 * untouched. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryBalanceProjectionViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `records expose running balances and resolved account names`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val cashId = accounts.createAccount(Account(name = "现金", initialBalance = 1_000L, createdAt = 1L))
        val savingsId = accounts.createAccount(Account(name = "储蓄", initialBalance = 0L, createdAt = 1L))
        val names = mapOf(cashId to "现金", savingsId to "储蓄")
        val initialBalances = mapOf(cashId to 1_000L, savingsId to 0L)
        val ledger = InMemoryTransactionRepository(
            accountNameLookup = names::get,
            accountInitialBalanceLookup = { initialBalances[it] ?: 0L },
        )
        ledger.insertCashFlowRecord(cashFlow(cashId, CashFlowDirection.INFLOW, 500L, "工资", 1L))
        ledger.insertTransferRecord(
            TransferRecord(
                fromAccountId = cashId,
                toAccountId = savingsId,
                amount = 300L,
                note = "",
                occurredAt = 2L,
                createdAt = 2L,
                updatedAt = 2L,
                operationId = testOperationId(),
            ),
        )
        ledger.insertCashFlowRecord(cashFlow(savingsId, CashFlowDirection.OUTFLOW, 50L, "购物", 3L))
        val viewModel = HistoryViewModel(
            accountRepository = accounts,
            transactionRepository = ledger,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
        )
        runCurrent()

        val records = viewModel.uiState.value.records
        assertEquals(3, records.size)
        // Newest first; balance pairs accumulate over the full ledger including both transfer legs.
        assertEquals("购物", records[0].title)
        assertEquals("储蓄", records[0].subtitle)
        assertEquals(300L, records[0].balanceBefore)
        assertEquals(250L, records[0].balanceAfter)
        assertEquals(null, records[0].relatedBalanceBefore)
        assertEquals(null, records[0].relatedBalanceAfter)
        assertEquals("现金 → 储蓄", records[1].subtitle)
        // FROM account: 1_000 + 500 - 300; the receiving account's pair rides the same row.
        assertEquals(1_500L, records[1].balanceBefore)
        assertEquals(1_200L, records[1].balanceAfter)
        assertEquals(0L, records[1].relatedBalanceBefore)
        assertEquals(300L, records[1].relatedBalanceAfter)
        assertEquals("工资", records[2].title)
        assertEquals("现金", records[2].subtitle)
        assertEquals(1_000L, records[2].balanceBefore)
        assertEquals(1_500L, records[2].balanceAfter)
    }

    private fun cashFlow(
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        note: String,
        occurredAt: Long,
    ): CashFlowRecord = CashFlowRecord(
        accountId = accountId,
        direction = direction.value,
        amount = amount,
        note = note,
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = testOperationId(),
    )
}
