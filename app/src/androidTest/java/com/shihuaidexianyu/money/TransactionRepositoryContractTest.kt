package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneOffset

/**
 * 契约测试：保证 [InMemoryTransactionRepository]（用于单元测试）与 [TransactionRepositoryImpl]（真实 Room 实现）
 * 在关键查询路径上语义等价。当前重点覆盖：
 * - 历史搜索 LIKE 转义（包含 % 与 _ 的关键字应做字面匹配，而非通配符）
 * - 按账户查询对账/调整记录的排序
 * - 日聚合（注意：DAO 用 SQL 整数算术，InMemory 用 java.time；本测试只对中午时刻记录做断言，避开子日分歧）
 * - 最近用途去重与排序
 */
@RunWith(AndroidJUnit4::class)
class TransactionRepositoryContractTest {
    private lateinit var db: MoneyDatabase
    private lateinit var roomRepo: TransactionRepositoryImpl
    private lateinit var memoryRepo: InMemoryTransactionRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        roomRepo = TransactionRepositoryImpl(
            database = db,
            cashFlowRecordDao = db.cashFlowRecordDao(),
            transferRecordDao = db.transferRecordDao(),
            balanceUpdateRecordDao = db.balanceUpdateRecordDao(),
            balanceAdjustmentRecordDao = db.balanceAdjustmentRecordDao(),
            historyRecordDao = db.historyRecordDao(),
        )
        memoryRepo = InMemoryTransactionRepository()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun historyKeywordWithPercentSign_matchesLiterallyInBothImplementations() = runBlocking {
        seedAccountAndCashFlows()
        val filters = HistoryRecordFilters(
            keyword = "100%",
            excludeKeyword = "",
            accountId = null,
            dateStartAt = null,
            dateEndAt = null,
            minAmount = null,
            maxAmount = null,
            amountDirection = HistoryAmountDirection.ALL,
        )
        val roomResults = roomRepo.queryHistoryRecords(filters, cursor = null, limit = 50)
        val memoryResults = memoryRepo.queryHistoryRecords(filters, cursor = null, limit = 50)
        assertEquals(memoryResults, roomResults)
        assertTrue("应有至少 1 条命中 '100%' 字面匹配", roomResults.isNotEmpty())
    }

    @Test
    fun historyKeywordWithUnderscore_matchesLiterallyInBothImplementations() = runBlocking {
        seedAccountAndCashFlows()
        val filters = HistoryRecordFilters(
            keyword = "a_b",
            excludeKeyword = "",
            accountId = null,
            dateStartAt = null,
            dateEndAt = null,
            minAmount = null,
            maxAmount = null,
            amountDirection = HistoryAmountDirection.ALL,
        )
        val roomResults = roomRepo.queryHistoryRecords(filters, cursor = null, limit = 50)
        val memoryResults = memoryRepo.queryHistoryRecords(filters, cursor = null, limit = 50)
        assertEquals(memoryResults, roomResults)
        assertTrue("应有至少 1 条命中 'a_b' 字面匹配", roomResults.isNotEmpty())
    }

    @Test
    fun balanceUpdateRecordsByAccountIdAreSortedByOccurredAtDescThenIdDesc() = runBlocking {
        seedAccount()
        val accountId = 1L
        val baseTime = 10_000L
        roomRepo.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 0,
                systemBalanceBeforeUpdate = 0,
                delta = 100,
                occurredAt = baseTime,
                createdAt = baseTime,
            ),
        )
        roomRepo.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 0,
                systemBalanceBeforeUpdate = 0,
                delta = 200,
                occurredAt = baseTime + 1_000,
                createdAt = baseTime + 1_000,
            ),
        )
        memoryRepo.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 0,
                systemBalanceBeforeUpdate = 0,
                delta = 100,
                occurredAt = baseTime,
                createdAt = baseTime,
            ),
        )
        memoryRepo.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = accountId,
                actualBalance = 0,
                systemBalanceBeforeUpdate = 0,
                delta = 200,
                occurredAt = baseTime + 1_000,
                createdAt = baseTime + 1_000,
            ),
        )

        val roomRecords = roomRepo.queryBalanceUpdateRecordsByAccountId(accountId)
        val memoryRecords = memoryRepo.queryBalanceUpdateRecordsByAccountId(accountId)
        assertEquals("InMemory 应与 DAO 排序一致", roomRecords, memoryRecords)
        assertEquals(
            "应按 occurredAt DESC 排序",
            listOf(200L, 100L),
            roomRecords.map { it.delta },
        )
    }

    @Test
    fun balanceAdjustmentRecordsByAccountIdAreSortedByOccurredAtDescThenIdDesc() = runBlocking {
        seedAccount()
        val accountId = 1L
        val baseTime = 10_000L
        roomRepo.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(accountId = accountId, delta = 50, occurredAt = baseTime, createdAt = baseTime),
        )
        roomRepo.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(accountId = accountId, delta = -30, occurredAt = baseTime + 1_000, createdAt = baseTime + 1_000),
        )
        memoryRepo.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(accountId = accountId, delta = 50, occurredAt = baseTime, createdAt = baseTime),
        )
        memoryRepo.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(accountId = accountId, delta = -30, occurredAt = baseTime + 1_000, createdAt = baseTime + 1_000),
        )

        val roomRecords = roomRepo.queryBalanceAdjustmentRecordsByAccountId(accountId)
        val memoryRecords = memoryRepo.queryBalanceAdjustmentRecordsByAccountId(accountId)
        assertEquals("InMemory 应与 DAO 排序一致", roomRecords, memoryRecords)
    }

    @Test
    fun dailyCashFlowTotalsAtLocalNoon_areEquivalentAcrossImplementations() = runBlocking {
        seedAccount()
        val accountId = 1L
        val zone = ZoneOffset.ofHours(8)
        val zoneOffsetSeconds = zone.totalSeconds
        val localNoonUtcMillis = 1_704_062_400_000L // 2024-01-01 00:00 UTC = 2024-01-01 08:00 +08:00
        val noonLocalMillis = localNoonUtcMillis + 4 * 3600_000L // 12:00 +08:00
        val record = CashFlowRecord(
            accountId = accountId,
            direction = CashFlowDirection.INFLOW.value,
            amount = 1000,
            purpose = "薪水",
            occurredAt = noonLocalMillis,
            createdAt = noonLocalMillis,
            updatedAt = noonLocalMillis,
            isDeleted = false,
        )
        roomRepo.insertCashFlowRecord(record)
        memoryRepo.insertCashFlowRecord(record)

        val startAt = noonLocalMillis - 1
        val endAt = noonLocalMillis + 1
        val roomTotals = roomRepo.queryDailyCashFlowTotals(startAt, endAt, zoneOffsetSeconds)
        val memoryTotals = memoryRepo.queryDailyCashFlowTotals(startAt, endAt, zoneOffsetSeconds)
        assertEquals(memoryTotals, roomTotals)
        assertTrue(roomTotals.isNotEmpty())
        val expected: List<CashFlowDailyTotal> = listOf(
            CashFlowDailyTotal(
                epochDay = 19_723L, // 2024-01-01 epoch day
                direction = CashFlowDirection.INFLOW.value,
                amount = 1000,
            ),
        )
        assertEquals(expected, roomTotals)
    }

    @Test
    fun recentPurposes_returnsDistinctPurposesInBothImplementations() = runBlocking {
        seedAccount()
        val accountId = 1L
        val baseTime = 10_000L
        repeat(3) { i ->
            roomRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = CashFlowDirection.OUTFLOW.value,
                    amount = 100,
                    purpose = "吃饭",
                    occurredAt = baseTime + i * 1_000L,
                    createdAt = baseTime + i * 1_000L,
                    updatedAt = baseTime + i * 1_000L,
                    isDeleted = false,
                ),
            )
            memoryRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = CashFlowDirection.OUTFLOW.value,
                    amount = 100,
                    purpose = "吃饭",
                    occurredAt = baseTime + i * 1_000L,
                    createdAt = baseTime + i * 1_000L,
                    updatedAt = baseTime + i * 1_000L,
                    isDeleted = false,
                ),
            )
        }
        roomRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 200,
                purpose = "打车",
                occurredAt = baseTime + 5_000L,
                createdAt = baseTime + 5_000L,
                updatedAt = baseTime + 5_000L,
                isDeleted = false,
            ),
        )
        memoryRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 200,
                purpose = "打车",
                occurredAt = baseTime + 5_000L,
                createdAt = baseTime + 5_000L,
                updatedAt = baseTime + 5_000L,
                isDeleted = false,
            ),
        )

        val roomPurposes = roomRepo.queryRecentCashFlowPurposes(
            direction = CashFlowDirection.OUTFLOW.value,
            accountId = accountId,
            limit = 10,
        )
        val memoryPurposes = memoryRepo.queryRecentCashFlowPurposes(
            direction = CashFlowDirection.OUTFLOW.value,
            accountId = accountId,
            limit = 10,
        )
        assertEquals(memoryPurposes, roomPurposes)
        assertEquals(listOf("打车", "吃饭"), roomPurposes)
    }

    private suspend fun seedAccount() {
        val accountDao = db.accountDao()
        accountDao.insert(
            com.shihuaidexianyu.money.data.entity.AccountEntity(
                id = 1,
                name = "测试账户",
                initialBalance = 100_000,
                createdAt = 1_000L,
                displayOrder = 1,
            ),
        )
    }

    private suspend fun seedAccountAndCashFlows() {
        seedAccount()
        val accountId = 1L
        val baseTime = 1_700_000_000_000L
        val samples = listOf(
            Triple("工资 100% bonus", 5_000L, CashFlowDirection.INFLOW.value),
            Triple("a_b 测试", 1_200L, CashFlowDirection.OUTFLOW.value),
            Triple("普通吃饭", 50_00L, CashFlowDirection.OUTFLOW.value),
            Triple("100% 报销", 3_400L, CashFlowDirection.INFLOW.value),
        )
        samples.forEachIndexed { index, (purpose, amount, direction) ->
            val occurredAt = baseTime + index * 60_000L
            roomRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = direction,
                    amount = amount,
                    purpose = purpose,
                    occurredAt = occurredAt,
                    createdAt = occurredAt,
                    updatedAt = occurredAt,
                    isDeleted = false,
                ),
            )
            memoryRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = direction,
                    amount = amount,
                    purpose = purpose,
                    occurredAt = occurredAt,
                    createdAt = occurredAt,
                    updatedAt = occurredAt,
                    isDeleted = false,
                ),
            )
        }
    }
}
