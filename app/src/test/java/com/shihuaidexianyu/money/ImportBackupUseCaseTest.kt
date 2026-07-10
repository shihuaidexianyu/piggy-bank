package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.domain.model.backup.BackupAccount
import com.shihuaidexianyu.money.domain.model.backup.BackupAccountReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupCashFlowRecord
import com.shihuaidexianyu.money.domain.model.backup.BackupTransferRecord
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.usecase.MAX_BACKUP_LEDGER_RECORDS
import com.shihuaidexianyu.money.domain.usecase.ValidateBackupSnapshotUseCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.junit.Test

class ValidateBackupSnapshotUseCaseTest {
    private val validator = ValidateBackupSnapshotUseCase { 2_000_000_000_000L }

    @Test
    fun `valid snapshot returns complete counts`() {
        val snapshot = validSnapshot()

        val result = validator(snapshot)

        assertEquals(1, result.cashFlowCount)
        assertEquals(1, result.transferCount)
        assertEquals(1, result.balanceUpdateCount)
        assertEquals(1, result.balanceAdjustmentCount)
        assertEquals(1, result.savingsGoalCount)
    }

    @Test
    fun `invalid FK and self transfer are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            validator(validSnapshot().copy(cashFlowRecords = validSnapshot().cashFlowRecords.map { it.copy(accountId = 99L) }))
        }
        assertFailsWith<IllegalArgumentException> {
            validator(validSnapshot().copy(transferRecords = validSnapshot().transferRecords.map { it.copy(toAccountId = it.fromAccountId) }))
        }
    }

    @Test
    fun `duplicate ids and operation ids are rejected with paths`() {
        val valid = validSnapshot()
        assertFailureContains("accounts.id") {
            validator(valid.copy(accounts = valid.accounts + valid.accounts.first()))
        }
        assertFailureContains("cashFlowRecords.operationId") {
            validator(
                valid.copy(cashFlowRecords = valid.cashFlowRecords + valid.cashFlowRecords.single().copy(id = 99L)),
            )
        }
    }

    @Test
    fun `invalid amount schedule lifecycle and reconciliation evidence are rejected`() {
        val valid = validSnapshot()
        assertFailureContains("amount") {
            validator(valid.copy(cashFlowRecords = valid.cashFlowRecords.map { it.copy(amount = 0L) }))
        }
        assertFailureContains("间隔天数") {
            validator(
                valid.copy(recurringReminders = valid.recurringReminders.map { it.copy(periodValue = 0) }),
            )
        }
        assertFailureContains("晚于账户关闭时间") {
            validator(
                valid.copy(
                    accounts = valid.accounts.map {
                        if (it.id == 1L) {
                            it.copy(closedAt = 450L, lastUsedAt = null, lastBalanceUpdateAt = null)
                        } else {
                            it
                        }
                    },
                    recurringReminders = valid.recurringReminders.map { it.copy(isEnabled = false) },
                ),
            )
        }
        assertFailureContains("核对证据") {
            validator(
                valid.copy(balanceUpdateRecords = valid.balanceUpdateRecords.map { it.copy(delta = 49L) }),
            )
        }
    }

    @Test
    fun `aggregate overflow and record limit are rejected before write`() {
        val valid = validSnapshot()
        assertFailureContains("算术溢出") {
            validator(
                valid.copy(accounts = valid.accounts.map { if (it.id == 1L) it.copy(initialBalance = Long.MAX_VALUE) else it }),
            )
        }
        assertFailureContains("数量超过") {
            validator(
                valid.copy(cashFlowRecords = List(MAX_BACKUP_LEDGER_RECORDS + 1) { valid.cashFlowRecords.single() }),
            )
        }
    }

    @Test
    fun `historical prefix overflow is rejected in occurred time order`() {
        val valid = validSnapshot()
        val overflow = valid.copy(
            accounts = valid.accounts.map { if (it.id == 1L) it.copy(initialBalance = 0L) else it },
            cashFlowRecords = listOf(
                BackupCashFlowRecord(
                    id = 10L,
                    accountId = 1L,
                    direction = "inflow",
                    amount = Long.MAX_VALUE,
                    note = "",
                    occurredAt = 200L,
                    createdAt = 200L,
                    updatedAt = 200L,
                    operationId = "cash:overflow:in",
                ),
                BackupCashFlowRecord(
                    id = 11L,
                    accountId = 1L,
                    direction = "outflow",
                    amount = Long.MAX_VALUE,
                    note = "",
                    occurredAt = 400L,
                    createdAt = 400L,
                    updatedAt = 400L,
                    operationId = "cash:overflow:out",
                ),
            ),
            transferRecords = emptyList(),
            balanceUpdateRecords = emptyList(),
            balanceAdjustmentRecords = listOf(
                BackupBalanceAdjustmentRecord(
                    id = 12L,
                    accountId = 1L,
                    delta = 1L,
                    occurredAt = 300L,
                    createdAt = 300L,
                    updatedAt = 300L,
                    operationId = "adjustment:overflow",
                ),
            ),
        )

        assertFailureContains("算术溢出") { validator(overflow) }
    }

    @Test
    fun `invalid metadata account order and visual enums are rejected`() {
        val valid = validSnapshot()
        assertFailureContains("databaseVersion") {
            validator(valid.copy(metadata = valid.metadata.copy(databaseVersion = 0)))
        }
        assertFailureContains("displayOrder") {
            validator(valid.copy(accounts = valid.accounts.map { it.copy(displayOrder = -1) }))
        }
        assertFailureContains("colorName") {
            validator(valid.copy(accounts = valid.accounts.map { it.copy(colorName = "unknown") }))
        }
    }

    private fun validSnapshot(): MoneyBackupSnapshot {
        val base = BackupJsonCodec.decode(requireNotNull(javaClass.getResource("/backups/v4.json")).readText())
        val secondAccount = BackupAccount(
            id = 2L,
            name = "银行卡",
            initialBalance = 0L,
            createdAt = 100L,
            lastUsedAt = null,
            lastBalanceUpdateAt = null,
            displayOrder = 1,
            colorName = "green",
        )
        return base.copy(
            metadata = base.metadata.copy(exportedAt = 1_700_000_000_004L),
            accounts = base.accounts + secondAccount,
            accountReminderConfigs = base.accountReminderConfigs + BackupAccountReminderConfig(
                accountId = 2L,
                config = BackupBalanceUpdateReminderConfig(
                    period = "weekly",
                    weekday = "friday",
                    monthDay = 1,
                    hour = 22,
                    minute = 0,
                    isEnabled = true,
                ),
            ),
            transferRecords = listOf(
                BackupTransferRecord(
                    id = 1L,
                    fromAccountId = 1L,
                    toAccountId = 2L,
                    amount = 10L,
                    note = "转账",
                    occurredAt = 600L,
                    createdAt = 600L,
                    updatedAt = 600L,
                    operationId = "transfer:v4:1",
                ),
            ),
        )
    }

    private fun assertFailureContains(expected: String, block: () -> Unit) {
        val error = assertFailsWith<IllegalArgumentException> { block() }
        assertTrue(error.message.orEmpty().contains(expected), error.message)
    }
}
