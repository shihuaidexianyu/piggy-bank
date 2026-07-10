package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemorySettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderWeekday
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ObserveHomeDashboardUseCase
import com.shihuaidexianyu.money.util.TimeRangeUtils
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class ObserveHomeDashboardUseCaseTest {
    @Test
    fun `home stale status uses injected clock and zone`() = runBlocking {
        val now = Instant.parse("2026-04-10T14:30:00Z").toEpochMilli()
        val lastUpdatedAt = Instant.parse("2026-04-06T10:00:00Z").toEpochMilli()
        val accountRepository = InMemoryAccountRepository()
        val reminderSettingsRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "时区边界账户",
                initialBalance = 10_000,
                createdAt = lastUpdatedAt,
                lastBalanceUpdateAt = lastUpdatedAt,
            ),
        )
        reminderSettingsRepository.updateReminderConfig(
            accountId,
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.FRIDAY,
                hour = 22,
                minute = 0,
            ),
        )
        val clockProvider = testClockProvider(now)
        val single = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository, clockProvider)
        val batch = CalculateAccountBalancesUseCase(transactionRepository, clockProvider)
        val useCase = ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = reminderSettingsRepository,
            accountRepository = accountRepository,
            recurringReminderRepository = InMemoryRecurringReminderRepository(
                tickerFlow = MutableStateFlow(now).asStateFlow(),
            ),
            settingsRepository = InMemorySettingsRepository(AppSettings(homePeriod = HomePeriod.WEEK)),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = single,
            calculateAccountBalancesUseCase = batch,
            clockProvider = clockProvider,
            zoneIdProvider = testZoneIdProvider(ZoneOffset.UTC),
        )

        val snapshot = useCase().first()

        assertEquals(0, snapshot.staleAccountCount)
        assertEquals(emptyList(), snapshot.staleAccounts)
    }

    @Test
    fun `home dashboard keeps cash flow and reconciliation amounts separate`() = runBlocking {
        val now = System.currentTimeMillis()
        val range = TimeRangeUtils.currentWeekRange(nowMillis = now)
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val accountId = accountRepository.createAccount(
            Account(
                name = "主账户",
                initialBalance = 10_000,
                createdAt = range.startInclusive - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 2_000,
                note = "工资",
                occurredAt = range.startInclusive + 1_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "outflow",
                amount = 500,
                note = "午饭",
                occurredAt = range.startInclusive + 2_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 11_800,
                systemBalanceBeforeUpdate = 11_500,
                delta = 300,
                occurredAt = range.startInclusive + 3_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 11_100,
                systemBalanceBeforeUpdate = 11_800,
                delta = -700,
                occurredAt = range.startInclusive + 4_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )

        val useCase = ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accountRepository,
            recurringReminderRepository = InMemoryRecurringReminderRepository(
                tickerFlow = MutableStateFlow(now).asStateFlow(),
            ),
            settingsRepository = InMemorySettingsRepository(AppSettings(homePeriod = HomePeriod.WEEK)),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactionRepository),
            clockProvider = testClockProvider(now),
            zoneIdProvider = testZoneIdProvider(),
        )

        val snapshot = useCase().first()

        assertEquals(2_000L, snapshot.periodBreakdown.cashInflow)
        assertEquals(500L, snapshot.periodBreakdown.cashOutflow)
        assertEquals(300L, snapshot.periodBreakdown.reconciliationIncrease)
        assertEquals(700L, snapshot.periodBreakdown.reconciliationDecrease)
    }

    @Test
    fun `home dashboard includes start excludes end and ignores reconciliation and deleted records`() = runBlocking {
        val now = System.currentTimeMillis()
        val range = TimeRangeUtils.currentWeekRange(nowMillis = now)
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val fromAccountId = accountRepository.createAccount(
            Account(
                name = "主账户",
                initialBalance = 10_000,
                createdAt = range.startInclusive - 60_000,
            ),
        )
        val toAccountId = accountRepository.createAccount(
            Account(
                name = "备用账户",
                initialBalance = 5_000,
                createdAt = range.startInclusive - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = fromAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100,
                note = "周期起点",
                occurredAt = range.startInclusive,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = fromAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 200,
                note = "入账",
                occurredAt = range.startInclusive + 1_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = fromAccountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 300,
                note = "下一周期起点",
                occurredAt = range.endExclusive,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        val deletedCashFlowId = transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = fromAccountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 400,
                note = "已删除",
                occurredAt = range.startInclusive + 2_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        ).recordId
        transactionRepository.softDeleteCurrentCashFlowRecord(deletedCashFlowId, now)
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = 500,
                note = "转账",
                occurredAt = range.startInclusive + 3_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        val deletedTransferId = transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = toAccountId,
                toAccountId = fromAccountId,
                amount = 600,
                note = "已删除转账",
                occurredAt = range.startInclusive + 4_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        ).recordId
        transactionRepository.softDeleteCurrentTransferRecord(deletedTransferId, now)
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = fromAccountId,
                delta = 700,
                occurredAt = range.startInclusive + 5_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = fromAccountId,
                actualBalance = 10_800,
                systemBalanceBeforeUpdate = 10_100,
                delta = 700,
                occurredAt = range.startInclusive + 6_000,
                createdAt = now,
                updatedAt = now,
                operationId = testOperationId(),
            ),
        )

        val useCase = ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
            accountRepository = accountRepository,
            recurringReminderRepository = InMemoryRecurringReminderRepository(
                tickerFlow = MutableStateFlow(now).asStateFlow(),
            ),
            settingsRepository = InMemorySettingsRepository(AppSettings(homePeriod = HomePeriod.WEEK)),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactionRepository),
            clockProvider = testClockProvider(now),
            zoneIdProvider = testZoneIdProvider(),
        )

        val snapshot = useCase().first()

        assertEquals(300L, snapshot.periodBreakdown.cashInflow)
        assertEquals(0L, snapshot.periodBreakdown.cashOutflow)
        assertEquals(4, snapshot.periodRecordCount)
    }

    @Test
    fun `home dashboard exposes stale active accounts only`() = runBlocking {
        val now = System.currentTimeMillis()
        val accountRepository = InMemoryAccountRepository()
        val reminderSettingsRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val recurringReminderRepository = InMemoryRecurringReminderRepository(
            tickerFlow = MutableStateFlow(now).asStateFlow(),
        )
        val oldTime = 1_000L
        val staleBankId = accountRepository.createAccount(
            Account(
                name = "银行卡",
                initialBalance = 10_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = oldTime,
            ),
        )
        val stalePaymentId = accountRepository.createAccount(
            Account(
                name = "零钱",
                initialBalance = 2_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = oldTime,
            ),
        )
        accountRepository.setHidden(stalePaymentId, hidden = true)
        val freshId = accountRepository.createAccount(
            Account(
                name = "新账户",
                initialBalance = 3_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = now,
            ),
        )
        val closedId = accountRepository.createAccount(
            Account(
                name = "关闭账户",
                initialBalance = 4_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = oldTime,
            ),
        )
        accountRepository.closeAccount(closedId, now)
        reminderSettingsRepository.updateReminderConfig(
            freshId,
            BalanceUpdateReminderConfig(
                weekday = BalanceUpdateReminderWeekday.SUNDAY,
                hour = 23,
                minute = 59,
            ),
        )

        val useCase = ObserveHomeDashboardUseCase(
            accountReminderSettingsRepository = reminderSettingsRepository,
            accountRepository = accountRepository,
            recurringReminderRepository = recurringReminderRepository,
            settingsRepository = InMemorySettingsRepository(AppSettings(homePeriod = HomePeriod.WEEK)),
            transactionRepository = transactionRepository,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository),
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactionRepository),
            clockProvider = testClockProvider(now),
            zoneIdProvider = testZoneIdProvider(),
        )

        val snapshot = useCase().first()

        assertEquals(2, snapshot.staleAccountCount)
        assertEquals(setOf(staleBankId, stalePaymentId), snapshot.staleAccounts.map { it.id }.toSet())
        assertEquals(10_000, snapshot.accountBalances[staleBankId])
        assertEquals(2_000, snapshot.accountBalances[stalePaymentId])
        assertEquals(4_000, snapshot.accountBalances[closedId])
        assertEquals(19_000, snapshot.totalAssets)
    }
}
