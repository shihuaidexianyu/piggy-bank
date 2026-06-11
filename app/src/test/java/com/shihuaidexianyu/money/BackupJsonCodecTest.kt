package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.data.backup.BackupJsonCodec
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.domain.model.ReminderType
import com.shihuaidexianyu.money.domain.model.backup.BackupAccount
import com.shihuaidexianyu.money.domain.model.backup.BackupAccountReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupBalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.backup.BackupMetadata
import com.shihuaidexianyu.money.domain.model.backup.BackupSettings
import com.shihuaidexianyu.money.domain.model.backup.MONEY_BACKUP_SCHEMA_VERSION
import com.shihuaidexianyu.money.domain.model.backup.MoneyBackupSnapshot
import com.shihuaidexianyu.money.domain.usecase.BuildExportJsonUseCase
import com.shihuaidexianyu.money.domain.usecase.BuildExportSnapshotUseCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun `backup codec round trips snapshot and ignores unknown fields`() {
        val snapshot = MoneyBackupSnapshot(
            metadata = BackupMetadata(
                schemaVersion = MONEY_BACKUP_SCHEMA_VERSION,
                databaseVersion = 7,
                exportedAt = Long.MAX_VALUE,
            ),
            settings = BackupSettings(
                homePeriod = "week",
                currencySymbol = "元\"\\\n",
                showStaleMark = true,
                themeMode = "system",
                amountColorMode = "red_income_green_expense",
                lastHistoryKeyword = "咖啡",
                lastHistoryAccountId = -1L,
                lastHistoryDateStartAt = 0L,
                lastHistoryDateEndAt = 0L,
                lastHistoryMinAmountText = "",
                lastHistoryMaxAmountText = "",
                lastHistoryAmountDirection = "all",
            ),
            accounts = listOf(
                BackupAccount(
                    id = 1L,
                    name = "现金\n账户",
                    initialBalance = Long.MAX_VALUE,
                    createdAt = 1L,
                    archivedAt = null,
                    isArchived = false,
                    lastUsedAt = null,
                    lastBalanceUpdateAt = null,
                    displayOrder = 0,
                    colorName = "green",
                ),
            ),
            cashFlowRecords = emptyList(),
            transferRecords = emptyList(),
            balanceUpdateRecords = emptyList(),
            balanceAdjustmentRecords = emptyList(),
            recurringReminders = emptyList(),
            accountReminderConfigs = listOf(
                BackupAccountReminderConfig(
                    accountId = 1L,
                    config = BackupBalanceUpdateReminderConfig(
                        weekday = "friday",
                        hour = 22,
                        minute = 0,
                    ),
                ),
            ),
        )

        val encoded = BackupJsonCodec.encode(snapshot)
        val withUnknownField = encoded.dropLast(1) + ",\"unknownTopLevel\":true}"

        assertEquals(snapshot, BackupJsonCodec.decode(withUnknownField))
    }

    @Test
    fun `backup codec upgrades schema 1 and drops linked adjustment records`() {
        val raw = """
            {
              "metadata":{"schemaVersion":1,"databaseVersion":8,"exportedAt":42},
              "settings":{
                "homePeriod":"week",
                "currencySymbol":"¥",
                "showStaleMark":true,
                "themeMode":"system",
                "amountColorMode":"red_income_green_expense",
                "lastHistoryKeyword":"",
                "lastHistoryAccountId":-1,
                "lastHistoryDateStartAt":-1,
                "lastHistoryDateEndAt":-1,
                "lastHistoryMinAmountText":"",
                "lastHistoryMaxAmountText":"",
                "lastHistoryAmountDirection":"all"
              },
              "accounts":[
                {
                  "id":1,
                  "name":"现金",
                  "initialBalance":100,
                  "createdAt":1,
                  "archivedAt":null,
                  "isArchived":false,
                  "lastUsedAt":null,
                  "lastBalanceUpdateAt":null,
                  "displayOrder":0,
                  "colorName":"blue"
                }
              ],
              "cashFlowRecords":[],
              "transferRecords":[],
              "balanceUpdateRecords":[],
              "balanceAdjustmentRecords":[
                {"id":1,"accountId":1,"delta":10,"sourceUpdateRecordId":5,"occurredAt":2,"createdAt":2},
                {"id":2,"accountId":1,"delta":20,"sourceUpdateRecordId":0,"occurredAt":3,"createdAt":3}
              ],
              "recurringReminders":[],
              "accountReminderConfigs":[
                {"accountId":1,"config":{"weekday":"friday","hour":22,"minute":0}}
              ]
            }
        """.trimIndent()

        val decoded = BackupJsonCodec.decode(raw)

        assertEquals(MONEY_BACKUP_SCHEMA_VERSION, decoded.metadata.schemaVersion)
        assertEquals(listOf(2L), decoded.balanceAdjustmentRecords.map { it.id })
        assertEquals(20L, decoded.balanceAdjustmentRecords.single().delta)
    }

    @Test
    fun `export json is parseable with empty collections`() = runBlocking {
        val json = buildUseCase()(exportedAt = 42L)
        val root = JSONObject(json)

        assertEquals(2, root.getJSONObject("metadata").getInt("schemaVersion"))
        assertEquals(42L, root.getJSONObject("metadata").getLong("exportedAt"))
        assertEquals(0, root.getJSONArray("accounts").length())
        assertEquals(0, root.getJSONArray("cashFlowRecords").length())
        assertEquals(0, root.getJSONArray("transferRecords").length())
        assertEquals(0, root.getJSONArray("balanceUpdateRecords").length())
        assertEquals(0, root.getJSONArray("balanceAdjustmentRecords").length())
        assertEquals(0, root.getJSONArray("recurringReminders").length())
        assertEquals(0, root.getJSONArray("accountReminderConfigs").length())
    }

    @Test
    fun `export json escapes text and preserves nulls and long values`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val reminderRepository = InMemoryRecurringReminderRepository()
        val accountName = "现金\"账户\\A\n第二行"
        val accountId = accountRepository.createAccount(
            Account(
                name = accountName,
                initialBalance = Long.MAX_VALUE,
                createdAt = 1L,
                colorName = "blue",
            ),
        )
        val purpose = "早餐\t包子\\豆浆\""
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = Long.MAX_VALUE,
                purpose = purpose,
                occurredAt = 2L,
                createdAt = 2L,
                updatedAt = 2L,
            ),
        )
        reminderRepository.insertReminder(
            RecurringReminder(
                name = "订阅\n会员",
                type = ReminderType.SUBSCRIPTION.value,
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = 888L,
                periodType = ReminderPeriodType.MONTHLY.value,
                periodValue = 9,
                periodMonth = null,
                nextDueAt = 3L,
                createdAt = 3L,
                updatedAt = 3L,
            ),
        )

        val json = buildUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            reminderRepository = reminderRepository,
            settingsRepository = TestSettingsRepository(
                AppSettings(
                    currencySymbol = "￥\"\\\n",
                    lastHistoryKeyword = "咖啡\n引号\"",
                ),
            ),
        )(exportedAt = Long.MAX_VALUE)
        val root = JSONObject(json)

        assertEquals(Long.MAX_VALUE, root.getJSONObject("metadata").getLong("exportedAt"))
        assertEquals("￥\"\\\n", root.getJSONObject("settings").getString("currencySymbol"))
        assertEquals("咖啡\n引号\"", root.getJSONObject("settings").getString("lastHistoryKeyword"))
        assertEquals(accountName, root.getJSONArray("accounts").getJSONObject(0).getString("name"))
        assertEquals(Long.MAX_VALUE, root.getJSONArray("accounts").getJSONObject(0).getLong("initialBalance"))
        assertEquals(purpose, root.getJSONArray("cashFlowRecords").getJSONObject(0).getString("purpose"))
        assertEquals(Long.MAX_VALUE, root.getJSONArray("cashFlowRecords").getJSONObject(0).getLong("amount"))
        assertTrue(root.getJSONArray("recurringReminders").getJSONObject(0).isNull("periodMonth"))
    }

    private fun buildUseCase(
        accountRepository: InMemoryAccountRepository = InMemoryAccountRepository(),
        transactionRepository: InMemoryTransactionRepository = InMemoryTransactionRepository(),
        reminderRepository: InMemoryRecurringReminderRepository = InMemoryRecurringReminderRepository(),
        settingsRepository: TestSettingsRepository = TestSettingsRepository(),
        reminderSettingsRepository: InMemoryAccountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
    ): BuildExportJsonUseCase {
        return BuildExportJsonUseCase(
            buildExportSnapshotUseCase = BuildExportSnapshotUseCase(
                accountReminderSettingsRepository = reminderSettingsRepository,
                accountRepository = accountRepository,
                recurringReminderRepository = reminderRepository,
                settingsRepository = settingsRepository,
                transactionRepository = transactionRepository,
            ),
        )
    }
}
