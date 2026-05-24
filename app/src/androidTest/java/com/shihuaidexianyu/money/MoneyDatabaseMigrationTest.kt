package com.shihuaidexianyu.money

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shihuaidexianyu.money.data.db.MONEY_DATABASE_MIGRATIONS
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            version = 7,
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
            assertFalse("iconName" in columns)
            assertEquals(true, "colorName" in columns)
        } finally {
            tableInfoCursor.close()
        }
        val accountCursor = migrated.query(
            "SELECT name, initialBalance, displayOrder, colorName FROM accounts WHERE id = 1",
        )
        try {
            accountCursor.moveToFirst()
            assertEquals("招商银行", accountCursor.getString(0))
            assertEquals(120000L, accountCursor.getLong(1))
            assertEquals(3, accountCursor.getInt(2))
            assertEquals("blue", accountCursor.getString(3))
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
