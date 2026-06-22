package com.shihuaidexianyu.money.data.debug

import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.dao.BalanceAdjustmentRecordDao
import com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao
import com.shihuaidexianyu.money.data.dao.CashFlowRecordDao
import com.shihuaidexianyu.money.data.dao.RecurringReminderDao
import com.shihuaidexianyu.money.data.dao.TransferRecordDao
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.random.Random

object DebugSampleDataSeeder {
    /**
     * Seeds sample data only when the DB is empty.
     *
     * Self-guarding: refuses to run if the calling app is not debuggable (FLAG_DEBUGGABLE).
     * This protects against a future caller accidentally invoking [seedIfNeeded] from a release
     * build — even if [com.shihuaidexianyu.money.di.DataGraph.seedDebugSampleDataIfNeeded] is
     * bypassed, this assertion still catches it.
     */
    suspend fun seedIfNeeded(context: android.content.Context, database: MoneyDatabase) {
        // Self-guarding: refuses to run if the calling app is not debuggable (FLAG_DEBUGGABLE).
        // This protects against a future caller accidentally invoking [seedIfNeeded] from a release
        // build — even if [com.shihuaidexianyu.money.di.DataGraph.seedDebugSampleDataIfNeeded] is
        // bypassed, this assertion still catches it.
        val isDebuggable = (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        check(isDebuggable) {
            "DebugSampleDataSeeder.seedIfNeeded must not be called from a non-debuggable build."
        }
        val accountDao = database.accountDao()
        val cashFlowDao = database.cashFlowRecordDao()
        val transferDao = database.transferRecordDao()
        val balanceUpdateDao = database.balanceUpdateRecordDao()
        val balanceAdjustmentDao = database.balanceAdjustmentRecordDao()
        val reminderDao = database.recurringReminderDao()

        val hasData =
            accountDao.queryActiveAccounts().isNotEmpty() ||
                cashFlowDao.queryAllActive().isNotEmpty() ||
                transferDao.queryAllActive().isNotEmpty() ||
                balanceUpdateDao.queryAllActive().isNotEmpty() ||
                balanceAdjustmentDao.queryAllActive().isNotEmpty() ||
                reminderDao.queryAll().isNotEmpty()
        if (hasData) return

        val random = Random(System.currentTimeMillis())
        val zoneId = ZoneId.systemDefault()
        val today = LocalDate.now(zoneId)
        val now = System.currentTimeMillis()

        database.withTransaction {
            val accounts = createRandomAccounts(
                accountDao = accountDao,
                random = random,
                zoneId = zoneId,
                today = today,
            )

            seedRandomCashFlows(
                cashFlowDao = cashFlowDao,
                accountIds = accounts.values.toList(),
                incomeAccountId = accounts.getValue("salary"),
                random = random,
                zoneId = zoneId,
                today = today,
            )

            seedRandomTransfers(
                transferDao = transferDao,
                accounts = accounts,
                random = random,
                zoneId = zoneId,
                today = today,
            )

            seedRandomBalanceEvents(
                balanceUpdateDao = balanceUpdateDao,
                balanceAdjustmentDao = balanceAdjustmentDao,
                accounts = accounts,
                random = random,
                zoneId = zoneId,
                today = today,
            )

            seedRandomReminders(
                reminderDao = reminderDao,
                accountIds = accounts.values.toList(),
                random = random,
                zoneId = zoneId,
                today = today,
                now = now,
            )
        }
    }

    private suspend fun createRandomAccounts(
        accountDao: com.shihuaidexianyu.money.data.dao.AccountDao,
        random: Random,
        zoneId: ZoneId,
        today: LocalDate,
    ): Map<String, Long> {
        val names = listOf("微信零钱", "招商银行", "应急储蓄", "指数基金", "旅行预算").shuffled(random)
        val colors = listOf("green", "blue", "teal", "purple", "orange")
        val keys = listOf("wallet", "salary", "savings", "investment", "travel")
        return keys.mapIndexed { index, key ->
            val createdAt = millisAt(
                zoneId = zoneId,
                date = today.minusDays(random.nextLong(190, 780)),
                hour = random.nextInt(8, 22),
                minute = random.nextInt(0, 60),
            )
            val accountId = accountDao.insert(
                AccountEntity(
                    name = names[index],
                    initialBalance = randomAmount(
                        random = random,
                        minYuan = if (key == "investment") 12_000 else 300,
                        maxYuan = if (key == "investment") 48_000 else 18_000,
                    ),
                    createdAt = createdAt,
                    lastUsedAt = millisAt(
                        zoneId = zoneId,
                        date = today.minusDays(random.nextLong(0, 10)),
                        hour = random.nextInt(8, 22),
                        minute = random.nextInt(0, 60),
                    ),
                    lastBalanceUpdateAt = if (key == "investment" || key == "savings") {
                        millisAt(
                            zoneId = zoneId,
                            date = today.minusDays(random.nextLong(3, 18)),
                            hour = 21,
                            minute = random.nextInt(0, 60),
                        )
                    } else {
                        null
                    },
                    displayOrder = index,
                    colorName = colors[index],
                ),
            )
            key to accountId
        }.toMap()
    }

    private suspend fun seedRandomCashFlows(
        cashFlowDao: CashFlowRecordDao,
        accountIds: List<Long>,
        incomeAccountId: Long,
        random: Random,
        zoneId: ZoneId,
        today: LocalDate,
    ) {
        val incomePurposes = listOf("工资", "奖金", "报销", "副业", "退款", "利息")
        val outflowPurposes = listOf(
            "早餐",
            "午餐",
            "晚餐",
            "咖啡",
            "超市",
            "通勤",
            "打车",
            "电影",
            "健身",
            "生活用品",
            "宠物用品",
            "房租分摊",
            "网购",
            "水电燃气",
        )

        repeat(5) { monthOffset ->
            val baseMonth = today.minusMonths(monthOffset.toLong())
            val payrollDay = baseMonth.withDayOfMonth(minOf(5, baseMonth.lengthOfMonth()))
            insertCashFlow(
                dao = cashFlowDao,
                accountId = incomeAccountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = randomAmount(random = random, minYuan = 9_500, maxYuan = 18_500),
                purpose = incomePurposes.random(random),
                occurredAt = millisAt(zoneId, payrollDay, 10, random.nextInt(0, 45)),
            )

            repeat(random.nextInt(11, 18)) {
                val direction = if (random.nextInt(100) < 18) {
                    CashFlowDirection.INFLOW
                } else {
                    CashFlowDirection.OUTFLOW
                }
                val day = random.nextInt(1, baseMonth.lengthOfMonth() + 1)
                val date = baseMonth.withDayOfMonth(day).coerceAtMost(today)
                val amount = if (direction == CashFlowDirection.INFLOW) {
                    randomAmount(random = random, minYuan = 40, maxYuan = 1_200)
                } else {
                    randomAmount(random = random, minYuan = 12, maxYuan = 680)
                }
                insertCashFlow(
                    dao = cashFlowDao,
                    accountId = accountIds.random(random),
                    direction = direction.value,
                    amount = amount,
                    purpose = if (direction == CashFlowDirection.INFLOW) {
                        incomePurposes.random(random)
                    } else {
                        outflowPurposes.random(random)
                    },
                    occurredAt = millisAt(
                        zoneId = zoneId,
                        date = date,
                        hour = random.nextInt(7, 23),
                        minute = random.nextInt(0, 60),
                    ),
                )
            }
        }
    }

    private suspend fun seedRandomTransfers(
        transferDao: TransferRecordDao,
        accounts: Map<String, Long>,
        random: Random,
        zoneId: ZoneId,
        today: LocalDate,
    ) {
        val notes = listOf("月度储蓄", "基金定投", "旅行预留", "信用卡还款", "账户归集", "周末预算")
        val routes = listOf(
            accounts.getValue("salary") to accounts.getValue("savings"),
            accounts.getValue("salary") to accounts.getValue("investment"),
            accounts.getValue("salary") to accounts.getValue("travel"),
            accounts.getValue("wallet") to accounts.getValue("salary"),
            accounts.getValue("savings") to accounts.getValue("wallet"),
        )

        repeat(10) { index ->
            val (fromAccountId, toAccountId) = routes.random(random)
            val date = today.minusDays(random.nextLong(1, 135))
            insertTransfer(
                dao = transferDao,
                fromAccountId = fromAccountId,
                toAccountId = toAccountId,
                amount = randomAmount(
                    random = random,
                    minYuan = if (index < 3) 1_000 else 100,
                    maxYuan = if (index < 3) 8_000 else 2_500,
                ),
                note = notes.random(random),
                occurredAt = millisAt(
                    zoneId = zoneId,
                    date = date,
                    hour = random.nextInt(8, 21),
                    minute = random.nextInt(0, 60),
                ),
            )
        }
    }

    private suspend fun seedRandomBalanceEvents(
        balanceUpdateDao: BalanceUpdateRecordDao,
        balanceAdjustmentDao: BalanceAdjustmentRecordDao,
        accounts: Map<String, Long>,
        random: Random,
        zoneId: ZoneId,
        today: LocalDate,
    ) {
        listOf(accounts.getValue("investment"), accounts.getValue("savings")).forEach { accountId ->
            repeat(2) {
                val systemBalance = randomAmount(random = random, minYuan = 8_000, maxYuan = 62_000)
                val delta = randomAmount(random = random, minYuan = -900, maxYuan = 1_400)
                val occurredAt = millisAt(
                    zoneId = zoneId,
                    date = today.minusDays(random.nextLong(5, 160)),
                    hour = 21,
                    minute = random.nextInt(0, 60),
                )
                balanceUpdateDao.insert(
                    BalanceUpdateRecordEntity(
                        accountId = accountId,
                        actualBalance = systemBalance + delta,
                        systemBalanceBeforeUpdate = systemBalance,
                        delta = delta,
                        occurredAt = occurredAt,
                        createdAt = occurredAt,
                    ),
                )
            }
        }

        repeat(5) {
            val delta = randomAmount(random = random, minYuan = -260, maxYuan = 420)
            val occurredAt = millisAt(
                zoneId = zoneId,
                date = today.minusDays(random.nextLong(1, 90)),
                hour = random.nextInt(9, 22),
                minute = random.nextInt(0, 60),
            )
            balanceAdjustmentDao.insert(
                BalanceAdjustmentRecordEntity(
                    accountId = accounts.values.random(random),
                    delta = delta,
                    occurredAt = occurredAt,
                    createdAt = occurredAt,
                ),
            )
        }
    }

    private suspend fun seedRandomReminders(
        reminderDao: RecurringReminderDao,
        accountIds: List<Long>,
        random: Random,
        zoneId: ZoneId,
        today: LocalDate,
        now: Long,
    ) {
        val reminders = listOf(
            ReminderSeed("房租", CashFlowDirection.OUTFLOW, 2_800, 4_200, ReminderPeriodType.MONTHLY),
            ReminderSeed("交通卡", CashFlowDirection.OUTFLOW, 80, 240, ReminderPeriodType.CUSTOM_DAYS),
            ReminderSeed("会员订阅", CashFlowDirection.OUTFLOW, 18, 98, ReminderPeriodType.MONTHLY),
            ReminderSeed("保险缴费", CashFlowDirection.OUTFLOW, 1_200, 3_600, ReminderPeriodType.YEARLY),
            ReminderSeed("兼职收入", CashFlowDirection.INFLOW, 600, 2_000, ReminderPeriodType.CUSTOM_DAYS),
        ).shuffled(random).take(4)

        reminders.forEach { seed ->
            val nextDueAt = millisAt(
                zoneId = zoneId,
                date = today.plusDays(random.nextLong(-3, 16)),
                hour = 9,
                minute = random.nextInt(0, 60),
            )
            reminderDao.insert(
                RecurringReminderEntity(
                    name = seed.name,
                    type = ReminderType.MANUAL.value,
                    accountId = accountIds.random(random),
                    direction = seed.direction.value,
                    amount = randomAmount(random, seed.minYuan, seed.maxYuan),
                    periodType = seed.periodType.value,
                    periodValue = if (seed.periodType == ReminderPeriodType.CUSTOM_DAYS) {
                        listOf(14, 30, 45, 90).random(random)
                    } else {
                        1
                    },
                    periodMonth = if (seed.periodType == ReminderPeriodType.YEARLY) {
                        random.nextInt(1, 13)
                    } else {
                        null
                    },
                    nextDueAt = nextDueAt,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    private data class ReminderSeed(
        val name: String,
        val direction: CashFlowDirection,
        val minYuan: Int,
        val maxYuan: Int,
        val periodType: ReminderPeriodType,
    )

    private fun randomAmount(random: Random, minYuan: Int, maxYuan: Int): Long {
        val minFen = minYuan * 100L
        val maxFen = maxYuan * 100L
        val amount = random.nextLong(minFen, maxFen + 1)
        return amount / 100L * 100L
    }

    private suspend fun insertCashFlow(
        dao: CashFlowRecordDao,
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
        dao: TransferRecordDao,
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
