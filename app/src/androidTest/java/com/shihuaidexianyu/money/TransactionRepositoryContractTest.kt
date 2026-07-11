package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.data.repository.TransactionRepositoryImpl
import com.shihuaidexianyu.money.data.repository.toEntity
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryAmountDirection
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.LedgerOperationConflictException
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerInsertResult
import com.shihuaidexianyu.money.domain.model.TransferRecord
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * - 四类账本记录软删除后的保留与活动查询排除语义
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
            ledgerAggregateDao = db.ledgerAggregateDao(),
        )
        memoryRepo = InMemoryTransactionRepository { accountId ->
            when (accountId) {
                1L -> "测试账户"
                2L -> "第二账户"
                3L -> "第三账户"
                else -> null
            }
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun fourKindDeleteAndRestoreCas_areEquivalentAndPreservePayload() = runBlocking {
        seedAccount()
        db.accountDao().insert(
            com.shihuaidexianyu.money.data.entity.AccountEntity(
                id = 2,
                name = "第二账户",
                initialBalance = 0,
                createdAt = 1,
                displayOrder = 1,
            ),
        )

        listOf(roomRepo, memoryRepo).forEach { repository ->
            val cashId = repository.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = 1,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = 100,
                    note = "工资",
                    occurredAt = 10,
                    createdAt = 10,
                    updatedAt = 10,
                    operationId = "restore-cash",
                ),
            ).recordId
            val cash = requireNotNull(repository.queryStoredCashFlowRecordById(cashId))
            assertFalse(repository.softDeleteCashFlowRecord(cashId, "wrong", 10, 100))
            assertFalse(repository.softDeleteCashFlowRecord(cashId, cash.operationId, 9, 100))
            assertTrue(repository.softDeleteCashFlowRecord(cashId, cash.operationId, 10, 100))
            assertEquals(cash.copy(updatedAt = 100, deletedAt = 100), repository.queryStoredCashFlowRecordById(cashId))
            assertFalse(repository.restoreCashFlowRecord(cashId, cash.operationId, 99, 200))
            assertTrue(repository.restoreCashFlowRecord(cashId, cash.operationId, 100, 200))
            assertEquals(cash.copy(updatedAt = 200), repository.queryStoredCashFlowRecordById(cashId))

            val transferId = repository.insertTransferRecord(
                TransferRecord(
                    fromAccountId = 1,
                    toAccountId = 2,
                    amount = 50,
                    note = "转账",
                    occurredAt = 11,
                    createdAt = 11,
                    updatedAt = 11,
                    operationId = "restore-transfer",
                ),
            ).recordId
            val transfer = requireNotNull(repository.queryStoredTransferRecordById(transferId))
            assertFalse(repository.softDeleteTransferRecord(transferId, "wrong", 11, 101))
            assertTrue(repository.softDeleteTransferRecord(transferId, transfer.operationId, 11, 101))
            assertEquals(
                transfer.copy(updatedAt = 101, deletedAt = 101),
                repository.queryStoredTransferRecordById(transferId),
            )
            assertFalse(repository.restoreTransferRecord(transferId, transfer.operationId, 100, 201))
            assertTrue(repository.restoreTransferRecord(transferId, transfer.operationId, 101, 201))
            assertEquals(transfer.copy(updatedAt = 201), repository.queryStoredTransferRecordById(transferId))

            val updateId = repository.insertBalanceUpdateRecord(
                BalanceUpdateRecord(
                    accountId = 1,
                    actualBalance = 500,
                    systemBalanceBeforeUpdate = 480,
                    delta = 20,
                    occurredAt = 12,
                    createdAt = 12,
                    updatedAt = 12,
                    operationId = "restore-update",
                ),
            ).recordId
            val update = requireNotNull(repository.queryStoredBalanceUpdateRecordById(updateId))
            assertFalse(repository.softDeleteBalanceUpdateRecord(updateId, "wrong", 12, 102))
            assertTrue(repository.softDeleteBalanceUpdateRecord(updateId, update.operationId, 12, 102))
            assertEquals(
                update.copy(updatedAt = 102, deletedAt = 102),
                repository.queryStoredBalanceUpdateRecordById(updateId),
            )
            assertFalse(repository.restoreBalanceUpdateRecord(updateId, update.operationId, 101, 202))
            assertTrue(repository.restoreBalanceUpdateRecord(updateId, update.operationId, 102, 202))
            assertEquals(update.copy(updatedAt = 202), repository.queryStoredBalanceUpdateRecordById(updateId))

            val adjustmentId = repository.insertBalanceAdjustmentRecord(
                BalanceAdjustmentRecord(
                    accountId = 1,
                    delta = -10,
                    occurredAt = 13,
                    createdAt = 13,
                    updatedAt = 13,
                    operationId = "restore-adjustment",
                ),
            ).recordId
            val adjustment = requireNotNull(repository.queryStoredBalanceAdjustmentRecordById(adjustmentId))
            assertFalse(repository.softDeleteBalanceAdjustmentRecord(adjustmentId, "wrong", 13, 103))
            assertTrue(
                repository.softDeleteBalanceAdjustmentRecord(
                    adjustmentId,
                    adjustment.operationId,
                    13,
                    103,
                ),
            )
            assertEquals(
                adjustment.copy(updatedAt = 103, deletedAt = 103),
                repository.queryStoredBalanceAdjustmentRecordById(adjustmentId),
            )
            assertFalse(repository.restoreBalanceAdjustmentRecord(adjustmentId, adjustment.operationId, 102, 203))
            assertTrue(repository.restoreBalanceAdjustmentRecord(adjustmentId, adjustment.operationId, 103, 203))
            assertEquals(
                adjustment.copy(updatedAt = 203),
                repository.queryStoredBalanceAdjustmentRecordById(adjustmentId),
            )
        }
    }

    @Test
    fun idempotentInsertResultsAndConflicts_areEquivalentAcrossImplementations() = runBlocking {
        seedAccount()
        db.accountDao().insert(
            com.shihuaidexianyu.money.data.entity.AccountEntity(
                id = 2,
                name = "第二账户",
                initialBalance = 0,
                createdAt = 1_000,
                displayOrder = 2,
            ),
        )

        val cash = CashFlowRecord(
            accountId = 1,
            direction = CashFlowDirection.INFLOW.value,
            amount = 100,
            note = "  工资  ",
            occurredAt = 2_000,
            createdAt = 2_000,
            updatedAt = 2_000,
            operationId = "parity-cash",
        )
        assertEquals(roomRepo.insertCashFlowRecord(cash), memoryRepo.insertCashFlowRecord(cash))
        assertEquals(
            roomRepo.insertCashFlowRecord(cash.copy(note = "工资", createdAt = 9_000, updatedAt = 9_000)),
            memoryRepo.insertCashFlowRecord(cash.copy(note = "工资", createdAt = 9_000, updatedAt = 9_000)),
        )
        assertConflictParity(LedgerRecordKind.CASH_FLOW) { repository ->
            repository.insertCashFlowRecord(cash.copy(amount = 101))
        }
        assertTrue(
            runCatching {
                roomRepo.insertCashFlowRecord(cash.copy(id = 1, operationId = "room-explicit-id"))
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                memoryRepo.insertCashFlowRecord(cash.copy(id = 1, operationId = "memory-explicit-id"))
            }.exceptionOrNull() is IllegalArgumentException,
        )
        assertTrue(
            runCatching {
                db.cashFlowRecordDao().insert(cash.copy(id = 1, operationId = "strict-dao-pk-conflict").toEntity())
            }.isFailure,
        )

        val transfer = TransferRecord(
            fromAccountId = 1,
            toAccountId = 2,
            amount = 200,
            note = "  调拨  ",
            occurredAt = 2_000,
            createdAt = 2_000,
            updatedAt = 2_000,
            operationId = "parity-transfer",
        )
        assertEquals(roomRepo.insertTransferRecord(transfer), memoryRepo.insertTransferRecord(transfer))
        assertEquals(
            roomRepo.insertTransferRecord(transfer.copy(note = "调拨", createdAt = 9_000)),
            memoryRepo.insertTransferRecord(transfer.copy(note = "调拨", createdAt = 9_000)),
        )
        assertConflictParity(LedgerRecordKind.TRANSFER) { repository ->
            repository.insertTransferRecord(transfer.copy(amount = 201))
        }

        val update = BalanceUpdateRecord(
            accountId = 1,
            actualBalance = 300,
            systemBalanceBeforeUpdate = 100,
            delta = 200,
            occurredAt = 2_000,
            createdAt = 2_000,
            updatedAt = 2_000,
            operationId = "parity-update",
        )
        assertEquals(roomRepo.insertBalanceUpdateRecord(update), memoryRepo.insertBalanceUpdateRecord(update))
        assertEquals(
            roomRepo.insertBalanceUpdateRecord(update.copy(systemBalanceBeforeUpdate = -1, delta = 301)),
            memoryRepo.insertBalanceUpdateRecord(update.copy(systemBalanceBeforeUpdate = -1, delta = 301)),
        )
        assertConflictParity(LedgerRecordKind.BALANCE_UPDATE) { repository ->
            repository.insertBalanceUpdateRecord(update.copy(actualBalance = 301))
        }

        val adjustment = BalanceAdjustmentRecord(
            accountId = 1,
            delta = -50,
            occurredAt = 2_000,
            createdAt = 2_000,
            updatedAt = 2_000,
            operationId = "parity-adjustment",
        )
        assertEquals(roomRepo.insertBalanceAdjustmentRecord(adjustment), memoryRepo.insertBalanceAdjustmentRecord(adjustment))
        assertEquals(
            roomRepo.insertBalanceAdjustmentRecord(adjustment.copy(createdAt = 9_000, updatedAt = 9_000)),
            memoryRepo.insertBalanceAdjustmentRecord(adjustment.copy(createdAt = 9_000, updatedAt = 9_000)),
        )
        assertConflictParity(LedgerRecordKind.BALANCE_ADJUSTMENT) { repository ->
            repository.insertBalanceAdjustmentRecord(adjustment.copy(delta = -51))
        }

        val roomCash = requireNotNull(roomRepo.queryCashFlowRecordByOperationId(cash.operationId))
        val memoryCash = requireNotNull(memoryRepo.queryCashFlowRecordByOperationId(cash.operationId))
        roomRepo.softDeleteCurrentCashFlowRecord(roomCash.id, 3_000)
        memoryRepo.softDeleteCurrentCashFlowRecord(memoryCash.id, 3_000)
        assertFalse(roomRepo.insertCashFlowRecord(cash.copy(note = "工资")).inserted)
        assertFalse(memoryRepo.insertCashFlowRecord(cash.copy(note = "工资")).inserted)
        assertFalse(roomRepo.updateCashFlowRecord(roomCash.copy(amount = 999, updatedAt = 4_000), roomCash.updatedAt))
        assertFalse(memoryRepo.updateCashFlowRecord(memoryCash.copy(amount = 999, updatedAt = 4_000), memoryCash.updatedAt))
        assertEquals(
            memoryRepo.queryCashFlowRecordByOperationId(cash.operationId),
            roomRepo.queryCashFlowRecordByOperationId(cash.operationId),
        )

        val roomTransfer = requireNotNull(roomRepo.queryTransferRecordByOperationId(transfer.operationId))
        val memoryTransfer = requireNotNull(memoryRepo.queryTransferRecordByOperationId(transfer.operationId))
        roomRepo.softDeleteCurrentTransferRecord(roomTransfer.id, 3_000)
        memoryRepo.softDeleteCurrentTransferRecord(memoryTransfer.id, 3_000)
        assertFalse(roomRepo.insertTransferRecord(transfer.copy(note = "调拨")).inserted)
        assertFalse(memoryRepo.insertTransferRecord(transfer.copy(note = "调拨")).inserted)
        assertFalse(roomRepo.updateTransferRecord(roomTransfer.copy(amount = 999, updatedAt = 4_000), roomTransfer.updatedAt))
        assertFalse(memoryRepo.updateTransferRecord(memoryTransfer.copy(amount = 999, updatedAt = 4_000), memoryTransfer.updatedAt))
        assertEquals(
            memoryRepo.queryTransferRecordByOperationId(transfer.operationId),
            roomRepo.queryTransferRecordByOperationId(transfer.operationId),
        )

        val roomUpdate = requireNotNull(roomRepo.queryBalanceUpdateRecordByOperationId(update.operationId))
        val memoryUpdate = requireNotNull(memoryRepo.queryBalanceUpdateRecordByOperationId(update.operationId))
        roomRepo.softDeleteCurrentBalanceUpdateRecord(roomUpdate.id, 3_000)
        memoryRepo.softDeleteCurrentBalanceUpdateRecord(memoryUpdate.id, 3_000)
        assertFalse(roomRepo.insertBalanceUpdateRecord(update.copy(delta = 999)).inserted)
        assertFalse(memoryRepo.insertBalanceUpdateRecord(update.copy(delta = 999)).inserted)
        assertFalse(roomRepo.updateBalanceUpdateRecord(roomUpdate.copy(actualBalance = 999, updatedAt = 4_000), roomUpdate.updatedAt))
        assertFalse(memoryRepo.updateBalanceUpdateRecord(memoryUpdate.copy(actualBalance = 999, updatedAt = 4_000), memoryUpdate.updatedAt))
        assertEquals(
            memoryRepo.queryBalanceUpdateRecordByOperationId(update.operationId),
            roomRepo.queryBalanceUpdateRecordByOperationId(update.operationId),
        )

        val roomAdjustment = requireNotNull(roomRepo.queryBalanceAdjustmentRecordByOperationId(adjustment.operationId))
        val memoryAdjustment = requireNotNull(memoryRepo.queryBalanceAdjustmentRecordByOperationId(adjustment.operationId))
        roomRepo.softDeleteCurrentBalanceAdjustmentRecord(roomAdjustment.id, 3_000)
        memoryRepo.softDeleteCurrentBalanceAdjustmentRecord(memoryAdjustment.id, 3_000)
        assertFalse(roomRepo.insertBalanceAdjustmentRecord(adjustment).inserted)
        assertFalse(memoryRepo.insertBalanceAdjustmentRecord(adjustment).inserted)
        assertFalse(
            roomRepo.updateBalanceAdjustmentRecord(
                roomAdjustment.copy(delta = 999, updatedAt = 4_000),
                roomAdjustment.updatedAt,
            ),
        )
        assertFalse(
            memoryRepo.updateBalanceAdjustmentRecord(
                memoryAdjustment.copy(delta = 999, updatedAt = 4_000),
                memoryAdjustment.updatedAt,
            ),
        )
        assertEquals(
            memoryRepo.queryBalanceAdjustmentRecordByOperationId(adjustment.operationId),
            roomRepo.queryBalanceAdjustmentRecordByOperationId(adjustment.operationId),
        )
    }

    @Test
    fun concurrentEqualRoomInserts_convergeToOneRowAndOneInsertion() = runBlocking {
        seedAccount()
        db.accountDao().insert(
            com.shihuaidexianyu.money.data.entity.AccountEntity(
                id = 2,
                name = "第二账户",
                initialBalance = 0,
                createdAt = 1_000,
                displayOrder = 2,
            ),
        )
        val cash = CashFlowRecord(
            accountId = 1,
            direction = CashFlowDirection.INFLOW.value,
            amount = 100,
            note = "并发",
            occurredAt = 2_000,
            createdAt = 2_000,
            updatedAt = 2_000,
            operationId = "room-concurrent-cash",
        )
        val transfer = TransferRecord(
            fromAccountId = 1,
            toAccountId = 2,
            amount = 100,
            note = "并发",
            occurredAt = 2_000,
            createdAt = 2_000,
            updatedAt = 2_000,
            operationId = "room-concurrent-transfer",
        )
        val update = BalanceUpdateRecord(
            accountId = 1,
            actualBalance = 100,
            systemBalanceBeforeUpdate = 0,
            delta = 100,
            occurredAt = 2_000,
            createdAt = 2_000,
            updatedAt = 2_000,
            operationId = "room-concurrent-update",
        )
        val adjustment = BalanceAdjustmentRecord(
            accountId = 1,
            delta = 100,
            occurredAt = 2_000,
            createdAt = 2_000,
            updatedAt = 2_000,
            operationId = "room-concurrent-adjustment",
        )

        assertConcurrentConvergence { roomRepo.insertCashFlowRecord(cash) }
        assertConcurrentConvergence { roomRepo.insertTransferRecord(transfer) }
        assertConcurrentConvergence { roomRepo.insertBalanceUpdateRecord(update) }
        assertConcurrentConvergence { roomRepo.insertBalanceAdjustmentRecord(adjustment) }

        assertEquals(1, roomRepo.queryAllCashFlowRecords().size)
        assertEquals(1, roomRepo.queryAllTransferRecords().size)
        assertEquals(1, roomRepo.queryAllBalanceUpdateRecords().size)
        assertEquals(1, roomRepo.queryAllBalanceAdjustmentRecords().size)
    }

    @Test
    fun rolledBackRoomTransaction_doesNotPublishPhantomChange() = runBlocking {
        seedAccount()
        val emissions = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val initialEmission = CompletableDeferred<Unit>()
        val collectionJob = launch(Dispatchers.Default) {
            roomRepo.observeChangeVersion().collect { version ->
                emissions += version
                initialEmission.complete(Unit)
            }
        }
        try {
            initialEmission.await()
            val failure = runCatching {
                roomRepo.runInTransaction {
                    roomRepo.insertCashFlowRecord(
                        CashFlowRecord(
                            accountId = 1,
                            direction = CashFlowDirection.INFLOW.value,
                            amount = 100,
                            note = "回滚",
                            occurredAt = 2_000,
                            createdAt = 2_000,
                            updatedAt = 2_000,
                            operationId = "rollback-notification",
                        ),
                    )
                    error("rollback")
                }
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            delay(100)
            assertTrue(roomRepo.queryAllCashFlowRecords().isEmpty())
            assertEquals("回滚事务不应发布伪变更", 1, emissions.size)

            roomRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = 1,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = 100,
                    note = "提交",
                    occurredAt = 2_000,
                    createdAt = 2_000,
                    updatedAt = 2_000,
                    operationId = "committed-notification",
                ),
            )
            repeat(20) {
                if (emissions.size < 2) delay(10)
            }
            assertTrue("提交后的插入应发布变更", emissions.size >= 2)
            val emissionCountAfterInsert = emissions.size
            val committed = requireNotNull(
                roomRepo.queryCashFlowRecordByOperationId("committed-notification"),
            )
            assertTrue(
                roomRepo.updateCashFlowRecord(
                    committed.copy(amount = 200, updatedAt = 3_000),
                    committed.updatedAt,
                ),
            )
            repeat(20) {
                if (emissions.size <= emissionCountAfterInsert) delay(10)
            }
            assertTrue("提交后的同计数更新也应发布变更", emissions.size > emissionCountAfterInsert)
        } finally {
            collectionJob.cancelAndJoin()
        }
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
    fun historyKeywordWithBackslash_matchesLiterallyInBothImplementations() = runBlocking {
        seedAccountAndCashFlows()
        val filters = HistoryRecordFilters(keyword = "PATH\\RECEIPT")

        val roomResults = roomRepo.queryHistoryRecords(filters, cursor = null, limit = 50)
        val memoryResults = memoryRepo.queryHistoryRecords(filters, cursor = null, limit = 50)

        assertEquals(memoryResults, roomResults)
        assertEquals(listOf("path\\receipt"), roomResults.map { it.title })
    }

    @Test
    fun historyAmountMagnitudeHandlesLongMinInBothImplementations() = runBlocking {
        seedAccount()
        val record = BalanceAdjustmentRecord(
            accountId = 1L,
            delta = Long.MIN_VALUE,
            occurredAt = 2_000L,
            createdAt = 2_000L,
            updatedAt = 2_000L,
            operationId = "history-long-min",
        )
        roomRepo.insertBalanceAdjustmentRecord(record)
        memoryRepo.insertBalanceAdjustmentRecord(record)

        listOf(
            HistoryRecordFilters(minAmount = Long.MAX_VALUE),
            HistoryRecordFilters(maxAmount = Long.MAX_VALUE),
        ).forEach { filters ->
            assertEquals(
                memoryRepo.queryHistoryRecords(filters, null, 50),
                roomRepo.queryHistoryRecords(filters, null, 50),
            )
        }
        assertEquals(1, roomRepo.countHistoryRecords(HistoryRecordFilters(minAmount = Long.MAX_VALUE)))
        assertEquals(0, roomRepo.countHistoryRecords(HistoryRecordFilters(maxAmount = Long.MAX_VALUE)))
    }

    @Test
    fun historySearchesAccountAndSystemTitlesAndCombinesRecordTypes() = runBlocking {
        seedAccountAndCashFlows()
        val update = BalanceUpdateRecord(
            accountId = 1L,
            actualBalance = 100_000L,
            systemBalanceBeforeUpdate = 100_000L,
            delta = 0L,
            occurredAt = 1_800_000_000_000L,
            createdAt = 1_800_000_000_000L,
            updatedAt = 1_800_000_000_000L,
            operationId = "history-system-title",
        )
        roomRepo.insertBalanceUpdateRecord(update)
        memoryRepo.insertBalanceUpdateRecord(update)

        listOf("测试账户", "余额核对").forEach { keyword ->
            val filters = HistoryRecordFilters(
                keyword = keyword,
                recordTypes = setOf(HistoryRecordType.BALANCE_UPDATE),
            )
            assertEquals(
                memoryRepo.queryHistoryRecords(filters, null, 50),
                roomRepo.queryHistoryRecords(filters, null, 50),
            )
        }
    }

    @Test
    fun historyTransferPathFilterMatchesExactSourceAndDestinationInBothImplementations() = runBlocking {
        seedAccount()
        listOf(2L to "第二账户", 3L to "第三账户").forEach { (id, name) ->
            db.accountDao().insert(
                com.shihuaidexianyu.money.data.entity.AccountEntity(
                    id = id,
                    name = name,
                    initialBalance = 0L,
                    createdAt = 1_000L,
                    displayOrder = id.toInt(),
                ),
            )
        }
        listOf(2L, 3L).forEach { targetId ->
            val record = TransferRecord(
                fromAccountId = 1L,
                toAccountId = targetId,
                amount = targetId * 100L,
                note = "路径$targetId",
                occurredAt = 2_000L + targetId,
                createdAt = 2_000L + targetId,
                updatedAt = 2_000L + targetId,
                operationId = "history-path-$targetId",
            )
            roomRepo.insertTransferRecord(record)
            memoryRepo.insertTransferRecord(record)
        }
        val filters = HistoryRecordFilters(
            recordTypes = setOf(HistoryRecordType.TRANSFER),
            transferFromAccountId = 1L,
            transferToAccountId = 2L,
            dateStartAt = 1_000L,
            dateEndAt = 3_000L,
        )

        assertEquals(
            memoryRepo.queryHistoryRecords(filters, null, 20),
            roomRepo.queryHistoryRecords(filters, null, 20),
        )
        assertEquals(1, roomRepo.countHistoryRecords(filters))
        assertEquals(
            memoryRepo.queryTransferPathTotalsBetween(1_000L, 3_000L),
            roomRepo.queryTransferPathTotalsBetween(1_000L, 3_000L),
        )
    }

    @Test
    fun analysisCashProjectionUsesOnlyActiveHalfOpenRowsWithRoomMemoryParity() = runBlocking {
        seedAccountAndCashFlows()
        val start = 1_700_000_000_000L
        val end = start + 3 * 60_000L

        assertEquals(
            memoryRepo.queryCashFlowAnalysisEntriesBetween(start, end),
            roomRepo.queryCashFlowAnalysisEntriesBetween(start, end),
        )
        assertTrue(roomRepo.queryCashFlowAnalysisEntriesBetween(start, end).all {
            it.occurredAt >= start && it.occurredAt < end
        })
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
                updatedAt = baseTime,
                operationId = "sort-update-1",
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
                updatedAt = baseTime + 1_000,
                operationId = "sort-update-2",
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
                updatedAt = baseTime,
                operationId = "sort-update-1",
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
                updatedAt = baseTime + 1_000,
                operationId = "sort-update-2",
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
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = 50,
                occurredAt = baseTime,
                createdAt = baseTime,
                updatedAt = baseTime,
                operationId = "sort-adjustment-1",
            ),
        )
        roomRepo.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = -30,
                occurredAt = baseTime + 1_000,
                createdAt = baseTime + 1_000,
                updatedAt = baseTime + 1_000,
                operationId = "sort-adjustment-2",
            ),
        )
        memoryRepo.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = 50,
                occurredAt = baseTime,
                createdAt = baseTime,
                updatedAt = baseTime,
                operationId = "sort-adjustment-1",
            ),
        )
        memoryRepo.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = -30,
                occurredAt = baseTime + 1_000,
                createdAt = baseTime + 1_000,
                updatedAt = baseTime + 1_000,
                operationId = "sort-adjustment-2",
            ),
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
            note = "薪水",
            occurredAt = noonLocalMillis,
            createdAt = noonLocalMillis,
            updatedAt = noonLocalMillis,
            operationId = "daily-cash-noon",
        )
        roomRepo.insertCashFlowRecord(record)
        memoryRepo.insertCashFlowRecord(record)

        val startAt = noonLocalMillis
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
    fun recentNotes_returnsDistinctNotesInBothImplementations() = runBlocking {
        seedAccount()
        val accountId = 1L
        val baseTime = 10_000L
        repeat(3) { i ->
            roomRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = CashFlowDirection.OUTFLOW.value,
                    amount = 100,
                    note = "吃饭",
                    occurredAt = baseTime + i * 1_000L,
                    createdAt = baseTime + i * 1_000L,
                    updatedAt = baseTime + i * 1_000L,
                    operationId = "recent-meal-$i",
                ),
            )
            memoryRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = CashFlowDirection.OUTFLOW.value,
                    amount = 100,
                    note = "吃饭",
                    occurredAt = baseTime + i * 1_000L,
                    createdAt = baseTime + i * 1_000L,
                    updatedAt = baseTime + i * 1_000L,
                    operationId = "recent-meal-$i",
                ),
            )
        }
        roomRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 200,
                note = "打车",
                occurredAt = baseTime + 5_000L,
                createdAt = baseTime + 5_000L,
                updatedAt = baseTime + 5_000L,
                operationId = "recent-taxi",
            ),
        )
        memoryRepo.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 200,
                note = "打车",
                occurredAt = baseTime + 5_000L,
                createdAt = baseTime + 5_000L,
                updatedAt = baseTime + 5_000L,
                operationId = "recent-taxi",
            ),
        )

        val roomNotes = roomRepo.queryRecentCashFlowNotes(
            direction = CashFlowDirection.OUTFLOW.value,
            accountId = accountId,
            limit = 10,
        )
        val memoryNotes = memoryRepo.queryRecentCashFlowNotes(
            direction = CashFlowDirection.OUTFLOW.value,
            accountId = accountId,
            limit = 10,
        )
        assertEquals(memoryNotes, roomNotes)
        assertEquals(listOf("打车", "吃饭"), roomNotes)
    }

    @Test
    fun periodQueries_includeStartAndExcludeEndInBothImplementations() = runBlocking {
        seedAccount()
        val startInclusive = 10_000L
        val endExclusive = 20_000L
        listOf(roomRepo, memoryRepo).forEach { repository ->
            repository.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = 1L,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = 100,
                    note = "起点",
                    occurredAt = startInclusive,
                    createdAt = startInclusive,
                    updatedAt = startInclusive,
                    operationId = "period-cash-start",
                ),
            )
            repository.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = 1L,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = 200,
                    note = "终点",
                    occurredAt = endExclusive,
                    createdAt = endExclusive,
                    updatedAt = endExclusive,
                    operationId = "period-cash-end",
                ),
            )
            repository.insertTransferRecord(
                TransferRecord(
                    fromAccountId = 1L,
                    toAccountId = 1L,
                    amount = 300,
                    note = "起点",
                    occurredAt = startInclusive,
                    createdAt = startInclusive,
                    updatedAt = startInclusive,
                    operationId = "period-transfer-start",
                ),
            )
            repository.insertTransferRecord(
                TransferRecord(
                    fromAccountId = 1L,
                    toAccountId = 1L,
                    amount = 400,
                    note = "终点",
                    occurredAt = endExclusive,
                    createdAt = endExclusive,
                    updatedAt = endExclusive,
                    operationId = "period-transfer-end",
                ),
            )
            repository.insertBalanceUpdateRecord(
                BalanceUpdateRecord(
                    accountId = 1L,
                    actualBalance = 500,
                    systemBalanceBeforeUpdate = 0,
                    delta = 500,
                    occurredAt = startInclusive,
                    createdAt = startInclusive,
                    updatedAt = startInclusive,
                    operationId = "period-update-start",
                ),
            )
            repository.insertBalanceUpdateRecord(
                BalanceUpdateRecord(
                    accountId = 1L,
                    actualBalance = 600,
                    systemBalanceBeforeUpdate = 0,
                    delta = 600,
                    occurredAt = endExclusive,
                    createdAt = endExclusive,
                    updatedAt = endExclusive,
                    operationId = "period-update-end",
                ),
            )
            repository.insertBalanceAdjustmentRecord(
                BalanceAdjustmentRecord(
                    accountId = 1L,
                    delta = 700,
                    occurredAt = startInclusive,
                    createdAt = startInclusive,
                    updatedAt = startInclusive,
                    operationId = "period-adjustment-start",
                ),
            )
            repository.insertBalanceAdjustmentRecord(
                BalanceAdjustmentRecord(
                    accountId = 1L,
                    delta = 800,
                    occurredAt = endExclusive,
                    createdAt = endExclusive,
                    updatedAt = endExclusive,
                    operationId = "period-adjustment-end",
                ),
            )
        }

        assertEquals(100L, roomRepo.sumCashInflowBetween(startInclusive, endExclusive))
        assertEquals(
            memoryRepo.queryActiveTransferRecordsBetween(startInclusive, endExclusive),
            roomRepo.queryActiveTransferRecordsBetween(startInclusive, endExclusive),
        )
        assertEquals(
            memoryRepo.queryBalanceUpdateRecordsBetween(startInclusive, endExclusive),
            roomRepo.queryBalanceUpdateRecordsBetween(startInclusive, endExclusive),
        )
        assertEquals(
            memoryRepo.queryBalanceAdjustmentRecordsBetween(startInclusive, endExclusive),
            roomRepo.queryBalanceAdjustmentRecordsBetween(startInclusive, endExclusive),
        )
        val filters = HistoryRecordFilters(dateStartAt = startInclusive, dateEndAt = endExclusive)
        assertEquals(
            memoryRepo.queryHistoryRecords(filters, cursor = null, limit = 20),
            roomRepo.queryHistoryRecords(filters, cursor = null, limit = 20),
        )
        assertTrue(roomRepo.queryHistoryRecords(filters, cursor = null, limit = 20).all {
            it.occurredAt == startInclusive
        })
    }

    @Test
    fun softDeletedLedgerRowsRemainStoredButDisappearFromActiveQueriesInBothImplementations() = runBlocking {
        seedAccount()
        db.accountDao().insert(
            com.shihuaidexianyu.money.data.entity.AccountEntity(
                id = 2,
                name = "目标账户",
                initialBalance = 0,
                createdAt = 1_000L,
                displayOrder = 2,
            ),
        )
        val startInclusive = 1_000L
        val endExclusive = 10_000L
        val deletedAt = 9_000L

        listOf(roomRepo, memoryRepo).forEach { repository ->
            val cashId = repository.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = 1L,
                    direction = CashFlowDirection.INFLOW.value,
                    amount = 100L,
                    note = "已删除现金",
                    occurredAt = 2_000L,
                    createdAt = 2_000L,
                    updatedAt = 2_000L,
                    operationId = "delete-contract-cash",
                ),
            ).recordId
            val transferId = repository.insertTransferRecord(
                TransferRecord(
                    fromAccountId = 1L,
                    toAccountId = 2L,
                    amount = 200L,
                    note = "已删除转账",
                    occurredAt = 3_000L,
                    createdAt = 3_000L,
                    updatedAt = 3_000L,
                    operationId = "delete-contract-transfer",
                ),
            ).recordId
            val updateId = repository.insertBalanceUpdateRecord(
                BalanceUpdateRecord(
                    accountId = 1L,
                    actualBalance = 300L,
                    systemBalanceBeforeUpdate = 0L,
                    delta = 300L,
                    occurredAt = 4_000L,
                    createdAt = 4_000L,
                    updatedAt = 4_000L,
                    operationId = "delete-contract-balance-update",
                ),
            ).recordId
            val adjustmentId = repository.insertBalanceAdjustmentRecord(
                BalanceAdjustmentRecord(
                    accountId = 1L,
                    delta = -50L,
                    occurredAt = 5_000L,
                    createdAt = 5_000L,
                    updatedAt = 5_000L,
                    operationId = "delete-contract-balance-adjustment",
                ),
            ).recordId

            repository.softDeleteCurrentCashFlowRecord(cashId, deletedAt)
            repository.softDeleteCurrentTransferRecord(transferId, deletedAt)
            repository.softDeleteCurrentBalanceUpdateRecord(updateId, deletedAt)
            repository.softDeleteCurrentBalanceAdjustmentRecord(adjustmentId, deletedAt)
        }

        val roomCash = roomRepo.queryAllCashFlowRecords()
        val memoryCash = memoryRepo.queryAllCashFlowRecords()
        assertEquals(memoryCash, roomCash)
        assertEquals("已删除现金", roomCash.single().note)
        assertEquals("delete-contract-cash", roomCash.single().operationId)
        assertEquals(deletedAt, roomCash.single().deletedAt)
        assertEquals(deletedAt, roomCash.single().updatedAt)

        val roomTransfers = roomRepo.queryAllTransferRecords()
        val memoryTransfers = memoryRepo.queryAllTransferRecords()
        assertEquals(memoryTransfers, roomTransfers)
        assertEquals("delete-contract-transfer", roomTransfers.single().operationId)
        assertEquals(deletedAt, roomTransfers.single().deletedAt)
        assertEquals(deletedAt, roomTransfers.single().updatedAt)

        val roomUpdates = roomRepo.queryAllBalanceUpdateRecords()
        val memoryUpdates = memoryRepo.queryAllBalanceUpdateRecords()
        assertEquals(memoryUpdates, roomUpdates)
        assertEquals("delete-contract-balance-update", roomUpdates.single().operationId)
        assertEquals(deletedAt, roomUpdates.single().deletedAt)
        assertEquals(deletedAt, roomUpdates.single().updatedAt)
        assertEquals("Soft deletion must not rewrite the stored reconciliation delta", 300L, roomUpdates.single().delta)

        val roomAdjustments = roomRepo.queryAllBalanceAdjustmentRecords()
        val memoryAdjustments = memoryRepo.queryAllBalanceAdjustmentRecords()
        assertEquals(memoryAdjustments, roomAdjustments)
        assertEquals("delete-contract-balance-adjustment", roomAdjustments.single().operationId)
        assertEquals(deletedAt, roomAdjustments.single().deletedAt)
        assertEquals(deletedAt, roomAdjustments.single().updatedAt)

        assertEquals(memoryRepo.queryAllActiveCashFlowRecords(), roomRepo.queryAllActiveCashFlowRecords())
        assertTrue(roomRepo.queryAllActiveCashFlowRecords().isEmpty())
        assertEquals(memoryRepo.queryAllActiveTransferRecords(), roomRepo.queryAllActiveTransferRecords())
        assertTrue(roomRepo.queryAllActiveTransferRecords().isEmpty())
        assertEquals(
            memoryRepo.queryBalanceUpdateRecordsBetween(startInclusive, endExclusive),
            roomRepo.queryBalanceUpdateRecordsBetween(startInclusive, endExclusive),
        )
        assertTrue(roomRepo.queryBalanceUpdateRecordsBetween(startInclusive, endExclusive).isEmpty())
        assertEquals(
            memoryRepo.queryBalanceAdjustmentRecordsBetween(startInclusive, endExclusive),
            roomRepo.queryBalanceAdjustmentRecordsBetween(startInclusive, endExclusive),
        )
        assertTrue(roomRepo.queryBalanceAdjustmentRecordsBetween(startInclusive, endExclusive).isEmpty())

        assertEquals(
            memoryRepo.sumInflowBetween(1L, startInclusive, endExclusive),
            roomRepo.sumInflowBetween(1L, startInclusive, endExclusive),
        )
        assertEquals(0L, roomRepo.sumInflowBetween(1L, startInclusive, endExclusive))
        assertEquals(
            memoryRepo.sumTransferOutBetween(1L, startInclusive, endExclusive),
            roomRepo.sumTransferOutBetween(1L, startInclusive, endExclusive),
        )
        assertEquals(0L, roomRepo.sumTransferOutBetween(1L, startInclusive, endExclusive))
        assertEquals(
            memoryRepo.sumTransferInBetween(2L, startInclusive, endExclusive),
            roomRepo.sumTransferInBetween(2L, startInclusive, endExclusive),
        )
        assertEquals(0L, roomRepo.sumTransferInBetween(2L, startInclusive, endExclusive))
        assertEquals(
            memoryRepo.sumAdjustmentBetween(1L, startInclusive, endExclusive),
            roomRepo.sumAdjustmentBetween(1L, startInclusive, endExclusive),
        )
        assertEquals(0L, roomRepo.sumAdjustmentBetween(1L, startInclusive, endExclusive))

        assertEquals(
            memoryRepo.sumCashInflowBetween(startInclusive, endExclusive),
            roomRepo.sumCashInflowBetween(startInclusive, endExclusive),
        )
        assertEquals(0L, roomRepo.sumCashInflowBetween(startInclusive, endExclusive))
        assertEquals(
            memoryRepo.sumBalanceUpdateIncreaseBetween(startInclusive, endExclusive),
            roomRepo.sumBalanceUpdateIncreaseBetween(startInclusive, endExclusive),
        )
        assertEquals(0L, roomRepo.sumBalanceUpdateIncreaseBetween(startInclusive, endExclusive))
        assertEquals(
            memoryRepo.sumManualAdjustmentDecreaseBetween(startInclusive, endExclusive),
            roomRepo.sumManualAdjustmentDecreaseBetween(startInclusive, endExclusive),
        )
        assertEquals(0L, roomRepo.sumManualAdjustmentDecreaseBetween(startInclusive, endExclusive))
        assertEquals(
            memoryRepo.queryPurposeTotals(CashFlowDirection.INFLOW.value, startInclusive, endExclusive),
            roomRepo.queryPurposeTotals(CashFlowDirection.INFLOW.value, startInclusive, endExclusive),
        )
        assertTrue(
            roomRepo.queryPurposeTotals(CashFlowDirection.INFLOW.value, startInclusive, endExclusive).isEmpty(),
        )
        assertEquals(
            memoryRepo.queryDailyCashFlowTotals(startInclusive, endExclusive, ZoneOffset.UTC.totalSeconds),
            roomRepo.queryDailyCashFlowTotals(startInclusive, endExclusive, ZoneOffset.UTC.totalSeconds),
        )
        assertTrue(
            roomRepo.queryDailyCashFlowTotals(startInclusive, endExclusive, ZoneOffset.UTC.totalSeconds).isEmpty(),
        )

        val filters = HistoryRecordFilters()
        assertEquals(
            memoryRepo.queryHistoryRecords(filters, cursor = null, limit = 20),
            roomRepo.queryHistoryRecords(filters, cursor = null, limit = 20),
        )
        assertTrue(roomRepo.queryHistoryRecords(filters, cursor = null, limit = 20).isEmpty())
        assertEquals(memoryRepo.countHistoryRecords(filters), roomRepo.countHistoryRecords(filters))
        assertEquals(0, roomRepo.countHistoryRecords(filters))
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

    private suspend fun assertConflictParity(
        expectedKind: LedgerRecordKind,
        insert: suspend (com.shihuaidexianyu.money.domain.repository.TransactionRepository) -> Unit,
    ) {
        val roomFailure = runCatching { insert(roomRepo) }.exceptionOrNull()
        val memoryFailure = runCatching { insert(memoryRepo) }.exceptionOrNull()
        assertTrue(roomFailure is LedgerOperationConflictException)
        assertTrue(memoryFailure is LedgerOperationConflictException)
        assertEquals(expectedKind, (roomFailure as LedgerOperationConflictException).kind)
        assertEquals(expectedKind, (memoryFailure as LedgerOperationConflictException).kind)
        assertEquals(roomFailure.operationId, memoryFailure.operationId)
        assertEquals(roomFailure.existingRecordId, memoryFailure.existingRecordId)
    }

    private suspend fun assertConcurrentConvergence(insert: suspend () -> LedgerInsertResult) {
        val results = coroutineScope {
            List(16) { async(Dispatchers.Default) { insert() } }.awaitAll()
        }
        assertEquals(1, results.map { it.recordId }.distinct().size)
        assertEquals(1, results.count { it.inserted })
    }

    private suspend fun seedAccountAndCashFlows() {
        seedAccount()
        val accountId = 1L
        val baseTime = 1_700_000_000_000L
        val samples = listOf(
            Triple("工资 100% bonus", 5_000L, CashFlowDirection.INFLOW.value),
            Triple("a_b 测试", 1_200L, CashFlowDirection.OUTFLOW.value),
            Triple("path\\receipt", 900L, CashFlowDirection.OUTFLOW.value),
            Triple("普通吃饭", 50_00L, CashFlowDirection.OUTFLOW.value),
            Triple("100% 报销", 3_400L, CashFlowDirection.INFLOW.value),
        )
        samples.forEachIndexed { index, (note, amount, direction) ->
            val occurredAt = baseTime + index * 60_000L
            roomRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = direction,
                    amount = amount,
                    note = note,
                    occurredAt = occurredAt,
                    createdAt = occurredAt,
                    updatedAt = occurredAt,
                    operationId = "history-cash-$index",
                ),
            )
            memoryRepo.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = direction,
                    amount = amount,
                    note = note,
                    occurredAt = occurredAt,
                    createdAt = occurredAt,
                    updatedAt = occurredAt,
                    operationId = "history-cash-$index",
                ),
            )
        }
    }
}
