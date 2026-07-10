package com.shihuaidexianyu.money

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_MIGRATIONS
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_VERSION
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MoneyDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation(),
        databaseClass = MoneyDatabase::class.java,
    )

    @Test
    fun migrateAllHistoricalSchemasToCurrentVersion() {
        (1 until MONEY_DATABASE_VERSION).forEach { version ->
            val dbName = "$TEST_DB-v$version"
            helper.createDatabase(dbName, version).close()

            helper.runMigrationsAndValidate(
                name = dbName,
                version = MONEY_DATABASE_VERSION,
                validateDroppedTables = true,
                *MONEY_DATABASE_MIGRATIONS,
            ).close()
        }
    }

    @Test
    fun migrateFromVersion4DropsAccountGroupTypeAndAddsColorDefault() {
        helper.createDatabase(TEST_DB, 4).apply {
            createVersion4AccountsTable()
            execSQL(
                """
                INSERT INTO accounts (
                    id,
                    name,
                    groupType,
                    initialBalance,
                    createdAt,
                    archivedAt,
                    isArchived,
                    lastUsedAt,
                    lastBalanceUpdateAt,
                    displayOrder
                ) VALUES (
                    1,
                    '招商银行',
                    'bank',
                    120000,
                    1000,
                    NULL,
                    0,
                    2000,
                    NULL,
                    3
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name = TEST_DB,
            version = MONEY_DATABASE_VERSION,
            validateDroppedTables = true,
            *MONEY_DATABASE_MIGRATIONS,
        )

        val tableInfoCursor = migrated.query("PRAGMA table_info(accounts)")
        try {
            val columns = mutableSetOf<String>()
            while (tableInfoCursor.moveToNext()) {
                columns += tableInfoCursor.getString(tableInfoCursor.getColumnIndexOrThrow("name"))
            }
            assertFalse("groupType" in columns)
            assertTrue("iconName" in columns)
            assertTrue("colorName" in columns)
        } finally {
            tableInfoCursor.close()
        }
        val accountCursor = migrated.query(
            "SELECT name, initialBalance, displayOrder, colorName, iconName FROM accounts WHERE id = 1",
        )
        try {
            accountCursor.moveToFirst()
            assertEquals("招商银行", accountCursor.getString(0))
            assertEquals(120000L, accountCursor.getLong(1))
            assertEquals(3, accountCursor.getInt(2))
            assertEquals("blue", accountCursor.getString(3))
            assertEquals("wallet", accountCursor.getString(4))
        } finally {
            accountCursor.close()
        }
    }

    @Test(expected = SQLiteConstraintException::class)
    fun migratedDatabaseRejectsCashFlowForMissingAccount() {
        val dbName = "$TEST_DB-foreign-key"
        helper.createDatabase(dbName, 7).close()

        val migrated = helper.runMigrationsAndValidate(
            name = dbName,
            version = MONEY_DATABASE_VERSION,
            validateDroppedTables = true,
            *MONEY_DATABASE_MIGRATIONS,
        )
        try {
            migrated.execSQL("PRAGMA foreign_keys=ON")
            migrated.execSQL(
                """
                INSERT INTO cash_flow_records (
                    id,
                    accountId,
                    direction,
                    amount,
                    note,
                    occurredAt,
                    createdAt,
                    updatedAt,
                    deletedAt,
                    operationId
                ) VALUES (
                    1,
                    99,
                    'outflow',
                    100,
                    '孤儿记录',
                    1000,
                    1000,
                    1000,
                    NULL,
                    'cash:test:missing-account'
                )
                """.trimIndent(),
            )
        } finally {
            migrated.close()
        }
    }

    @Test
    fun migrateFromVersion8To9DropsBalanceAdjustmentSourceColumnAndFiltersLinkedRows() {
        helper.createDatabase("$TEST_DB-v8", 8).apply {
            execSQL(
                """
                INSERT INTO accounts (
                    id, name, initialBalance, createdAt, archivedAt, isArchived,
                    lastUsedAt, lastBalanceUpdateAt, displayOrder, colorName
                ) VALUES (
                    1, '测试账户', 100000, 1000, NULL, 0,
                    NULL, NULL, 1, 'blue'
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO balance_adjustment_records (
                    id, accountId, delta, sourceUpdateRecordId, occurredAt, createdAt
                ) VALUES (1, 1, 5000, 0, 2000, 2000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO balance_adjustment_records (
                    id, accountId, delta, sourceUpdateRecordId, occurredAt, createdAt
                ) VALUES (2, 1, -3000, 999, 3000, 3000)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name = "$TEST_DB-v8",
            version = 9,
            validateDroppedTables = true,
            *MONEY_DATABASE_MIGRATIONS,
        )

        val tableInfoCursor = migrated.query("PRAGMA table_info(balance_adjustment_records)")
        try {
            val columns = mutableSetOf<String>()
            while (tableInfoCursor.moveToNext()) {
                columns += tableInfoCursor.getString(tableInfoCursor.getColumnIndexOrThrow("name"))
            }
            assertFalse("sourceUpdateRecordId should be dropped, got columns=$columns" in columns)
        } finally {
            tableInfoCursor.close()
        }

        val countCursor = migrated.query("SELECT COUNT(*) FROM balance_adjustment_records")
        try {
            countCursor.moveToFirst()
            assertEquals(1, countCursor.getInt(0))
        } finally {
            countCursor.close()
        }

        val rowCursor = migrated.query("SELECT id, delta FROM balance_adjustment_records")
        try {
            rowCursor.moveToFirst()
            assertEquals(1L, rowCursor.getLong(0))
            assertEquals(5000L, rowCursor.getLong(1))
        } finally {
            rowCursor.close()
        }
    }

    @Test
    fun migrateFromVersion9To10AddsIconNameColumnWithWalletDefault() {
        helper.createDatabase("$TEST_DB-v9", 9).apply {
            execSQL(
                """
                INSERT INTO accounts (
                    id, name, initialBalance, createdAt, archivedAt, isArchived,
                    lastUsedAt, lastBalanceUpdateAt, displayOrder, colorName
                ) VALUES (
                    1, '测试账户', 100000, 1000, NULL, 0,
                    NULL, NULL, 1, 'blue'
                )
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name = "$TEST_DB-v9",
            version = 10,
            validateDroppedTables = true,
            *MONEY_DATABASE_MIGRATIONS,
        )

        val tableInfoCursor = migrated.query("PRAGMA table_info(accounts)")
        try {
            val columns = mutableSetOf<String>()
            while (tableInfoCursor.moveToNext()) {
                columns += tableInfoCursor.getString(tableInfoCursor.getColumnIndexOrThrow("name"))
            }
            assertTrue("iconName should exist after 9→10 migration, got columns=$columns", "iconName" in columns)
        } finally {
            tableInfoCursor.close()
        }

        val accountCursor = migrated.query("SELECT iconName FROM accounts WHERE id = 1")
        try {
            accountCursor.moveToFirst()
            assertEquals("wallet", accountCursor.getString(0))
        } finally {
            accountCursor.close()
        }
    }

    @Test
    fun migrateFromVersion13To14PreservesPopulatedLedgerAndNormalizesLifecycle() {
        val dbName = "$TEST_DB-v13-populated"
        helper.createDatabase(dbName, 13).apply {
            execSQL(
                """
                INSERT INTO accounts (
                    id, name, initialBalance, createdAt, archivedAt, isArchived,
                    lastUsedAt, lastBalanceUpdateAt, displayOrder, colorName, iconName
                ) VALUES
                    (1, '活动账户', 1000, 1000, NULL, 0, 2000, 2500, 0, 'blue', 'wallet'),
                    (2, '归档零账户', 0, 1000, 2000, 1, NULL, NULL, 1, 'green', 'cash'),
                    (3, '归档非零账户', 5000, 1000, 3000, 1, 3500, 4000, 2, 'red', 'bank')
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO cash_flow_records (
                    id, accountId, direction, amount, purpose, occurredAt, createdAt, updatedAt, isDeleted
                ) VALUES
                    (10, 3, 'inflow', 700, '保留用途', 9000, 8500, 8000, 0),
                    (11, 1, 'outflow', 111, '已删除现金', 2000, 2000, 2500, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO transfer_records (
                    id, fromAccountId, toAccountId, amount, note, occurredAt, createdAt, updatedAt, isDeleted
                ) VALUES
                    (20, 3, 1, 200, '晚于归档', 8000, 7500, 7000, 0),
                    (21, 1, 3, 222, '已删除转账', 7000, 6000, 6500, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO balance_update_records (
                    id, accountId, actualBalance, systemBalanceBeforeUpdate, delta, occurredAt, createdAt
                ) VALUES (30, 3, 1234, 1000, 234, 10000, 9500)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO balance_adjustment_records (
                    id, accountId, delta, occurredAt, createdAt
                ) VALUES (40, 3, -50, 11000, 10500)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO recurring_reminders (
                    id, name, type, accountId, direction, amount, periodType, periodValue, periodMonth,
                    isEnabled, nextDueAt, lastConfirmedAt, createdAt, updatedAt
                ) VALUES (50, '关闭账户提醒', 'subscription', 3, 'outflow', 999, 'monthly', 8, NULL,
                    1, 12000, NULL, 5000, 5000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO savings_goals (id, targetAmount, createdAt) VALUES
                    (5, 50000, 500),
                    (2, 20000, 200)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            name = dbName,
            version = 14,
            validateDroppedTables = true,
            *MONEY_DATABASE_MIGRATIONS,
        )

        migrated.query(
            "SELECT id, initialBalance, isHidden, closedAt FROM accounts ORDER BY id",
        ).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(1000L, cursor.getLong(1))
            assertEquals(0, cursor.getInt(2))
            assertTrue(cursor.isNull(3))
            assertTrue(cursor.moveToNext())
            assertEquals(2L, cursor.getLong(0))
            assertEquals(0L, cursor.getLong(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(2000L, cursor.getLong(3))
            assertTrue(cursor.moveToNext())
            assertEquals(3L, cursor.getLong(0))
            assertEquals(5000L, cursor.getLong(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(11000L, cursor.getLong(3))
            assertFalse(cursor.moveToNext())
        }

        migrated.query(
            "SELECT id, amount, note, createdAt, updatedAt, deletedAt, operationId " +
                "FROM cash_flow_records ORDER BY id",
        ).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(10L, cursor.getLong(0))
            assertEquals(700L, cursor.getLong(1))
            assertEquals("保留用途", cursor.getString(2))
            assertEquals(8500L, cursor.getLong(3))
            assertEquals(8500L, cursor.getLong(4))
            assertTrue(cursor.isNull(5))
            assertEquals("cash:legacy-v14:10", cursor.getString(6))
            assertTrue(cursor.moveToNext())
            assertEquals(11L, cursor.getLong(0))
            assertEquals(111L, cursor.getLong(1))
            assertEquals(2500L, cursor.getLong(4))
            assertEquals(2500L, cursor.getLong(5))
            assertEquals("cash:legacy-v14:11", cursor.getString(6))
            assertFalse(cursor.moveToNext())
        }

        migrated.query(
            "SELECT id, amount, createdAt, updatedAt, deletedAt, operationId " +
                "FROM transfer_records ORDER BY id",
        ).use { cursor ->
            assertTrue(cursor.moveToNext())
            assertEquals(20L, cursor.getLong(0))
            assertEquals(200L, cursor.getLong(1))
            assertEquals(7500L, cursor.getLong(2))
            assertEquals(7500L, cursor.getLong(3))
            assertTrue(cursor.isNull(4))
            assertEquals("transfer:legacy-v14:20", cursor.getString(5))
            assertTrue(cursor.moveToNext())
            assertEquals(21L, cursor.getLong(0))
            assertEquals(222L, cursor.getLong(1))
            assertEquals(6500L, cursor.getLong(3))
            assertEquals(6500L, cursor.getLong(4))
            assertEquals("transfer:legacy-v14:21", cursor.getString(5))
            assertFalse(cursor.moveToNext())
        }

        migrated.query(
            "SELECT id, actualBalance, systemBalanceBeforeUpdate, delta, occurredAt, createdAt, " +
                "updatedAt, deletedAt, operationId FROM balance_update_records",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(30L, cursor.getLong(0))
            assertEquals(1234L, cursor.getLong(1))
            assertEquals(1000L, cursor.getLong(2))
            assertEquals(234L, cursor.getLong(3))
            assertEquals(10000L, cursor.getLong(4))
            assertEquals(9500L, cursor.getLong(5))
            assertEquals(9500L, cursor.getLong(6))
            assertTrue(cursor.isNull(7))
            assertEquals("balance-update:legacy-v14:30", cursor.getString(8))
        }

        migrated.query(
            "SELECT id, delta, occurredAt, createdAt, updatedAt, deletedAt, operationId " +
                "FROM balance_adjustment_records",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(40L, cursor.getLong(0))
            assertEquals(-50L, cursor.getLong(1))
            assertEquals(11000L, cursor.getLong(2))
            assertEquals(10500L, cursor.getLong(3))
            assertEquals(10500L, cursor.getLong(4))
            assertTrue(cursor.isNull(5))
            assertEquals("balance-adjustment:legacy-v14:40", cursor.getString(6))
        }

        migrated.query(
            """
            SELECT COUNT(*), COUNT(DISTINCT operationId) FROM (
                SELECT operationId FROM cash_flow_records
                UNION ALL SELECT operationId FROM transfer_records
                UNION ALL SELECT operationId FROM balance_update_records
                UNION ALL SELECT operationId FROM balance_adjustment_records
            )
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(6, cursor.getInt(0))
            assertEquals(6, cursor.getInt(1))
        }

        migrated.query(
            "SELECT isEnabled, nextDueAt, anchorDueAt, lastNotifiedDueAt FROM recurring_reminders WHERE id = 50",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
            assertEquals(12000L, cursor.getLong(1))
            assertEquals(12000L, cursor.getLong(2))
            assertTrue(cursor.isNull(3))
        }

        migrated.query("SELECT id, targetAmount, createdAt, updatedAt FROM savings_goals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1L, cursor.getLong(0))
            assertEquals(20000L, cursor.getLong(1))
            assertEquals(200L, cursor.getLong(2))
            assertEquals(200L, cursor.getLong(3))
            assertFalse(cursor.moveToNext())
        }

        val expectedNewTables = setOf(
            "portable_settings",
            "account_reminder_configs",
            "local_migration_state",
        )
        migrated.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN " +
                "('portable_settings', 'account_reminder_configs', 'local_migration_state')",
        ).use { cursor ->
            val actual = buildSet {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
            assertEquals(expectedNewTables, actual)
        }
        expectedNewTables.forEach { table ->
            migrated.query("SELECT COUNT(*) FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("$table must be empty after schema migration", 0, cursor.getInt(0))
            }
        }

        migrated.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals("Foreign-key violations after 13→14 migration", 0, cursor.count)
        }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.createVersion4AccountsTable() {
        execSQL(
            """
            CREATE TABLE IF NOT EXISTS accounts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                name TEXT NOT NULL,
                groupType TEXT NOT NULL,
                initialBalance INTEGER NOT NULL,
                createdAt INTEGER NOT NULL,
                archivedAt INTEGER,
                isArchived INTEGER NOT NULL,
                lastUsedAt INTEGER,
                lastBalanceUpdateAt INTEGER,
                displayOrder INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        execSQL("CREATE INDEX IF NOT EXISTS index_accounts_name ON accounts (name)")
        execSQL("CREATE INDEX IF NOT EXISTS index_accounts_isArchived ON accounts (isArchived)")
    }

    private companion object {
        const val TEST_DB = "money-migration-test"
    }
}
