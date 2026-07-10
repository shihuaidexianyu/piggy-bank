package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.SettingsRepository
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
    val settings: AppSettings,
    val reminderConfig: BalanceUpdateReminderConfig,
    val currentBalance: Long,
    val isStale: Boolean,
    val monthInflow: Long = 0L,
    val monthOutflow: Long = 0L,
    val recentRecords: List<AccountDetailRecentRecord> = emptyList(),
)

data class AccountDetailRecentRecord(
    val id: Long,
    val title: String,
    val amount: Long,
    val occurredAt: Long,
    val kind: AccountDetailRecordKind,
)

enum class AccountDetailRecordKind { CASH_FLOW, TRANSFER, BALANCE_UPDATE, BALANCE_ADJUSTMENT }

class ObserveAccountDetailUseCase(
    private val accountId: Long,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val accountRepository: AccountRepository,
    private val settingsRepository: SettingsRepository,
    private val transactionRepository: TransactionRepository,
    private val calculateCurrentBalanceUseCase: CalculateCurrentBalanceUseCase,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(): Flow<AccountDetailSnapshot> {
        return combine(
            accountRepository.observeActiveAccounts(),
            accountRepository.observeArchivedAccounts(),
            accountReminderSettingsRepository.observeReminderConfigs(),
            settingsRepository.observeSettings(),
            transactionRepository.observeChangeVersion(),
        ) { active, archived, reminderConfigs, settings, _ ->
            Quadruple(active, archived, reminderConfigs, settings)
        }.mapLatest { (active, archived, reminderConfigs, settings) ->
            buildSnapshot(
                account = (active + archived).firstOrNull { it.id == accountId },
                settings = settings,
                reminderConfigs = reminderConfigs,
            )
        }.flowOn(Dispatchers.Default)
    }

    private suspend fun buildSnapshot(
        account: Account?,
        settings: AppSettings,
        reminderConfigs: Map<Long, BalanceUpdateReminderConfig>,
    ): AccountDetailSnapshot {
        if (account == null) {
            return AccountDetailSnapshot(
                account = null,
                settings = settings,
                reminderConfig = BalanceUpdateReminderConfig(),
                currentBalance = 0L,
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

        // Recent 5 records (cash flow + transfer) for this account, newest first.
        val recentCashFlows = transactionRepository.queryCashFlowRecordsByAccountId(account.id).take(5)
        val recentTransfers = transactionRepository.queryTransferRecordsByAccountId(account.id).take(5)
        val recentRecords = (recentCashFlows.map { it.toRecentRecord() } + recentTransfers.map { it.toRecentRecord() })
            .sortedByDescending { it.occurredAt }
            .take(5)

        return AccountDetailSnapshot(
            account = account,
            settings = settings,
            reminderConfig = reminderConfig,
            currentBalance = calculateCurrentBalanceUseCase(account.id, snapshotTimeMillis),
            isStale = AccountStatusCalculator.isStale(
                account = account,
                reminderConfig = reminderConfig,
                nowMillis = snapshotTimeMillis,
                zoneId = zoneId,
            ),
            monthInflow = inflow,
            monthOutflow = outflow,
            recentRecords = recentRecords,
        )
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private fun CashFlowRecord.toRecentRecord(): AccountDetailRecentRecord {
    val signedAmount = if (direction == com.shihuaidexianyu.money.domain.model.CashFlowDirection.INFLOW.value) amount else -amount
    return AccountDetailRecentRecord(
        id = id,
        title = purpose.ifBlank { "未填写用途" },
        amount = signedAmount,
        occurredAt = occurredAt,
        kind = AccountDetailRecordKind.CASH_FLOW,
    )
}

private fun TransferRecord.toRecentRecord(): AccountDetailRecentRecord {
    return AccountDetailRecentRecord(
        id = id,
        title = note.ifBlank { "账户间转移" },
        amount = amount,
        occurredAt = occurredAt,
        kind = AccountDetailRecordKind.TRANSFER,
    )
}
