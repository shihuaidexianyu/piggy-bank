package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.LedgerBalanceCalculator
import kotlin.test.assertEquals
import org.junit.Test

/**
 * Direct unit tests for [LedgerBalanceCalculator]. The calculator is `internal object`, so these
 * tests live in the same module and exercise every branch without going through a use case.
 */
class LedgerBalanceCalculatorTest {
private val account = Account(
    id = 1,
    name = "A",
    initialBalance = 10_000,
    // 960_000L is exactly minute-aligned (16 * 60_000). floorToMinute is a no-op here.
    createdAt = 960_000L,
)

@Test
fun `openingAt floors createdAt to minute boundary`() {
    // 1_000_042L is 16*60_000 + 40_042L into the minute; floorToMinute returns 16*60_000 = 960_000L.
    val weirdAccount = account.copy(createdAt = 1_000_042L)
    assertEquals(960_000L, LedgerBalanceCalculator.openingAt(weirdAccount))
}

@Test
fun `startBeforeOpening is one millisecond before opening`() {
    assertEquals(959_999L, LedgerBalanceCalculator.startBeforeOpening(account))
}

@Test
fun `isOpenAt returns false before opening and true from opening onward`() {
    assertEquals(false, LedgerBalanceCalculator.isOpenAt(account, 959_999L))
    assertEquals(true, LedgerBalanceCalculator.isOpenAt(account, 960_000L))
    assertEquals(true, LedgerBalanceCalculator.isOpenAt(account, Long.MAX_VALUE))
}

@Test
fun `isOpeningInRange returns true when opening falls inside the range`() {
    assertEquals(true, LedgerBalanceCalculator.isOpeningInRange(account, 0L, 2_000_000L))
    assertEquals(true, LedgerBalanceCalculator.isOpeningInRange(account, 960_000L, 960_000L))
    assertEquals(false, LedgerBalanceCalculator.isOpeningInRange(account, 960_001L, 2_000_000L))
    assertEquals(false, LedgerBalanceCalculator.isOpeningInRange(account, 0L, 959_999L))
}

@Test
fun `balanceAt returns 0 before account opening`() {
    val deltas = com.shihuaidexianyu.money.domain.usecase.LedgerBalanceDeltas(
        inflow = 1_000_000L,
        outflow = 0L,
        transferIn = 0L,
        transferOut = 0L,
        manualAdjustment = 0L,
        reconciliation = 0L,
    )
    assertEquals(0L, LedgerBalanceCalculator.balanceAt(account, 959_999L, deltas))
}

@Test
fun `balanceAt applies initialBalance plus deltas at and after opening`() {
    val deltas = com.shihuaidexianyu.money.domain.usecase.LedgerBalanceDeltas(
        inflow = 5_000L,
        outflow = 1_000L,
        transferIn = 200L,
        transferOut = 100L,
        manualAdjustment = 50L,
        reconciliation = -20L,
    )
    // 10_000 + 5_000 - 1_000 + 200 - 100 + 50 - 20 = 14_130
    assertEquals(14_130L, LedgerBalanceCalculator.balanceAt(account, 960_000L, deltas))
    assertEquals(14_130L, LedgerBalanceCalculator.balanceAt(account, 9_999_999L, deltas))
}

@Test
fun `deltasFromRecords returns zero deltas before account opening`() {
    val cashFlows = listOf(cashFlow(occurredAt = 960_000L, amount = 5_000L, direction = CashFlowDirection.INFLOW))
    val deltas = LedgerBalanceCalculator.deltasFromRecords(
        account = account,
        cashFlows = cashFlows,
        transfers = emptyList(),
        balanceUpdates = emptyList(),
        adjustments = emptyList(),
        atTimeMillis = 959_999L,
    )
    assertEquals(0L, deltas.inflow)
    assertEquals(0L, deltas.outflow)
    assertEquals(0L, deltas.net)
}

@Test
fun `deltasFromRecords sums only records within the open-ended window`() {
    val cashFlows = listOf(
        cashFlow(occurredAt = 959_999L, amount = 1_000L, direction = CashFlowDirection.INFLOW), // before opening: excluded
        cashFlow(occurredAt = 960_000L, amount = 5_000L, direction = CashFlowDirection.INFLOW), // at opening: included
        cashFlow(occurredAt = 2_000_000L, amount = 3_000L, direction = CashFlowDirection.OUTFLOW), // after opening but before atTime: included
        cashFlow(occurredAt = 5_000_000L, amount = 999L, direction = CashFlowDirection.INFLOW), // after atTime: excluded
    )
    val deltas = LedgerBalanceCalculator.deltasFromRecords(
        account = account,
        cashFlows = cashFlows,
        transfers = emptyList(),
        balanceUpdates = emptyList(),
        adjustments = emptyList(),
        atTimeMillis = 3_000_000L,
    )
    assertEquals(5_000L, deltas.inflow)
    assertEquals(3_000L, deltas.outflow)
    assertEquals(2_000L, deltas.net)
}

@Test
fun `deltasFromRecords ignores soft-deleted cash flow and transfer records`() {
    val cashFlows = listOf(
        cashFlow(occurredAt = 1_500_000L, amount = 1_000L, direction = CashFlowDirection.INFLOW, isDeleted = true),
    )
    val transfers = listOf(
        TransferRecord(
            id = 1,
            fromAccountId = 1,
            toAccountId = 2,
            amount = 500L,
            note = "",
            occurredAt = 1_500_000L,
            createdAt = 960_000L,
            updatedAt = 960_000L,
            isDeleted = true,
        ),
    )
    val deltas = LedgerBalanceCalculator.deltasFromRecords(
        account = account,
        cashFlows = cashFlows,
        transfers = transfers,
        balanceUpdates = emptyList(),
        adjustments = emptyList(),
        atTimeMillis = 2_000_000L,
    )
    assertEquals(0L, deltas.inflow)
    assertEquals(0L, deltas.transferIn)
    assertEquals(0L, deltas.transferOut)
}

@Test
fun `deltasFromRecords counts transferIn and transferOut separately`() {
    val transfers = listOf(
        TransferRecord(id = 1, fromAccountId = 1, toAccountId = 2, amount = 300L, note = "", occurredAt = 1_500_000L, createdAt = 960_000L, updatedAt = 960_000L),
        TransferRecord(id = 2, fromAccountId = 2, toAccountId = 1, amount = 800L, note = "", occurredAt = 1_600_000L, createdAt = 960_000L, updatedAt = 960_000L),
    )
    val deltas = LedgerBalanceCalculator.deltasFromRecords(
        account = account,
        cashFlows = emptyList(),
        transfers = transfers,
        balanceUpdates = emptyList(),
        adjustments = emptyList(),
        atTimeMillis = 2_000_000L,
    )
    assertEquals(800L, deltas.transferIn)
    assertEquals(300L, deltas.transferOut)
}

@Test
fun `deltasFromRecords sums manual adjustments and reconciliation deltas`() {
    val balanceUpdates = listOf(
        BalanceUpdateRecord(id = 1, accountId = 1, actualBalance = 0, systemBalanceBeforeUpdate = 0, delta = 200L, occurredAt = 1_500_000L, createdAt = 960_000L),
        BalanceUpdateRecord(id = 2, accountId = 1, actualBalance = 0, systemBalanceBeforeUpdate = 0, delta = -50L, occurredAt = 1_600_000L, createdAt = 960_000L),
    )
    val adjustments = listOf(
        BalanceAdjustmentRecord(id = 1, accountId = 1, delta = 33L, occurredAt = 1_700_000L, createdAt = 960_000L),
        BalanceAdjustmentRecord(id = 2, accountId = 1, delta = -10L, occurredAt = 1_800_000L, createdAt = 960_000L),
    )
    val deltas = LedgerBalanceCalculator.deltasFromRecords(
        account = account,
        cashFlows = emptyList(),
        transfers = emptyList(),
        balanceUpdates = balanceUpdates,
        adjustments = adjustments,
        atTimeMillis = 2_000_000L,
    )
    assertEquals(150L, deltas.reconciliation)
    assertEquals(23L, deltas.manualAdjustment)
}

@Test
fun `deltasFromRecords excludes the named balance update when excludingBalanceUpdateId is set`() {
    val balanceUpdates = listOf(
        BalanceUpdateRecord(id = 1, accountId = 1, actualBalance = 0, systemBalanceBeforeUpdate = 0, delta = 200L, occurredAt = 1_500_000L, createdAt = 960_000L),
        BalanceUpdateRecord(id = 2, accountId = 1, actualBalance = 0, systemBalanceBeforeUpdate = 0, delta = -50L, occurredAt = 1_600_000L, createdAt = 960_000L),
    )
    val deltas = LedgerBalanceCalculator.deltasFromRecords(
        account = account,
        cashFlows = emptyList(),
        transfers = emptyList(),
        balanceUpdates = balanceUpdates,
        adjustments = emptyList(),
        atTimeMillis = 2_000_000L,
        excludingBalanceUpdateId = 1,
    )
    assertEquals(-50L, deltas.reconciliation)
}

@Test
fun `reconciliationDeltaFromRecords excludes balance updates outside the window`() {
    val balanceUpdates = listOf(
        BalanceUpdateRecord(id = 1, accountId = 1, actualBalance = 0, systemBalanceBeforeUpdate = 0, delta = 200L, occurredAt = 959_999L, createdAt = 960_000L), // before opening
        BalanceUpdateRecord(id = 2, accountId = 1, actualBalance = 0, systemBalanceBeforeUpdate = 0, delta = -50L, occurredAt = 5_000_000L, createdAt = 960_000L), // after atTime
    )
    val delta = LedgerBalanceCalculator.reconciliationDeltaFromRecords(
        account = account,
        balanceUpdates = balanceUpdates,
        atTimeMillis = 2_000_000L,
    )
    assertEquals(0L, delta)
}

@Test
fun `deltasFromRecords ignores records belonging to other accounts`() {
    val cashFlows = listOf(
        cashFlow(id = 1, accountId = 99, occurredAt = 1_500_000L, amount = 1_000L, direction = CashFlowDirection.INFLOW),
    )
    val deltas = LedgerBalanceCalculator.deltasFromRecords(
        account = account,
        cashFlows = cashFlows,
        transfers = emptyList(),
        balanceUpdates = emptyList(),
        adjustments = emptyList(),
        atTimeMillis = 2_000_000L,
    )
    assertEquals(0L, deltas.inflow)
}

@Test
fun `deltasFromRecords at Long MAX_VALUE still respects account opening`() {
    val cashFlows = listOf(
        cashFlow(occurredAt = 960_000L, amount = 1L, direction = CashFlowDirection.INFLOW),
    )
    val deltas = LedgerBalanceCalculator.deltasFromRecords(
        account = account,
        cashFlows = cashFlows,
        transfers = emptyList(),
        balanceUpdates = emptyList(),
        adjustments = emptyList(),
        atTimeMillis = Long.MAX_VALUE,
    )
    assertEquals(1L, deltas.inflow)
}

private fun cashFlow(
    id: Long = 1L,
    accountId: Long = 1L,
    occurredAt: Long,
    amount: Long,
    direction: CashFlowDirection,
    isDeleted: Boolean = false,
): CashFlowRecord = CashFlowRecord(
    id = id,
    accountId = accountId,
    direction = direction.value,
    amount = amount,
    purpose = "",
    occurredAt = occurredAt,
    createdAt = 960_000L,
    updatedAt = 960_000L,
    isDeleted = isDeleted,
)
}
