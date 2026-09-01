package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemorySyncRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.sync.SyncChangeOperation
import com.shihuaidexianyu.money.domain.model.sync.SyncEntityKind
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.AccountLifecycleCoordinator
import com.shihuaidexianyu.money.domain.usecase.CloseAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.CreateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.ProcessDueReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.ReopenAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.RestoreLedgerRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.SetAccountHiddenUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.sync.AppendSyncChangesUseCase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Pins the sync change-log instrumentation (design 5.3): every ledger mutation family must append
 * exactly one change entry inside its own transaction, with monotonically increasing revisions
 * and mirror payloads matching the fixture conventions (amounts as minor-unit strings).
 */
class SyncChangeLogInstrumentationTest {
    private class Fixture {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val reminderSettings = InMemoryAccountReminderSettingsRepository()
        val reminders = InMemoryRecurringReminderRepository(MutableStateFlow(0L))
        val clock = MutableClock(1_000L)
        val syncRepository = InMemorySyncRepository(
            datasetIdGenerator = { "ds-test" },
            createdAtProvider = { 0L },
        )

        private val transactionPort = object : TransactionRepository by transactions {
            // The production Room runner is re-entrant; the in-memory one is not, so flatten.
            override suspend fun <T> runInTransaction(block: suspend () -> T): T = block()
        }
        // The helpers cast to LedgerAggregateRepository, which only the concrete in-memory repo is.
        private val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        val append = AppendSyncChangesUseCase(syncRepository, clock)

        val createAccount = CreateAccountUseCase(
            accountRepository = accounts,
            accountReminderSettingsRepository = reminderSettings,
            clockProvider = clock,
            transactionRunner = directTransactionRunner,
            appendSyncChangesUseCase = append,
        )
        val updateAccount = UpdateAccountUseCase(
            accountRepository = accounts,
            accountReminderSettingsRepository = reminderSettings,
            transactionRunner = directTransactionRunner,
            accountLifecycleCoordinator = AccountLifecycleCoordinator(),
            appendSyncChangesUseCase = append,
        )
        val setHidden = SetAccountHiddenUseCase(accounts, directTransactionRunner, append)
        val closeAccount = CloseAccountUseCase(
            accountRepository = accounts,
            reminderRepository = reminders,
            calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accounts, transactions, clock),
            transactionRunner = directTransactionRunner,
            clockProvider = clock,
            accountLifecycleCoordinator = AccountLifecycleCoordinator(),
            accountReminderSettingsRepository = reminderSettings,
            appendSyncChangesUseCase = append,
        )
        val reopenAccount = ReopenAccountUseCase(accounts, directTransactionRunner, append)
        val reorder = UpdateAccountDisplayOrderUseCase(accounts, directTransactionRunner, append)

        val createCashFlow = CreateCashFlowRecordUseCase(accounts, transactionPort, refresh, clock, append)
        val updateCashFlow = UpdateCashFlowRecordUseCase(accounts, transactionPort, refresh, clock, append)
        val deleteCashFlow = DeleteCashFlowRecordUseCase(accounts, transactionPort, refresh, clock, append)
        val createTransfer = CreateTransferRecordUseCase(accounts, transactionPort, refresh, clock, append)
        val updateTransfer = UpdateTransferRecordUseCase(accounts, transactionPort, refresh, clock, append)
        val deleteTransfer = DeleteTransferRecordUseCase(accounts, transactionPort, refresh, clock, append)
        val updateBalance = UpdateBalanceUseCase(
            accountRepository = accounts,
            transactionRepository = transactionPort,
            resolveBalanceUpdateContextUseCase = ResolveBalanceUpdateContextUseCase(accounts, transactions),
            refreshAccountActivityStateUseCase = refresh,
            clockProvider = clock,
            appendSyncChangesUseCase = append,
        )
        val deleteBalanceUpdate = DeleteBalanceUpdateRecordUseCase(
            accountRepository = accounts,
            transactionRepository = transactionPort,
            refreshAccountActivityStateUseCase = refresh,
            clockProvider = clock,
            appendSyncChangesUseCase = append,
        )
        val createBalanceAdjustment = CreateBalanceAdjustmentUseCase(
            accounts, transactionPort, refresh, clock, append,
        )
        val updateBalanceAdjustment = UpdateBalanceAdjustmentUseCase(
            accounts, transactionPort, refresh, clock, append,
        )
        val deleteBalanceAdjustment = DeleteBalanceAdjustmentUseCase(
            accounts, transactionPort, refresh, clock, append,
        )
        val restoreRecord = RestoreLedgerRecordUseCase(accounts, transactionPort, refresh, clock, appendSyncChangesUseCase = append)
        val processDueReminder = ProcessDueReminderUseCase(
            accountRepository = accounts,
            transactionRepository = transactionPort,
            reminderRepository = reminders,
            refreshAccountActivityStateUseCase = refresh,
            clockProvider = clock,
            zoneIdProvider = testZoneIdProvider(),
            appendSyncChangesUseCase = append,
        )

        suspend fun newAccount(name: String = "现金"): Long =
            accounts.createAccount(Account(name = name, initialBalance = 0, createdAt = clock.now))
    }

    private class MutableClock(var now: Long) : ClockProvider {
        override fun nowMillis(): Long = now
    }

    @Test
    fun cashFlowCreateEditDeleteRestoreEachAppendOneChange() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.newAccount()

        val created = fixture.createCashFlow(
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 8650,
            note = "聚餐",
            occurredAt = 10,
            operationId = "op-cf-create",
        )
        val stored = assertNotNull(fixture.transactions.queryStoredCashFlowRecordById(created.recordId))
        fixture.updateCashFlow(
            recordId = created.recordId,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            amount = 8650,
            note = "聚餐改",
            occurredAt = 10,
            expectedUpdatedAt = stored.updatedAt,
        )
        val updated = assertNotNull(fixture.transactions.queryStoredCashFlowRecordById(created.recordId))
        val token = assertNotNull(fixture.deleteCashFlow(created.recordId, updated.updatedAt))
        fixture.restoreRecord(token)

        val changes = fixture.syncRepository.queryChangesAfter(0, 100)
        assertEquals(4, changes.size)
        assertEquals(listOf(1L, 2L, 3L, 4L), changes.map { it.revision })
        assertEquals(
            listOf(
                SyncChangeOperation.UPSERT,
                SyncChangeOperation.UPSERT,
                SyncChangeOperation.DELETE,
                SyncChangeOperation.UPSERT,
            ),
            changes.map { it.operation },
        )
        assertTrue(changes.all { it.entityKind == SyncEntityKind.CASH_FLOW && it.recordId == created.recordId })

        val createPayload = Json.parseToJsonElement(assertNotNull(changes[0].payloadJson)).jsonObject
        assertEquals("8650", createPayload.getValue("amount").jsonPrimitive.content)
        assertEquals("聚餐", createPayload.getValue("note").jsonPrimitive.content)
        assertEquals("op-cf-create", createPayload.getValue("operationId").jsonPrimitive.content)
        assertNull(createPayload["deletedAt"])

        val tombstone = changes[2]
        assertNull(tombstone.payloadJson)
        assertNotNull(tombstone.deletedAt)
        assertEquals(tombstone.deletedAt, tombstone.updatedAt)

        // Restore is an upsert again whose payload no longer carries deletedAt.
        val restoredPayload = Json.parseToJsonElement(assertNotNull(changes[3].payloadJson)).jsonObject
        assertNull(restoredPayload["deletedAt"])
    }

    @Test
    fun transferCreateEditDeleteAppendChanges() = runBlocking {
        val fixture = Fixture()
        val from = fixture.newAccount("招行")
        val to = fixture.newAccount("现金")

        val created = fixture.createTransfer(from, to, 200000, "还信用卡", 10, "op-tf-create")
        val stored = assertNotNull(fixture.transactions.queryStoredTransferRecordById(created.recordId))
        fixture.updateTransfer(
            recordId = created.recordId,
            fromAccountId = from,
            toAccountId = to,
            amount = 200000,
            note = "还招行信用卡",
            occurredAt = 10,
            expectedUpdatedAt = stored.updatedAt,
        )
        val updated = assertNotNull(fixture.transactions.queryStoredTransferRecordById(created.recordId))
        fixture.deleteTransfer(created.recordId, updated.updatedAt)

        val changes = fixture.syncRepository.queryChangesAfter(0, 100)
        assertEquals(3, changes.size)
        assertTrue(changes.all { it.entityKind == SyncEntityKind.TRANSFER })
        assertEquals(SyncChangeOperation.DELETE, changes.last().operation)
        val payload = Json.parseToJsonElement(assertNotNull(changes[1].payloadJson)).jsonObject
        assertEquals("200000", payload.getValue("amount").jsonPrimitive.content)
        assertEquals("还招行信用卡", payload.getValue("note").jsonPrimitive.content)
    }

    @Test
    fun balanceUpdateCreateAndDeleteAppendChanges() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.newAccount()

        val result = fixture.updateBalance(accountId, actualBalance = 152300, occurredAt = 10, operationId = "op-bu-1")
        val recordId = result.insertResult.recordId
        val stored = assertNotNull(fixture.transactions.queryStoredBalanceUpdateRecordById(recordId))
        fixture.deleteBalanceUpdate(recordId, stored.updatedAt)

        val changes = fixture.syncRepository.queryChangesAfter(0, 100)
        assertEquals(2, changes.size)
        assertEquals(SyncEntityKind.BALANCE_UPDATE, changes[0].entityKind)
        val payload = Json.parseToJsonElement(assertNotNull(changes[0].payloadJson)).jsonObject
        assertEquals("152300", payload.getValue("actualBalance").jsonPrimitive.content)
        // Fresh account with no prior records: system balance 0, so delta equals actualBalance.
        assertEquals("0", payload.getValue("systemBalanceBeforeUpdate").jsonPrimitive.content)
        assertEquals("152300", payload.getValue("delta").jsonPrimitive.content)
        assertNull(changes[1].payloadJson)
        assertEquals(SyncChangeOperation.DELETE, changes[1].operation)
    }

    @Test
    fun balanceAdjustmentCreateEditDeleteAppendChanges() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.newAccount()

        val created = fixture.createBalanceAdjustment(accountId, delta = 500, occurredAt = 10, operationId = "op-ba-1")
        fixture.updateBalanceAdjustment(created.recordId, delta = 600, occurredAt = 10)
        val updated = assertNotNull(fixture.transactions.queryStoredBalanceAdjustmentRecordById(created.recordId))
        fixture.deleteBalanceAdjustment(created.recordId, updated.updatedAt)

        val changes = fixture.syncRepository.queryChangesAfter(0, 100)
        assertEquals(3, changes.size)
        assertTrue(changes.all { it.entityKind == SyncEntityKind.BALANCE_ADJUSTMENT })
        assertEquals(
            listOf(
                SyncChangeOperation.UPSERT,
                SyncChangeOperation.UPSERT,
                SyncChangeOperation.DELETE,
            ),
            changes.map { it.operation },
        )
        val payload = Json.parseToJsonElement(assertNotNull(changes[0].payloadJson)).jsonObject
        assertEquals("500", payload.getValue("delta").jsonPrimitive.content)
    }

    @Test
    fun accountLifecycleMutationsAppendAccountUpserts() = runBlocking {
        val fixture = Fixture()

        val accountId = fixture.createAccount("现金", initialBalance = 0)
        fixture.updateAccount(accountId, name = "现金改")
        fixture.setHidden(accountId, hidden = true)
        fixture.setHidden(accountId, hidden = false)
        fixture.closeAccount(accountId)
        fixture.reopenAccount(accountId)
        fixture.reorder(listOf(accountId))

        val changes = fixture.syncRepository.queryChangesAfter(0, 100)
        // create + edit + hide + unhide + close + reopen; reorder no-ops (single account keeps
        // displayOrder 0).
        assertEquals(6, changes.size)
        assertTrue(changes.all { it.entityKind == SyncEntityKind.ACCOUNT && it.recordId == accountId })
        assertTrue(changes.all { it.operation == SyncChangeOperation.UPSERT })
        assertEquals((1L..6L).toList(), changes.map { it.revision })
        val firstPayload = Json.parseToJsonElement(assertNotNull(changes.first().payloadJson)).jsonObject
        assertEquals("现金", firstPayload.getValue("name").jsonPrimitive.content)
        assertEquals("funding", firstPayload.getValue("kind").jsonPrimitive.content)
    }

    @Test
    fun reminderProcessingAppendsCashFlowChange() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.newAccount()
        val reminderId = fixture.reminders.insertReminder(
            RecurringReminder(
                name = "房租",
                type = "fixed_expense",
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 300000,
                periodType = "monthly",
                periodValue = 1,
                periodMonth = null,
                isEnabled = true,
                nextDueAt = 500,
                anchorDueAt = 500,
                createdAt = 0,
                updatedAt = 0,
            ),
        )

        fixture.clock.now = 1_000
        val recordId = fixture.processDueReminder(
            reminderId = reminderId,
            expectedDueAt = 500,
            accountId = accountId,
            direction = CashFlowDirection.OUTFLOW,
            occurredAt = 500,
            amount = 300000,
            note = "房租",
        )

        val changes = fixture.syncRepository.queryChangesAfter(0, 100)
        assertEquals(1, changes.size)
        assertEquals(SyncEntityKind.CASH_FLOW, changes.single().entityKind)
        assertEquals(recordId, changes.single().recordId)
        val payload = Json.parseToJsonElement(assertNotNull(changes.single().payloadJson)).jsonObject
        assertEquals("cash:reminder:$reminderId:500", payload.getValue("operationId").jsonPrimitive.content)
    }
}
