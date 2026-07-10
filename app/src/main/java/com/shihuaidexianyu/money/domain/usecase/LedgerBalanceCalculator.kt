package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountLedgerAggregate
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TimeMath
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.domain.model.ledgerSubtractExact
import com.shihuaidexianyu.money.domain.model.ledgerSumExact

internal data class LedgerBalanceDeltas(
    val inflow: Long = 0L,
    val outflow: Long = 0L,
    val transferIn: Long = 0L,
    val transferOut: Long = 0L,
    val manualAdjustment: Long = 0L,
    val reconciliation: Long = 0L,
) {
    val net: Long
        get() {
            var value = ledgerSubtractExact(inflow, outflow)
            value = ledgerAddExact(value, transferIn)
            value = ledgerSubtractExact(value, transferOut)
            value = ledgerAddExact(value, manualAdjustment)
            return ledgerAddExact(value, reconciliation)
        }
}

internal object LedgerBalanceCalculator {
    fun openingAt(account: Account): Long {
        return TimeMath.floorToMinute(account.createdAt)
    }

    fun isOpenAt(account: Account, atTimeMillis: Long): Boolean {
        return atTimeMillis >= openingAt(account)
    }

    fun endExclusiveAfter(atTimeMillis: Long): Long {
        return if (atTimeMillis == Long.MAX_VALUE) Long.MAX_VALUE else atTimeMillis + 1L
    }

    fun isOpeningInRange(account: Account, startInclusive: Long, endExclusive: Long): Boolean {
        return openingAt(account) >= startInclusive && openingAt(account) < endExclusive
    }

    fun balanceAt(
        account: Account,
        atTimeMillis: Long,
        deltas: LedgerBalanceDeltas,
    ): Long {
        if (!isOpenAt(account, atTimeMillis)) return 0L
        return ledgerAddExact(account.initialBalance, deltas.net)
    }

    fun balanceAt(
        account: Account,
        atTimeMillis: Long,
        aggregate: AccountLedgerAggregate,
    ): Long = balanceAt(account, atTimeMillis, aggregate.toDeltas())

    fun balanceBefore(
        account: Account,
        endExclusive: Long,
        deltas: LedgerBalanceDeltas,
    ): Long {
        if (openingAt(account) >= endExclusive) return 0L
        return ledgerAddExact(account.initialBalance, deltas.net)
    }

    fun balanceBefore(
        account: Account,
        endExclusive: Long,
        aggregate: AccountLedgerAggregate,
    ): Long = balanceBefore(account, endExclusive, aggregate.toDeltas())

    fun deltasFromRecordsBefore(
        account: Account,
        cashFlows: Iterable<CashFlowRecord>,
        transfers: Iterable<TransferRecord>,
        balanceUpdates: Iterable<BalanceUpdateRecord>,
        adjustments: Iterable<BalanceAdjustmentRecord>,
        endExclusive: Long,
        excludingBalanceUpdateId: Long? = null,
    ): LedgerBalanceDeltas {
        if (openingAt(account) >= endExclusive) return LedgerBalanceDeltas()
        val startInclusive = openingAt(account)
        return deltasFromRecordsWhere(
            account = account,
            cashFlows = cashFlows,
            transfers = transfers,
            balanceUpdates = balanceUpdates,
            adjustments = adjustments,
            excludingBalanceUpdateId = excludingBalanceUpdateId,
            isWithinWindow = { occurredAt ->
                occurredAt >= startInclusive && occurredAt < endExclusive
            },
        )
    }

    private fun deltasFromRecordsWhere(
        account: Account,
        cashFlows: Iterable<CashFlowRecord>,
        transfers: Iterable<TransferRecord>,
        balanceUpdates: Iterable<BalanceUpdateRecord>,
        adjustments: Iterable<BalanceAdjustmentRecord>,
        excludingBalanceUpdateId: Long?,
        isWithinWindow: (Long) -> Boolean,
    ): LedgerBalanceDeltas {
        return LedgerBalanceDeltas(
            inflow = cashFlows
                .filter {
                    it.deletedAt == null &&
                        it.accountId == account.id &&
                        it.direction == CashFlowDirection.INFLOW.value &&
                        isWithinWindow(it.occurredAt)
                }
                .map(CashFlowRecord::amount)
                .ledgerSumExact(),
            outflow = cashFlows
                .filter {
                    it.deletedAt == null &&
                        it.accountId == account.id &&
                        it.direction == CashFlowDirection.OUTFLOW.value &&
                        isWithinWindow(it.occurredAt)
                }
                .map(CashFlowRecord::amount)
                .ledgerSumExact(),
            transferIn = transfers
                .filter {
                    it.deletedAt == null &&
                        it.toAccountId == account.id &&
                        isWithinWindow(it.occurredAt)
                }
                .map(TransferRecord::amount)
                .ledgerSumExact(),
            transferOut = transfers
                .filter {
                    it.deletedAt == null &&
                        it.fromAccountId == account.id &&
                        isWithinWindow(it.occurredAt)
                }
                .map(TransferRecord::amount)
                .ledgerSumExact(),
            manualAdjustment = adjustments
                .filter {
                    it.deletedAt == null &&
                        it.accountId == account.id &&
                        isWithinWindow(it.occurredAt)
                }
                .map(BalanceAdjustmentRecord::delta)
                .ledgerSumExact(),
            reconciliation = reconciliationDeltaFromRecordsWhere(
                account = account,
                balanceUpdates = balanceUpdates,
                excludingBalanceUpdateId = excludingBalanceUpdateId,
                isWithinWindow = isWithinWindow,
            ),
        )
    }

    fun reconciliationDeltaFromRecordsBefore(
        account: Account,
        balanceUpdates: Iterable<BalanceUpdateRecord>,
        endExclusive: Long,
        excludingBalanceUpdateId: Long? = null,
    ): Long {
        if (openingAt(account) >= endExclusive) return 0L
        val startInclusive = openingAt(account)
        return reconciliationDeltaFromRecordsWhere(
            account = account,
            balanceUpdates = balanceUpdates,
            excludingBalanceUpdateId = excludingBalanceUpdateId,
            isWithinWindow = { occurredAt ->
                occurredAt >= startInclusive && occurredAt < endExclusive
            },
        )
    }

    private fun reconciliationDeltaFromRecordsWhere(
        account: Account,
        balanceUpdates: Iterable<BalanceUpdateRecord>,
        excludingBalanceUpdateId: Long?,
        isWithinWindow: (Long) -> Boolean,
    ): Long {
        return balanceUpdates
            .filter {
                it.deletedAt == null &&
                    it.accountId == account.id &&
                    it.id != excludingBalanceUpdateId &&
                    isWithinWindow(it.occurredAt)
            }
            .map(BalanceUpdateRecord::delta)
            .ledgerSumExact()
    }

    fun deltasFromRecords(
        account: Account,
        cashFlows: Iterable<CashFlowRecord>,
        transfers: Iterable<TransferRecord>,
        balanceUpdates: Iterable<BalanceUpdateRecord>,
        adjustments: Iterable<BalanceAdjustmentRecord>,
        atTimeMillis: Long,
        excludingBalanceUpdateId: Long? = null,
    ): LedgerBalanceDeltas {
        if (!isOpenAt(account, atTimeMillis)) return LedgerBalanceDeltas()
        val startInclusive = openingAt(account)
        return deltasFromRecordsWhere(
            account = account,
            cashFlows = cashFlows,
            transfers = transfers,
            balanceUpdates = balanceUpdates,
            adjustments = adjustments,
            excludingBalanceUpdateId = excludingBalanceUpdateId,
            isWithinWindow = { occurredAt ->
                occurredAt >= startInclusive && occurredAt <= atTimeMillis
            },
        )
    }

    fun reconciliationDeltaFromRecords(
        account: Account,
        balanceUpdates: Iterable<BalanceUpdateRecord>,
        atTimeMillis: Long,
        excludingBalanceUpdateId: Long? = null,
    ): Long {
        if (!isOpenAt(account, atTimeMillis)) return 0L
        val startInclusive = openingAt(account)
        return reconciliationDeltaFromRecordsWhere(
            account = account,
            balanceUpdates = balanceUpdates,
            excludingBalanceUpdateId = excludingBalanceUpdateId,
            isWithinWindow = { occurredAt ->
                occurredAt >= startInclusive && occurredAt <= atTimeMillis
            },
        )
    }
}

private fun AccountLedgerAggregate.toDeltas(): LedgerBalanceDeltas {
    return LedgerBalanceDeltas(
        inflow = inflow,
        outflow = outflow,
        transferIn = transferIn,
        transferOut = transferOut,
        manualAdjustment = manualAdjustment,
        reconciliation = reconciliation,
    )
}
