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
                    purpose,
                    occurredAt,
                    createdAt,
                    updatedAt,
                    isDeleted
                ) VALUES (
                    1,
                    99,
                    'outflow',
                    100,
                    '孤儿记录',
                    1000,
                    1000,
                    1000,
                    0
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
