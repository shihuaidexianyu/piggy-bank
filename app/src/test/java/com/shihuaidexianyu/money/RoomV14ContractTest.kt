package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_MIGRATIONS
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_VERSION
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.usecase.LedgerBalanceCalculator
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test

class RoomV14ContractTest {
    @Test
    fun `database exposes the single 13 to 14 migration`() {
        assertEquals(14, MONEY_DATABASE_VERSION)
        val migration = MONEY_DATABASE_MIGRATIONS.last()
        assertEquals(13, migration.startVersion)
        assertEquals(14, migration.endVersion)
    }

    @Test
    fun `v14 domain models expose lifecycle and operation metadata`() {
        assertProperties(
            className = "com.shihuaidexianyu.money.domain.model.Account",
            required = setOf("isHidden", "closedAt", "isClosed"),
            removed = setOf("isArchived", "archivedAt"),
        )
        listOf(
            "CashFlowRecord",
            "TransferRecord",
            "BalanceUpdateRecord",
            "BalanceAdjustmentRecord",
        ).forEach { simpleName ->
            assertProperties(
                className = "com.shihuaidexianyu.money.domain.model.$simpleName",
                required = setOf("createdAt", "updatedAt", "deletedAt", "operationId"),
                removed = setOf("isDeleted"),
            )
        }
        assertProperties(
            className = "com.shihuaidexianyu.money.domain.model.CashFlowRecord",
            required = setOf("note"),
            removed = setOf("purpose"),
        )
        assertProperties(
            className = "com.shihuaidexianyu.money.domain.model.RecurringReminder",
            required = setOf("anchorDueAt", "lastNotifiedDueAt"),
        )
        assertProperties(
            className = "com.shihuaidexianyu.money.domain.model.SavingsGoal",
            required = setOf("updatedAt"),
        )
    }

    @Test
    fun `v14 registers portable settings reminder config and migration state entities`() {
        listOf(
            "PortableSettingsEntity",
            "AccountReminderConfigEntity",
            "LocalMigrationStateEntity",
        ).forEach { simpleName ->
            assertNotNull(
                runCatching {
                    Class.forName("com.shihuaidexianyu.money.data.entity.$simpleName")
                }.getOrNull(),
                "$simpleName must exist in the v14 Room model",
            )
        }
    }

    @Test
    fun `all four delete operations retain stored rows but remove them from active queries`() = runBlocking {
        val repository = InMemoryTransactionRepository()
        val cashId = repository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1,
                direction = "inflow",
                amount = 100,
                note = "cash",
                occurredAt = 10,
                createdAt = 10,
                updatedAt = 10,
                operationId = "cash-test",
            ),
        ).recordId
        val transferId = repository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 1,
                toAccountId = 2,
                amount = 200,
                note = "transfer",
                occurredAt = 20,
                createdAt = 20,
                updatedAt = 20,
                operationId = "transfer-test",
            ),
        ).recordId
        val updateId = repository.insertBalanceUpdateRecord(
            BalanceUpdateRecord(
                accountId = 1,
                actualBalance = 300,
                systemBalanceBeforeUpdate = 0,
                delta = 300,
                occurredAt = 30,
                createdAt = 30,
                updatedAt = 30,
                operationId = "update-test",
            ),
        ).recordId
        val adjustmentId = repository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = 1,
                delta = -50,
                occurredAt = 40,
                createdAt = 40,
                updatedAt = 40,
                operationId = "adjustment-test",
            ),
        ).recordId

        assertEquals(4, repository.countHistoryRecords(HistoryRecordFilters()))
        assertEquals(100, repository.sumCashInflowBetween(0, 1_000))
        assertEquals(300, repository.sumBalanceUpdateIncreaseBetween(0, 1_000))
        assertEquals(50, repository.sumManualAdjustmentDecreaseBetween(0, 1_000))
        assertEquals(150, repository.ledgerBalanceAt100())

        repository.softDeleteCurrentCashFlowRecord(cashId, 100)
        repository.softDeleteCurrentTransferRecord(transferId, 100)
        repository.softDeleteCurrentBalanceUpdateRecord(updateId, 100)
        repository.softDeleteCurrentBalanceAdjustmentRecord(adjustmentId, 100)

        assertEquals(1, repository.queryAllCashFlowRecords().size)
        assertEquals(1, repository.queryAllTransferRecords().size)
        assertEquals(1, repository.queryAllBalanceUpdateRecords().size)
        assertEquals(1, repository.queryAllBalanceAdjustmentRecords().size)
        assertTrue(repository.queryAllActiveCashFlowRecords().isEmpty())
        assertTrue(repository.queryAllActiveTransferRecords().isEmpty())
        assertNull(repository.getBalanceUpdateRecordById(updateId))
        assertNull(repository.getBalanceAdjustmentRecordById(adjustmentId))
        assertEquals(0, repository.countHistoryRecords(HistoryRecordFilters()))
        assertEquals(0, repository.sumCashInflowBetween(0, 1_000))
        assertEquals(0, repository.sumBalanceUpdateIncreaseBetween(0, 1_000))
        assertEquals(0, repository.sumManualAdjustmentDecreaseBetween(0, 1_000))
        assertEquals(0, repository.ledgerBalanceAt100())

        repository.queryAllCashFlowRecords().single().assertDeletedAt(100)
        repository.queryAllTransferRecords().single().assertDeletedAt(100)
        repository.queryAllBalanceUpdateRecords().single().assertDeletedAt(100)
        repository.queryAllBalanceAdjustmentRecords().single().assertDeletedAt(100)
        assertEquals(300, repository.queryAllBalanceUpdateRecords().single().delta)
    }

    private fun assertProperties(
        className: String,
        required: Set<String>,
        removed: Set<String> = emptySet(),
    ) {
        val names = Class.forName(className).methods
            .mapNotNull { method ->
                method.name.removePrefix("get").takeIf { method.name.startsWith("get") }
                    ?.replaceFirstChar(Char::lowercase)
                    ?: method.name.takeIf { method.name.startsWith("is") }
                        ?.replaceFirstChar(Char::lowercase)
            }
            .toSet()
        required.forEach { property -> assertTrue(property in names, "$className must expose $property") }
        removed.forEach { property -> assertTrue(property !in names, "$className must remove $property") }
    }

    private fun Any.assertDeletedAt(expected: Long) {
        val getter = javaClass.methods.firstOrNull { it.name == "getDeletedAt" }
        assertNotNull(getter, "${javaClass.simpleName} must expose deletedAt")
        assertEquals(expected, getter.invoke(this))
    }

    private suspend fun InMemoryTransactionRepository.ledgerBalanceAt100(): Long {
        val account = Account(id = 1, name = "test", initialBalance = 0, createdAt = 0)
        return LedgerBalanceCalculator.balanceAt(
            account = account,
            atTimeMillis = 100,
            deltas = LedgerBalanceCalculator.deltasFromRecords(
                account = account,
                cashFlows = queryAllCashFlowRecords(),
                transfers = queryAllTransferRecords(),
                balanceUpdates = queryAllBalanceUpdateRecords(),
                adjustments = queryAllBalanceAdjustmentRecords(),
                atTimeMillis = 100,
            ),
        )
    }
}
