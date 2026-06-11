package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

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
        return DateTimeTextFormatter.floorToMinute(account.createdAt)
    }

    fun startBeforeOpening(account: Account): Long {
        return (openingAt(account) - 1L).coerceAtLeast(-1L)
    }

    fun isOpenAt(account: Account, atTimeMillis: Long): Boolean {
        return atTimeMillis >= openingAt(account)
    }

    fun isOpeningInRange(account: Account, startAt: Long, endAt: Long): Boolean {
        return openingAt(account) in startAt..endAt
    }

    fun balanceAt(
        account: Account,
        atTimeMillis: Long,
        deltas: LedgerBalanceDeltas,
    ): Long {
        if (!isOpenAt(account, atTimeMillis)) return 0L
        return account.initialBalance + deltas.net
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
        val startAt = startBeforeOpening(account)
        return LedgerBalanceDeltas(
            inflow = cashFlows
                .filter {
                    !it.isDeleted &&
                        it.accountId == account.id &&
                        it.direction == CashFlowDirection.INFLOW.value &&
                        it.occurredAt > startAt &&
                        it.occurredAt <= atTimeMillis
                }
                .sumOf(CashFlowRecord::amount),
            outflow = cashFlows
                .filter {
                    !it.isDeleted &&
                        it.accountId == account.id &&
                        it.direction == CashFlowDirection.OUTFLOW.value &&
                        it.occurredAt > startAt &&
                        it.occurredAt <= atTimeMillis
                }
                .sumOf(CashFlowRecord::amount),
            transferIn = transfers
                .filter {
                    !it.isDeleted &&
                        it.toAccountId == account.id &&
                        it.occurredAt > startAt &&
                        it.occurredAt <= atTimeMillis
                }
                .sumOf(TransferRecord::amount),
            transferOut = transfers
                .filter {
                    !it.isDeleted &&
                        it.fromAccountId == account.id &&
                        it.occurredAt > startAt &&
                        it.occurredAt <= atTimeMillis
                }
                .sumOf(TransferRecord::amount),
            manualAdjustment = adjustments
                .filter {
                    it.accountId == account.id &&
                        it.occurredAt > startAt &&
                        it.occurredAt <= atTimeMillis
                }
                .sumOf(BalanceAdjustmentRecord::delta),
            reconciliation = reconciliationDeltaFromRecords(
                account = account,
                balanceUpdates = balanceUpdates,
                atTimeMillis = atTimeMillis,
                excludingBalanceUpdateId = excludingBalanceUpdateId,
            ),
        )
    }

    fun reconciliationDeltaFromRecords(
        account: Account,
        balanceUpdates: Iterable<BalanceUpdateRecord>,
        atTimeMillis: Long,
        excludingBalanceUpdateId: Long? = null,
    ): Long {
        if (!isOpenAt(account, atTimeMillis)) return 0L
        val startAt = startBeforeOpening(account)
        return balanceUpdates
            .filter {
                it.accountId == account.id &&
                    it.id != excludingBalanceUpdateId &&
                    it.occurredAt > startAt &&
                    it.occurredAt <= atTimeMillis
            }
            .sumOf(BalanceUpdateRecord::delta)
    }
}
