package com.shihuaidexianyu.money.data.debug

import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.model.AccountGroupType
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object DebugSampleDataSeeder {
    suspend fun seedIfNeeded(database: MoneyDatabase) {
        val accountDao = database.accountDao()
        val cashFlowDao = database.cashFlowRecordDao()
        val transferDao = database.transferRecordDao()
        val balanceUpdateDao = database.balanceUpdateRecordDao()
        val reminderDao = database.recurringReminderDao()

        val hasData =
            accountDao.queryActiveAccounts().isNotEmpty() ||
                cashFlowDao.queryAllActive().isNotEmpty() ||
                transferDao.queryAllActive().isNotEmpty() ||
                balanceUpdateDao.queryAllActive().isNotEmpty() ||
                reminderDao.queryAll().isNotEmpty()
        if (hasData) return

        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val now = System.currentTimeMillis()

        database.withTransaction {
            val paymentCreatedAt = millisAt(zoneId, LocalDate.of(2025, 1, 1), 9, 0)
            val bankCreatedAt = millisAt(zoneId, LocalDate.of(2024, 12, 1), 9, 30)
            val savingsCreatedAt = millisAt(zoneId, LocalDate.of(2024, 12, 15), 10, 0)
            val investmentCreatedAt = millisAt(zoneId, LocalDate.of(2022, 1, 10), 10, 30)

            val paymentId = accountDao.insert(
                AccountEntity(
                    name = "微信支付",
                    groupType = AccountGroupType.PAYMENT.value,
                    initialBalance = 28_500,
                    createdAt = paymentCreatedAt,
                    lastUsedAt = millisAt(zoneId, today, 20, 10),
                    displayOrder = 0,
                ),
            )
            val bankId = accountDao.insert(
                AccountEntity(
                    name = "招商银行",
                    groupType = AccountGroupType.BANK.value,
                    initialBalance = 1_250_000,
                    createdAt = bankCreatedAt,
                    lastUsedAt = millisAt(zoneId, today.minusDays(1), 18, 10),
                    displayOrder = 1,
                ),
            )
            val savingsId = accountDao.insert(
                AccountEntity(
                    name = "应急储蓄",
                    groupType = AccountGroupType.BANK.value,
                    initialBalance = 800_000,
                    createdAt = savingsCreatedAt,
                    lastUsedAt = millisAt(zoneId, today.minusDays(3), 19, 40),
                    displayOrder = 2,
                ),
            )
            val investmentId = accountDao.insert(
                AccountEntity(
                    name = "指数基金",
                    groupType = AccountGroupType.INVESTMENT.value,
                    initialBalance = 1_000_000,
                    createdAt = investmentCreatedAt,
                    lastUsedAt = millisAt(zoneId, today.minusDays(4), 15, 0),
                    lastBalanceUpdateAt = millisAt(zoneId, today.minusDays(5), 21, 0),
                    displayOrder = 3,
                ),
            )

            seedHistoricalCashFlows(
                cashFlowDao = cashFlowDao,
                paymentId = paymentId,
                bankId = bankId,
                zoneId = zoneId,
                today = today,
            )

            seedTransfers(
                transferDao = transferDao,
                bankId = bankId,
                savingsId = savingsId,
                investmentId = investmentId,
                zoneId = zoneId,
                today = today,
            )

            seedInvestmentSnapshots(
                balanceUpdateDao = balanceUpdateDao,
                investmentId = investmentId,
                zoneId = zoneId,
            )

            reminderDao.insert(
                RecurringReminderEntity(
                    name = "房租",
                    type = ReminderType.MANUAL.value,
                    accountId = bankId,
                    direction = CashFlowDirection.OUTFLOW.value,
                    amount = 3_200_00,
                    periodType = ReminderPeriodType.MONTHLY.value,
                    periodValue = 1,
                    periodMonth = null,
                    nextDueAt = millisAt(zoneId, today.minusDays(1), 9, 0),
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private suspend fun seedHistoricalCashFlows(
        cashFlowDao: com.shihuaidexianyu.money.data.dao.CashFlowRecordDao,
        paymentId: Long,
        bankId: Long,
        zoneId: ZoneId,
        today: LocalDate,
    ) {
        val payrollDates = listOf(
            LocalDate.of(2022, 5, 12),
            LocalDate.of(2024, 9, 18),
            today.minusMonths(1).withDayOfMonth(5),
            today.withDayOfMonth(5),
        )
        payrollDates.forEachIndexed { index, date ->
            insertCashFlow(
                dao = cashFlowDao,
                accountId = bankId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 12_800_00 + index * 350_00L,
                purpose = "工资",
                occurredAt = millisAt(zoneId, date, 10, 0),
            )
        }

        val paymentFlows = listOf(
            Triple(today.withDayOfMonth(1), CashFlowDirection.OUTFLOW.value, 32_00L to "早餐"),
            Triple(today.withDayOfMonth(2), CashFlowDirection.OUTFLOW.value, 88_00L to "午餐"),
            Triple(today.withDayOfMonth(3), CashFlowDirection.OUTFLOW.value, 49_00L to "打车"),
            Triple(today.withDayOfMonth(4), CashFlowDirection.INFLOW.value, 66_00L to "退款"),
            Triple(today.withDayOfMonth(5), CashFlowDirection.OUTFLOW.value, 158_00L to "聚餐"),
            Triple(today.withDayOfMonth(6), CashFlowDirection.OUTFLOW.value, 25_00L to "咖啡"),
            Triple(today.withDayOfMonth(7), CashFlowDirection.OUTFLOW.value, 1_280_00L to "房租分摊"),
            Triple(today.withDayOfMonth(8), CashFlowDirection.INFLOW.value, 300_00L to "报销"),
            Triple(today.withDayOfMonth(9), CashFlowDirection.OUTFLOW.value, 56_00L to "晚餐"),
        ).filter { it.first.month == today.month && it.first.year == today.year }

        paymentFlows.forEach { (date, direction, pair) ->
            insertCashFlow(
                dao = cashFlowDao,
                accountId = paymentId,
                direction = direction,
                amount = pair.first,
                purpose = pair.second,
                occurredAt = millisAt(zoneId, date, 19, 30),
            )
        }

        val lastMonthBase = today.minusMonths(1)
        val lastMonthFlows = listOf(
            Triple(lastMonthBase.withDayOfMonth(2), CashFlowDirection.OUTFLOW.value, 42_00L to "早餐"),
            Triple(lastMonthBase.withDayOfMonth(6), CashFlowDirection.OUTFLOW.value, 216_00L to "超市"),
            Triple(lastMonthBase.withDayOfMonth(11), CashFlowDirection.OUTFLOW.value, 74_00L to "打车"),
            Triple(lastMonthBase.withDayOfMonth(16), CashFlowDirection.INFLOW.value, 120_00L to "退款"),
            Triple(lastMonthBase.withDayOfMonth(23), CashFlowDirection.OUTFLOW.value, 268_00L to "生活用品"),
            Triple(lastMonthBase.withDayOfMonth(28), CashFlowDirection.OUTFLOW.value, 92_00L to "外卖"),
        ).filter { it.first.month == lastMonthBase.month && it.first.year == lastMonthBase.year }

        lastMonthFlows.forEach { (date, direction, pair) ->
            insertCashFlow(
                dao = cashFlowDao,
                accountId = paymentId,
                direction = direction,
                amount = pair.first,
                purpose = pair.second,
                occurredAt = millisAt(zoneId, date, 20, 0),
            )
        }
    }

    private suspend fun seedTransfers(
        transferDao: com.shihuaidexianyu.money.data.dao.TransferRecordDao,
        bankId: Long,
        savingsId: Long,
        investmentId: Long,
        zoneId: ZoneId,
        today: LocalDate,
    ) {
        insertTransfer(
            dao = transferDao,
            fromAccountId = bankId,
            toAccountId = savingsId,
            amount = 2_000_00,
            note = "月度储蓄",
            occurredAt = millisAt(zoneId, today.withDayOfMonth(3), 8, 30),
        )
        insertTransfer(
            dao = transferDao,
            fromAccountId = bankId,
            toAccountId = investmentId,
            amount = 1_500_00,
            note = "定投",
            occurredAt = millisAt(zoneId, today.withDayOfMonth(4), 8, 45),
        )
        insertTransfer(
            dao = transferDao,
            fromAccountId = bankId,
            toAccountId = investmentId,
            amount = 50_000_00,
            note = "历史建仓",
            occurredAt = millisAt(zoneId, LocalDate.of(2024, 3, 5), 10, 0),
        )
        insertTransfer(
            dao = transferDao,
            fromAccountId = bankId,
            toAccountId = investmentId,
            amount = 10_000_00,
            note = "追加投入",
            occurredAt = millisAt(zoneId, LocalDate.of(2026, 2, 10), 10, 15),
        )
    }

    private suspend fun seedInvestmentSnapshots(
        balanceUpdateDao: com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao,
        investmentId: Long,
        zoneId: ZoneId,
    ) {
        val firstUpdateAt = millisAt(zoneId, LocalDate.of(2025, 12, 31), 21, 0)
        balanceUpdateDao.insert(
            BalanceUpdateRecordEntity(
                accountId = investmentId,
                actualBalance = 1_650_000,
                systemBalanceBeforeUpdate = 1_580_000,
                delta = 70_000,
                occurredAt = firstUpdateAt,
                createdAt = firstUpdateAt,
            ),
        )

        val secondUpdateAt = millisAt(zoneId, LocalDate.of(2026, 4, 5), 21, 0)
        balanceUpdateDao.insert(
            BalanceUpdateRecordEntity(
                accountId = investmentId,
                actualBalance = 1_820_000,
                systemBalanceBeforeUpdate = 1_750_000,
                delta = 70_000,
                occurredAt = secondUpdateAt,
                createdAt = secondUpdateAt,
            ),
        )
    }

    private suspend fun insertCashFlow(
        dao: com.shihuaidexianyu.money.data.dao.CashFlowRecordDao,
        accountId: Long,
        direction: String,
        amount: Long,
        purpose: String,
        occurredAt: Long,
    ) {
        dao.insert(
            CashFlowRecordEntity(
                accountId = accountId,
                direction = direction,
                amount = amount,
                purpose = purpose,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            ),
        )
    }

    private suspend fun insertTransfer(
        dao: com.shihuaidexianyu.money.data.dao.TransferRecordDao,
        fromAccountId: Long,
        toAccountId: Long,
        amount: Long,
        note: String,
        occurredAt: Long,
    ) {
        dao.insert(
            TransferRecordEntity(
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = amount,
                note = note,
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
            ),
        )
    }

    private fun millisAt(
        zoneId: ZoneId,
        date: LocalDate,
        hour: Int,
        minute: Int,
    ): Long {
        return LocalDateTime.of(date, LocalTime.of(hour, minute))
            .atZone(zoneId)
            .toInstant()
            .toEpochMilli()
    }
}
