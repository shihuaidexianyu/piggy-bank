package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.ledgerAddExact
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest

data class AccountDetailSnapshot(
    val account: Account?,
    val settings: PortableSettings,
    val reminderConfig: BalanceUpdateReminderConfig,
    val currentBalance: Long,
    val openAccountCount: Int,
    val isStale: Boolean,
    val monthInflow: Long = 0L,
    val monthOutflow: Long = 0L,
    /** Signed sum of this month's reconciliation deltas (investment P&L for investment accounts). */
    val monthInvestmentDelta: Long = 0L,
    val recentRecords: List<AccountDetailRecentRecord> = emptyList(),
)

data class AccountDetailRecentRecord(
    val id: Long,
    val title: String,
    val amount: Long,
    val occurredAt: Long,
    val kind: AccountDetailRecordKind,
    /**
     * Book balance of the viewed account immediately before this record ([balanceAfter] minus
     * this record's contribution; equals [balanceAfter] for zero-delta checks). Null when the
     * record was not part of the balance fold (defensive only).
     */
    val balanceBefore: Long? = null,
    /**
     * Book balance of the viewed account immediately after this record, accumulated from
     * [Account.initialBalance] over the account's complete active ledger. Null when the record
     * was not part of the balance fold (defensive only).
     */
    val balanceAfter: Long? = null,
)

enum class AccountDetailRecordKind { CASH_FLOW, TRANSFER, BALANCE_UPDATE, BALANCE_ADJUSTMENT }

/** Join key between a recent-record row and its running balance. */
private data class AccountDetailRecordKey(
    val kind: AccountDetailRecordKind,
    val recordId: Long,
    val occurredAt: Long,
)

private data class AccountDetailBalanceLeg(
    val key: AccountDetailRecordKey,
    val sourceOrder: Int,
    val contribution: Long,
)

/** Before/after book balance around one ledger leg of the viewed account. */
private data class AccountDetailBalancePoint(
    val balanceBefore: Long,
    val balanceAfter: Long,
)

// Mirrors the `sourceOrder` constants in HISTORY_UNION_FRAGMENT (HistoryRecordDao) so rows that
// share an occurredAt accumulate in the same order on every screen.
private const val SOURCE_ORDER_CASH_FLOW = 4
private const val SOURCE_ORDER_TRANSFER = 3
private const val SOURCE_ORDER_BALANCE_UPDATE = 2
private const val SOURCE_ORDER_BALANCE_ADJUSTMENT = 1

/**
 * Bank-statement running balance for one account: contributions fold in (occurredAt,
 * sourceOrder, recordId) order on top of [initialBalance]. A transfer contributes the
 * viewer-relative leg only (out `-amount`, in `+amount`; a self-transfer — rejected at creation —
 * would net to 0). Zero-delta balance checks contribute 0, so they still report the balance they
 * confirmed. All input lists are the account's complete ACTIVE rows (the DAOs filter
 * `deletedAt IS NULL`), matching the history screen's full-ledger semantics.
 */
private fun computeAccountRunningBalances(
    initialBalance: Long,
    viewerAccountId: Long,
    cashFlows: List<CashFlowRecord>,
    transfers: List<TransferRecord>,
    balanceUpdates: List<BalanceUpdateRecord>,
    adjustments: List<BalanceAdjustmentRecord>,
): Map<AccountDetailRecordKey, AccountDetailBalancePoint> {
    val legs = mutableListOf<AccountDetailBalanceLeg>()
    cashFlows.forEach { record ->
        legs += AccountDetailBalanceLeg(
            key = AccountDetailRecordKey(AccountDetailRecordKind.CASH_FLOW, record.id, record.occurredAt),
            sourceOrder = SOURCE_ORDER_CASH_FLOW,
            contribution = if (record.direction == CashFlowDirection.INFLOW.value) record.amount else -record.amount,
        )
    }
    transfers.forEach { record ->
        val contribution = when {
            record.fromAccountId == viewerAccountId && record.toAccountId == viewerAccountId -> 0L
            record.fromAccountId == viewerAccountId -> -record.amount
            else -> record.amount
        }
        legs += AccountDetailBalanceLeg(
            key = AccountDetailRecordKey(AccountDetailRecordKind.TRANSFER, record.id, record.occurredAt),
            sourceOrder = SOURCE_ORDER_TRANSFER,
            contribution = contribution,
        )
    }
    balanceUpdates.forEach { record ->
        legs += AccountDetailBalanceLeg(
            key = AccountDetailRecordKey(AccountDetailRecordKind.BALANCE_UPDATE, record.id, record.occurredAt),
            sourceOrder = SOURCE_ORDER_BALANCE_UPDATE,
            contribution = record.delta,
        )
    }
    adjustments.forEach { record ->
        legs += AccountDetailBalanceLeg(
            key = AccountDetailRecordKey(AccountDetailRecordKind.BALANCE_ADJUSTMENT, record.id, record.occurredAt),
            sourceOrder = SOURCE_ORDER_BALANCE_ADJUSTMENT,
            contribution = record.delta,
        )
    }
    val balances = HashMap<AccountDetailRecordKey, AccountDetailBalancePoint>(legs.size)
    var running = initialBalance
    legs.sortedWith(
        compareBy(
            { it.key.occurredAt },
            AccountDetailBalanceLeg::sourceOrder,
            { it.key.recordId },
        ),
    ).forEach { leg ->
        val before = running
        running = ledgerAddExact(running, leg.contribution)
        balances[leg.key] = AccountDetailBalancePoint(balanceBefore = before, balanceAfter = running)
    }
    return balances
}

class ObserveAccountDetailUseCase(
    private val accountId: Long,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val portableSettingsRepository: PortableSettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<AccountDetailSnapshot> {
        return combine(
            accountRepository.observeAllAccounts(),
            accountReminderSettingsRepository.observeReminderConfigs(),
            portableSettingsRepository.observe(),
            transactionRepository.observeChangeVersion(),
        ) { accounts, reminderConfigs, settings, _ ->
            Triple(accounts, reminderConfigs, settings)
        }.mapLatest { (accounts, reminderConfigs, settings) ->
            buildSnapshot(
                account = accounts.firstOrNull { it.id == accountId },
                openAccountCount = accounts.count { !it.isClosed },
                settings = settings,
                reminderConfigs = reminderConfigs,
            )
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun buildSnapshot(
        account: Account?,
        openAccountCount: Int,
        settings: PortableSettings,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    ): AccountDetailSnapshot {
        if (account == null) {
            return AccountDetailSnapshot(
                account = null,
                settings = settings,
                reminderConfig = BalanceUpdateReminderConfig(),
                currentBalance = 0L,
                openAccountCount = openAccountCount,
                isStale = false,
            )
        }

        val reminderConfig = reminderConfigs[account.id] ?: BalanceUpdateReminderConfig()
        val snapshotTimeMillis = clockProvider.nowMillis()
        val zoneId = zoneIdProvider.zoneId()

        // This month's inflow/outflow for the account.
        val monthRange = TimeRangeCalculator.currentMonthRange(
            zoneId = zoneId,
            nowMillis = snapshotTimeMillis,
        )
        val inflow = transactionRepository.sumInflowBetween(account.id, monthRange.startInclusive, monthRange.endExclusive)
        val outflow = transactionRepository.sumOutflowBetween(account.id, monthRange.startInclusive, monthRange.endExclusive)

        // Recent 5 ledger events for this account, newest first. Balance checks and manual
        // corrections are the events that change the balance most directly, so they belong in
        // the timeline alongside cash flow and transfers.
        val balanceUpdates = transactionRepository.queryBalanceUpdateRecordsByAccountId(account.id)
        val cashFlows = transactionRepository.queryCashFlowRecordsByAccountId(account.id)
        val transfers = transactionRepository.queryTransferRecordsByAccountId(account.id)
        val adjustments = transactionRepository.queryBalanceAdjustmentRecordsByAccountId(account.id)
        val balancesAfter = computeAccountRunningBalances(
            initialBalance = account.initialBalance,
            viewerAccountId = account.id,
            cashFlows = cashFlows,
            transfers = transfers,
            balanceUpdates = balanceUpdates,
            adjustments = adjustments,
        )
        val recentRecords = (
            cashFlows.take(5).map { it.toRecentRecord() } +
                transfers.take(5).map { it.toRecentRecord(account.id) } +
                balanceUpdates.take(5).map { it.toRecentRecord() } +
                adjustments.take(5).map { it.toRecentRecord() }
            )
            .sortedByDescending { it.occurredAt }
            .take(5)
            .map { record ->
                val balancePoint = balancesAfter[
                    AccountDetailRecordKey(record.kind, record.id, record.occurredAt),
                ]
                record.copy(
                    balanceBefore = balancePoint?.balanceBefore,
                    balanceAfter = balancePoint?.balanceAfter,
                )
            }

        // This month's reconciliation deltas read as investment P&L on investment accounts.
        val monthInvestmentDelta = balanceUpdates
            .filter { it.occurredAt >= monthRange.startInclusive && it.occurredAt < monthRange.endExclusive }
            .sumOf { it.delta }

        return AccountDetailSnapshot(
            account = account,
            settings = settings,
            reminderConfig = reminderConfig,
            currentBalance = calculateCurrentBalanceUseCase(account.id, snapshotTimeMillis),
            openAccountCount = openAccountCount,
            isStale = AccountStatusCalculator.isStale(
                account = account,
                reminderConfig = reminderConfig,
                nowMillis = snapshotTimeMillis,
                zoneId = zoneId,
            ),
            monthInflow = inflow,
            monthOutflow = outflow,
            monthInvestmentDelta = monthInvestmentDelta,
            recentRecords = recentRecords,
        )
    }
}

private fun CashFlowRecord.toRecentRecord(): AccountDetailRecentRecord {
    val signedAmount = if (direction == CashFlowDirection.INFLOW.value) amount else -amount
    return AccountDetailRecentRecord(
        id = id,
        title = note.ifBlank { "未填写用途" },
        amount = signedAmount,
        occurredAt = occurredAt,
        kind = AccountDetailRecordKind.CASH_FLOW,
    )
}

private fun TransferRecord.toRecentRecord(viewerAccountId: Long): AccountDetailRecentRecord {
    val signedAmount = if (fromAccountId == viewerAccountId) -amount else amount
    return AccountDetailRecentRecord(
        id = id,
        title = note.ifBlank { "账户间转移" },
        amount = signedAmount,
        occurredAt = occurredAt,
        kind = AccountDetailRecordKind.TRANSFER,
    )
}

private fun BalanceUpdateRecord.toRecentRecord(): AccountDetailRecentRecord =
    AccountDetailRecentRecord(
        id = id,
        title = if (delta == 0L) "余额核对" else "对账调整",
        amount = delta,
        occurredAt = occurredAt,
        kind = AccountDetailRecordKind.BALANCE_UPDATE,
    )

private fun BalanceAdjustmentRecord.toRecentRecord(): AccountDetailRecentRecord =
    AccountDetailRecentRecord(
        id = id,
        title = "余额校正",
        amount = delta,
        occurredAt = occurredAt,
        kind = AccountDetailRecordKind.BALANCE_ADJUSTMENT,
    )
