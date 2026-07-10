package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerRecordChangedException
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.model.RestoreLedgerResult
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceAdjustmentUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteCashFlowRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteTransferRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.RestoreLedgerRecordUseCase
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerDeleteRestoreUseCaseTest {
    @Test
    fun transferDeleteAndRestoreRefreshBothAffectedAccounts() = runBlocking {
        val fixture = Fixture(now = 100)
        val sourceId = fixture.createAccount("来源")
        val targetId = fixture.createAccount("目标")
        val transferId = fixture.transactions.insertTransferRecord(transfer(sourceId, targetId)).recordId

        val token = assertNotNull(fixture.deleteTransfer(transferId))
        assertEquals(1, fixture.accounts.getAccountById(sourceId)?.lastUsedAt)
        assertEquals(1, fixture.accounts.getAccountById(targetId)?.lastUsedAt)

        fixture.clock.now = 200
        assertEquals(RestoreLedgerResult.RESTORED, fixture.restore(token))
        assertEquals(11, fixture.accounts.getAccountById(sourceId)?.lastUsedAt)
        assertEquals(11, fixture.accounts.getAccountById(targetId)?.lastUsedAt)
    }

    @Test
    fun staleDeleteCasThrowsAndRollsBackTheFailedTransaction() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val accountId = accounts.createAccount(
            Account(name = "现金", initialBalance = 0, createdAt = 1, lastUsedAt = 1),
        )
        val delegate = InMemoryTransactionRepository()
        val recordId = delegate.insertCashFlowRecord(cash(accountId)).recordId
        var injected = false
        val racingRepository = object : TransactionRepository by delegate {
            override suspend fun queryStoredCashFlowRecordById(id: Long): CashFlowRecord? {
                val stored = delegate.queryStoredCashFlowRecordById(id)
                if (!injected && stored != null) {
                    injected = true
                    check(
                        delegate.updateCashFlowRecord(
                            stored.copy(note = "并发修改", updatedAt = stored.updatedAt + 1),
                            stored.updatedAt,
                        ),
                    )
                }
                return stored
            }
        }
        val delete = DeleteCashFlowRecordUseCase(
            accounts,
            racingRepository,
            RefreshAccountActivityStateUseCase(accounts, delegate),
            testClockProvider(100),
        )

        assertFailsWith<LedgerRecordChangedException> { delete(recordId) }
        val raced = assertNotNull(delegate.queryCashFlowRecordById(recordId))
        assertEquals("工资", raced.note)
        assertNull(raced.deletedAt)
    }

    @Test
    fun allFourDeletesReturnTokensAndRestoresPreservePayloadAndDerivedViews() = runBlocking {
        val fixture = Fixture(now = 100)
        val sourceId = fixture.createAccount("来源")
        val targetId = fixture.createAccount("目标")
        val cashId = fixture.transactions.insertCashFlowRecord(cash(sourceId)).recordId
        val transferId = fixture.transactions.insertTransferRecord(transfer(sourceId, targetId)).recordId
        val updateId = fixture.transactions.insertBalanceUpdateRecord(balanceUpdate(sourceId)).recordId
        val adjustmentId = fixture.transactions.insertBalanceAdjustmentRecord(adjustment(sourceId)).recordId
        val originals = listOf(
            fixture.transactions.queryStoredCashFlowRecordById(cashId),
            fixture.transactions.queryStoredTransferRecordById(transferId),
            fixture.transactions.queryStoredBalanceUpdateRecordById(updateId),
            fixture.transactions.queryStoredBalanceAdjustmentRecordById(adjustmentId),
        )

        val tokens = listOf(
            assertNotNull(fixture.deleteCash(cashId)),
            assertNotNull(fixture.deleteTransfer(transferId)),
            assertNotNull(fixture.deleteBalanceUpdate(updateId)),
            assertNotNull(fixture.deleteAdjustment(adjustmentId)),
        )

        assertEquals(
            listOf(
                LedgerRecordKind.CASH_FLOW,
                LedgerRecordKind.TRANSFER,
                LedgerRecordKind.BALANCE_UPDATE,
                LedgerRecordKind.BALANCE_ADJUSTMENT,
            ),
            tokens.map { it.kind },
        )
        assertEquals(listOf(cashId, transferId, updateId, adjustmentId), tokens.map { it.recordId })
        assertTrue(tokens.all { it.deletedAt == 100L && it.version == 1 })
        assertNull(fixture.transactions.queryCashFlowRecordById(cashId))
        assertNull(fixture.transactions.queryTransferRecordById(transferId))
        assertNull(fixture.transactions.getBalanceUpdateRecordById(updateId))
        assertNull(fixture.transactions.getBalanceAdjustmentRecordById(adjustmentId))
        assertEquals(0, fixture.transactions.countHistoryRecords(HistoryRecordFilters()))
        assertEquals(0, fixture.balance(sourceId))
        assertEquals(0, fixture.balance(targetId))
        assertEquals(0, fixture.transactions.sumCashInflowBetween(0, 1_000))
        assertEquals(0, fixture.transactions.countActiveTransferRecordsBetween(0, 1_000))

        fixture.clock.now = 200
        tokens.forEach { token ->
            assertEquals(RestoreLedgerResult.RESTORED, fixture.restore(token))
            assertEquals(RestoreLedgerResult.ALREADY_ACTIVE, fixture.restore(token))
        }

        assertEquals(4, fixture.transactions.countHistoryRecords(HistoryRecordFilters()))
        assertEquals(60, fixture.balance(sourceId))
        assertEquals(50, fixture.balance(targetId))
        assertEquals(
            originals[0],
            fixture.transactions.queryStoredCashFlowRecordById(cashId)?.copy(updatedAt = 10, deletedAt = null),
        )
        assertEquals(
            originals[1],
            fixture.transactions.queryStoredTransferRecordById(transferId)?.copy(updatedAt = 11, deletedAt = null),
        )
        assertEquals(
            originals[2],
            fixture.transactions.queryStoredBalanceUpdateRecordById(updateId)?.copy(updatedAt = 12, deletedAt = null),
        )
        assertEquals(
            originals[3],
            fixture.transactions.queryStoredBalanceAdjustmentRecordById(adjustmentId)?.copy(updatedAt = 13, deletedAt = null),
        )
    }

    @Test
    fun repeatedDeleteIsNullAndOldTokenCannotRestoreSecondTombstone() = runBlocking {
        val fixture = Fixture(now = 100)
        val accountId = fixture.createAccount("现金")
        val recordId = fixture.transactions.insertCashFlowRecord(cash(accountId)).recordId
        val first = assertNotNull(fixture.deleteCash(recordId))
        assertNull(fixture.deleteCash(recordId))

        fixture.clock.now = 200
        assertEquals(RestoreLedgerResult.RESTORED, fixture.restore(first))
        fixture.clock.now = 300
        val second = assertNotNull(fixture.deleteCash(recordId))

        assertEquals(300, second.deletedAt)
        assertEquals(RestoreLedgerResult.STALE, fixture.restore(first))
        assertEquals(RestoreLedgerResult.RESTORED, fixture.restore(second))
    }

    @Test
    fun wrongIdentityBoundaryAndMissingTokensAreClassifiedWithoutMutation() = runBlocking {
        val fixture = Fixture(now = 100)
        val accountId = fixture.createAccount("现金")
        val recordId = fixture.transactions.insertCashFlowRecord(cash(accountId)).recordId
        val token = assertNotNull(fixture.deleteCash(recordId))

        assertEquals(RestoreLedgerResult.STALE, fixture.restore(token.copy(version = 2)))
        assertEquals(RestoreLedgerResult.STALE, fixture.restore(token.copy(operationId = "wrong")))
        assertEquals(RestoreLedgerResult.STALE, fixture.restore(token.copy(recordId = 999)))
        assertEquals(RestoreLedgerResult.STALE, fixture.restore(token.copy(kind = LedgerRecordKind.TRANSFER)))
        assertEquals(RestoreLedgerResult.STALE, fixture.restore(token.copy(deletedAt = 99)))
        assertEquals(
            RestoreLedgerResult.NOT_FOUND,
            fixture.restore(
                LedgerUndoToken(
                    kind = LedgerRecordKind.CASH_FLOW,
                    recordId = 999,
                    operationId = "never-existed",
                    deletedAt = 1,
                ),
            ),
        )
        assertNotNull(fixture.transactions.queryStoredCashFlowRecordById(recordId)?.deletedAt)
        Unit
    }

    @Test
    fun restoreRejectsAnyClosedAffectedAccountAndTransferChecksBothAccounts() = runBlocking {
        val fixture = Fixture(now = 100)
        val sourceId = fixture.createAccount("来源")
        val targetId = fixture.createAccount("目标")
        val transferId = fixture.transactions.insertTransferRecord(transfer(sourceId, targetId)).recordId
        val token = assertNotNull(fixture.deleteTransfer(transferId))
        fixture.accounts.closeAccount(targetId, 150)
        fixture.clock.now = 200

        val error = assertFailsWith<IllegalArgumentException> { fixture.restore(token) }

        assertEquals("关闭账户不能恢复转账记录", error.message)
        assertNotNull(fixture.transactions.queryStoredTransferRecordById(transferId)?.deletedAt)
        Unit
    }

    @Test
    fun balanceUpdateRestorePreservesEvidenceFixedDeltaAndLaterReconciliation() = runBlocking {
        val fixture = Fixture(now = 100)
        val accountId = fixture.createAccount("现金")
        val firstId = fixture.transactions.insertBalanceUpdateRecord(balanceUpdate(accountId)).recordId
        val later = balanceUpdate(accountId).copy(
            actualBalance = 999,
            systemBalanceBeforeUpdate = 992,
            delta = 7,
            occurredAt = 20,
            createdAt = 20,
            updatedAt = 20,
            operationId = "balance-update-later",
        )
        val laterId = fixture.transactions.insertBalanceUpdateRecord(later).recordId
        val token = assertNotNull(fixture.deleteBalanceUpdate(firstId))
        fixture.clock.now = 200

        assertEquals(RestoreLedgerResult.RESTORED, fixture.restore(token))

        val restored = assertNotNull(fixture.transactions.getBalanceUpdateRecordById(firstId))
        assertEquals(500, restored.actualBalance)
        assertEquals(480, restored.systemBalanceBeforeUpdate)
        assertEquals(20, restored.delta)
        assertEquals(later.copy(id = laterId), fixture.transactions.getBalanceUpdateRecordById(laterId))
        assertEquals(27, fixture.balance(accountId))
    }

    @Test
    fun deleteTimestampOverflowThrowsWithoutTombstoning() = runBlocking {
        val fixture = Fixture(now = 100)
        val accountId = fixture.createAccount("现金")
        val recordId = fixture.transactions.insertCashFlowRecord(
            cash(accountId).copy(updatedAt = Long.MAX_VALUE),
        ).recordId

        assertFailsWith<IllegalStateException> { fixture.deleteCash(recordId) }
        assertNotNull(fixture.transactions.queryCashFlowRecordById(recordId))
        Unit
    }

    private class Fixture(now: Long) {
        val accounts = InMemoryAccountRepository()
        val transactions = InMemoryTransactionRepository()
        val clock = MutableClock(now)
        private val refresh = RefreshAccountActivityStateUseCase(accounts, transactions)
        private val calculateBalance = CalculateCurrentBalanceUseCase(accounts, transactions, clock)
        private val deleteCash = DeleteCashFlowRecordUseCase(accounts, transactions, refresh, clock)
        private val deleteTransfer = DeleteTransferRecordUseCase(accounts, transactions, refresh, clock)
        private val deleteUpdate = DeleteBalanceUpdateRecordUseCase(accounts, transactions, refresh, clock)
        private val deleteAdjustment = DeleteBalanceAdjustmentUseCase(accounts, transactions, refresh, clock)
        private val restore = RestoreLedgerRecordUseCase(accounts, transactions, refresh, clock)

        suspend fun createAccount(name: String): Long = accounts.createAccount(
            Account(name = name, initialBalance = 0, createdAt = 1, lastUsedAt = 1),
        )

        suspend fun deleteCash(recordId: Long) = deleteCash.invoke(recordId)
        suspend fun deleteTransfer(recordId: Long) = deleteTransfer.invoke(recordId)
        suspend fun deleteBalanceUpdate(recordId: Long) = deleteUpdate.invoke(recordId)
        suspend fun deleteAdjustment(recordId: Long) = deleteAdjustment.invoke(recordId)
        suspend fun restore(token: LedgerUndoToken) = restore.invoke(token)
        suspend fun balance(accountId: Long) = calculateBalance(accountId, clock.now)
    }

    private class MutableClock(var now: Long) : ClockProvider {
        override fun nowMillis(): Long = now
    }

    private fun cash(accountId: Long) = CashFlowRecord(
        accountId = accountId,
        direction = CashFlowDirection.INFLOW.value,
        amount = 100,
        note = "工资",
        occurredAt = 10,
        createdAt = 10,
        updatedAt = 10,
        operationId = "cash-flow",
    )

    private fun transfer(sourceId: Long, targetId: Long) = TransferRecord(
        fromAccountId = sourceId,
        toAccountId = targetId,
        amount = 50,
        note = "转账",
        occurredAt = 11,
        createdAt = 11,
        updatedAt = 11,
        operationId = "transfer",
    )

    private fun balanceUpdate(accountId: Long) = BalanceUpdateRecord(
        accountId = accountId,
        actualBalance = 500,
        systemBalanceBeforeUpdate = 480,
        delta = 20,
        occurredAt = 12,
        createdAt = 12,
        updatedAt = 12,
        operationId = "balance-update",
    )

    private fun adjustment(accountId: Long) = BalanceAdjustmentRecord(
        accountId = accountId,
        delta = -10,
        occurredAt = 13,
        createdAt = 13,
        updatedAt = 13,
        operationId = "adjustment",
    )
}
