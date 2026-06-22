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

class ObserveHomeDashboardUseCaseTest {
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
                createdAt = range.startAtMillis - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 2_000,
                purpose = "工资",
                occurredAt = range.startAtMillis + 1_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "outflow",
                amount = 500,
                purpose = "午饭",
                occurredAt = range.startAtMillis + 2_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 11_800,
                systemBalanceBeforeUpdate = 11_500,
                delta = 300,
                occurredAt = range.startAtMillis + 3_000,
                createdAt = now,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 11_100,
                systemBalanceBeforeUpdate = 11_800,
                delta = -700,
                occurredAt = range.startAtMillis + 4_000,
                createdAt = now,
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
        )

        val snapshot = useCase().first()

        assertEquals(2_000L, snapshot.periodBreakdown.cashInflow)
        assertEquals(500L, snapshot.periodBreakdown.cashOutflow)
        assertEquals(300L, snapshot.periodBreakdown.reconciliationIncrease)
        assertEquals(700L, snapshot.periodBreakdown.reconciliationDecrease)
    }

    @Test
    fun `home dashboard counts period records excluding reconciliation deleted and start boundary`() = runBlocking {
        val now = System.currentTimeMillis()
        val range = TimeRangeUtils.currentWeekRange(nowMillis = now)
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val fromAccountId = accountRepository.createAccount(
            Account(
                name = "主账户",
                initialBalance = 10_000,
                createdAt = range.startAtMillis - 60_000,
            ),
        )
        val toAccountId = accountRepository.createAccount(
            Account(
                name = "备用账户",
                initialBalance = 5_000,
                createdAt = range.startAtMillis - 60_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = fromAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100,
                purpose = "边界外",
                occurredAt = range.startAtMillis,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = fromAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 200,
                purpose = "入账",
                occurredAt = range.startAtMillis + 1_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = fromAccountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 300,
                purpose = "出账",
                occurredAt = range.endAtMillis,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val deletedCashFlowId = transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = fromAccountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 400,
                purpose = "已删除",
                occurredAt = range.startAtMillis + 2_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.softDeleteCashFlowRecord(deletedCashFlowId, now)
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = 500,
                note = "转账",
                occurredAt = range.startAtMillis + 3_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val deletedTransferId = transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = toAccountId,
                toAccountId = fromAccountId,
                amount = 600,
                note = "已删除转账",
                occurredAt = range.startAtMillis + 4_000,
                createdAt = now,
                updatedAt = now,
            ),
        )
        transactionRepository.softDeleteTransferRecord(deletedTransferId, now)
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = fromAccountId,
                delta = 700,
                occurredAt = range.startAtMillis + 5_000,
                createdAt = now,
            ),
        )
        transactionRepository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = fromAccountId,
                actualBalance = 10_800,
                systemBalanceBeforeUpdate = 10_100,
                delta = 700,
                occurredAt = range.startAtMillis + 6_000,
                createdAt = now,
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
        )

        val snapshot = useCase().first()

        assertEquals(4, snapshot.periodRecordCount)
    }

    @Test
    fun `home dashboard exposes stale active accounts only`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val reminderSettingsRepository = InMemoryAccountReminderSettingsRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val recurringReminderRepository = InMemoryRecurringReminderRepository(
            tickerFlow = MutableStateFlow(System.currentTimeMillis()).asStateFlow(),
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
        val freshId = accountRepository.createAccount(
            Account(
                name = "新账户",
                initialBalance = 3_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = System.currentTimeMillis(),
            ),
        )
        val archivedId = accountRepository.createAccount(
            Account(
                name = "归档账户",
                initialBalance = 4_000,
                createdAt = oldTime,
                lastBalanceUpdateAt = oldTime,
            ),
        )
        accountRepository.archiveAccount(archivedId, System.currentTimeMillis())
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
        )

        val snapshot = useCase().first()

        assertEquals(2, snapshot.staleAccountCount)
        assertEquals(setOf(staleBankId, stalePaymentId), snapshot.staleAccounts.map { it.id }.toSet())
        assertEquals(10_000, snapshot.accountBalances[staleBankId])
        assertEquals(2_000, snapshot.accountBalances[stalePaymentId])
    }
}
