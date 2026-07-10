package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TimeMath
import com.shihuaidexianyu.money.domain.model.TransferRecord

internal data class LedgerBalanceDeltas(
    val inflow: Long = 0L,
    val outflow: Long = 0L,
    val transferIn: Long = 0L,
    val transferOut: Long = 0L,
    val manualAdjustment: Long = 0L,
    val reconciliation: Long = 0L,
) {
    val net: Long
        get() = inflow - outflow + transferIn - transferOut + manualAdjustment + reconciliation
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
        return account.initialBalance + deltas.net
    }

    fun balanceBefore(
        account: Account,
        endExclusive: Long,
        deltas: LedgerBalanceDeltas,
    ): Long {
        if (openingAt(account) >= endExclusive) return 0L
        return account.initialBalance + deltas.net
    }

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
        return LedgerBalanceDeltas(
            inflow = cashFlows
                .filter {
                    !it.isDeleted &&
                        it.accountId == account.id &&
                        it.direction == CashFlowDirection.INFLOW.value &&
                        it.occurredAt >= startInclusive &&
                        it.occurredAt < endExclusive
                }
                .sumOf(CashFlowRecord::amount),
            outflow = cashFlows
                .filter {
                    !it.isDeleted &&
                        it.accountId == account.id &&
                        it.direction == CashFlowDirection.OUTFLOW.value &&
                        it.occurredAt >= startInclusive &&
                        it.occurredAt < endExclusive
                }
                .sumOf(CashFlowRecord::amount),
            transferIn = transfers
                .filter {
                    !it.isDeleted &&
                        it.toAccountId == account.id &&
                        it.occurredAt >= startInclusive &&
                        it.occurredAt < endExclusive
                }
                .sumOf(TransferRecord::amount),
            transferOut = transfers
                .filter {
                    !it.isDeleted &&
                        it.fromAccountId == account.id &&
                        it.occurredAt >= startInclusive &&
                        it.occurredAt < endExclusive
                }
                .sumOf(TransferRecord::amount),
            manualAdjustment = adjustments
                .filter {
                    it.accountId == account.id &&
                        it.occurredAt >= startInclusive &&
                        it.occurredAt < endExclusive
                }
                .sumOf(BalanceAdjustmentRecord::delta),
            reconciliation = reconciliationDeltaFromRecordsBefore(
                account = account,
                balanceUpdates = balanceUpdates,
                endExclusive = endExclusive,
                excludingBalanceUpdateId = excludingBalanceUpdateId,
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
        return balanceUpdates
            .filter {
                it.accountId == account.id &&
                    it.id != excludingBalanceUpdateId &&
                    it.occurredAt >= startInclusive &&
                    it.occurredAt < endExclusive
            }
            .sumOf(BalanceUpdateRecord::delta)
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
        return deltasFromRecordsBefore(
            account = account,
            cashFlows = cashFlows,
            transfers = transfers,
            balanceUpdates = balanceUpdates,
            adjustments = adjustments,
            endExclusive = endExclusiveAfter(atTimeMillis),
            excludingBalanceUpdateId = excludingBalanceUpdateId,
        )
    }

    fun reconciliationDeltaFromRecords(
        account: Account,
        balanceUpdates: Iterable<BalanceUpdateRecord>,
        atTimeMillis: Long,
        excludingBalanceUpdateId: Long? = null,
    ): Long {
        if (!isOpenAt(account, atTimeMillis)) return 0L
        return reconciliationDeltaFromRecordsBefore(
            account = account,
            balanceUpdates = balanceUpdates,
            endExclusive = endExclusiveAfter(atTimeMillis),
            excludingBalanceUpdateId = excludingBalanceUpdateId,
        )
    }
}
