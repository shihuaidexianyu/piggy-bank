package com.shihuaidexianyu.money.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shihuaidexianyu.money.data.dao.AccountDao
import com.shihuaidexianyu.money.data.dao.AccountReminderConfigDao
import com.shihuaidexianyu.money.data.dao.BalanceAdjustmentRecordDao
import com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao
import com.shihuaidexianyu.money.data.dao.CashFlowRecordDao
import com.shihuaidexianyu.money.data.dao.HistoryRecordDao
import com.shihuaidexianyu.money.data.dao.LocalMigrationStateDao
import com.shihuaidexianyu.money.data.dao.PortableSettingsDao
import com.shihuaidexianyu.money.data.dao.RecurringReminderDao
import com.shihuaidexianyu.money.data.dao.SavingsGoalDao
import com.shihuaidexianyu.money.data.dao.TransferRecordDao
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.AccountReminderConfigEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.LocalMigrationStateEntity
import com.shihuaidexianyu.money.data.entity.PortableSettingsEntity
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.data.entity.SavingsGoalEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity

const val MONEY_DATABASE_VERSION = 14

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_balance_adjustment_records_sourceUpdateRecordId`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_balance_adjustment_records_sourceUpdateRecordId` ON `balance_adjustment_records` (`sourceUpdateRecordId`)")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recurring_reminders` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `type` TEXT NOT NULL,
                `accountId` INTEGER NOT NULL,
                `direction` TEXT NOT NULL,
                `amount` INTEGER NOT NULL,
                `periodType` TEXT NOT NULL,
                `periodValue` INTEGER NOT NULL,
                `periodMonth` INTEGER,
                `isEnabled` INTEGER NOT NULL DEFAULT 1,
                `nextDueAt` INTEGER NOT NULL,
                `lastConfirmedAt` INTEGER,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_reminders_accountId` ON `recurring_reminders` (`accountId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_reminders_nextDueAt` ON `recurring_reminders` (`nextDueAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_reminders_isEnabled` ON `recurring_reminders` (`isEnabled`)")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_investment_settlements_accountId`")
        db.execSQL("DROP INDEX IF EXISTS `index_investment_settlements_balanceUpdateRecordId`")
        db.execSQL("DROP INDEX IF EXISTS `index_investment_settlements_periodStartAt_periodEndAt`")
        db.execSQL("DROP TABLE IF EXISTS `investment_settlements`")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `accounts_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `initialBalance` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `archivedAt` INTEGER,
                `isArchived` INTEGER NOT NULL,
                `lastUsedAt` INTEGER,
                `lastBalanceUpdateAt` INTEGER,
                `displayOrder` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `accounts_new` (
                `id`,
                `name`,
                `initialBalance`,
                `createdAt`,
                `archivedAt`,
                `isArchived`,
                `lastUsedAt`,
                `lastBalanceUpdateAt`,
                `displayOrder`
            )
            SELECT
                `id`,
                `name`,
                `initialBalance`,
                `createdAt`,
                `archivedAt`,
                `isArchived`,
                `lastUsedAt`,
                `lastBalanceUpdateAt`,
                `displayOrder`
            FROM `accounts`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `accounts`")
        db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_name` ON `accounts` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_isArchived` ON `accounts` (`isArchived`)")
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `iconName` TEXT NOT NULL DEFAULT 'wallet'")
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `colorName` TEXT NOT NULL DEFAULT 'blue'")
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `accounts_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `initialBalance` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `archivedAt` INTEGER,
                `isArchived` INTEGER NOT NULL,
                `lastUsedAt` INTEGER,
                `lastBalanceUpdateAt` INTEGER,
                `displayOrder` INTEGER NOT NULL,
                `colorName` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `accounts_new` (
                `id`,
                `name`,
                `initialBalance`,
                `createdAt`,
                `archivedAt`,
                `isArchived`,
                `lastUsedAt`,
                `lastBalanceUpdateAt`,
                `displayOrder`,
                `colorName`
            )
            SELECT
                `id`,
                `name`,
                `initialBalance`,
                `createdAt`,
                `archivedAt`,
                `isArchived`,
                `lastUsedAt`,
                `lastBalanceUpdateAt`,
                `displayOrder`,
                `colorName`
            FROM `accounts`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `accounts`")
        db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_name` ON `accounts` (`name`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_isArchived` ON `accounts` (`isArchived`)")
    }
}

private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        rebuildCashFlowRecords(db)
        rebuildTransferRecords(db)
        rebuildBalanceUpdateRecords(db)
        rebuildBalanceAdjustmentRecordsForVersion8(db)
        rebuildRecurringReminders(db)
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        rebuildBalanceAdjustmentRecordsForVersion9(db)
    }
}

private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `accounts` ADD COLUMN `iconName` TEXT NOT NULL DEFAULT 'wallet'")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `savings_goals` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `targetAmount` INTEGER NOT NULL,
                `colorName` TEXT NOT NULL DEFAULT 'blue',
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `savings_goal_account_links` (
                `goalId` INTEGER NOT NULL,
                `accountId` INTEGER NOT NULL,
                PRIMARY KEY(`goalId`, `accountId`),
                FOREIGN KEY(`goalId`) REFERENCES `savings_goals`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goal_account_links_goalId` ON `savings_goal_account_links` (`goalId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_goal_account_links_accountId` ON `savings_goal_account_links` (`accountId`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_savings_goal_account_links_goalId_accountId` ON `savings_goal_account_links` (`goalId`, `accountId`)")
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `savings_goals_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `targetAmount` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `savings_goals_new` (`id`, `name`, `targetAmount`, `createdAt`)
            SELECT `id`, `name`, `targetAmount`, `createdAt` FROM `savings_goals`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `savings_goals`")
        db.execSQL("ALTER TABLE `savings_goals_new` RENAME TO `savings_goals`")
    }
}

private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `savings_goal_account_links`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `savings_goals_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `targetAmount` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `savings_goals_new` (`id`, `targetAmount`, `createdAt`)
            SELECT `id`, `targetAmount`, `createdAt` FROM `savings_goals`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `savings_goals`")
        db.execSQL("ALTER TABLE `savings_goals_new` RENAME TO `savings_goals`")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA defer_foreign_keys = ON")
        createVersion14ReplacementTables(db)
        copyVersion13DataIntoVersion14Tables(db)
        replaceVersion13Tables(db)
        createVersion14Indices(db)
        createVersion14AuxiliaryTables(db)
        requireNoForeignKeyViolations(db)
    }
}

internal val MONEY_DATABASE_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
    MIGRATION_12_13,
    MIGRATION_13_14,
)

@Database(
    entities = [
        AccountEntity::class,
        CashFlowRecordEntity::class,
        TransferRecordEntity::class,
        BalanceUpdateRecordEntity::class,
        BalanceAdjustmentRecordEntity::class,
        RecurringReminderEntity::class,
        SavingsGoalEntity::class,
        PortableSettingsEntity::class,
        AccountReminderConfigEntity::class,
        LocalMigrationStateEntity::class,
    ],
    version = MONEY_DATABASE_VERSION,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun cashFlowRecordDao(): CashFlowRecordDao
    abstract fun transferRecordDao(): TransferRecordDao
    abstract fun balanceUpdateRecordDao(): BalanceUpdateRecordDao
    abstract fun balanceAdjustmentRecordDao(): BalanceAdjustmentRecordDao
    abstract fun historyRecordDao(): HistoryRecordDao
    abstract fun recurringReminderDao(): RecurringReminderDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun portableSettingsDao(): PortableSettingsDao
    abstract fun accountReminderConfigDao(): AccountReminderConfigDao
    abstract fun localMigrationStateDao(): LocalMigrationStateDao

    companion object {
        @Volatile
        private var INSTANCE: MoneyDatabase? = null

        fun getInstance(context: Context): MoneyDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context,
                    MoneyDatabase::class.java,
                    "money.db",
                ).addMigrations(*MONEY_DATABASE_MIGRATIONS).build()
                    .also { INSTANCE = it }
            }
        }
    }
}

private fun createVersion14ReplacementTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "accounts_v14" (
            "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            "name" TEXT NOT NULL,
            "initialBalance" INTEGER NOT NULL,
            "createdAt" INTEGER NOT NULL,
            "isHidden" INTEGER NOT NULL,
            "closedAt" INTEGER,
            "lastUsedAt" INTEGER,
            "lastBalanceUpdateAt" INTEGER,
            "displayOrder" INTEGER NOT NULL,
            "colorName" TEXT NOT NULL,
            "iconName" TEXT NOT NULL
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "cash_flow_records_v14" (
            "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            "accountId" INTEGER NOT NULL,
            "direction" TEXT NOT NULL,
            "amount" INTEGER NOT NULL,
            "note" TEXT NOT NULL,
            "occurredAt" INTEGER NOT NULL,
            "createdAt" INTEGER NOT NULL,
            "updatedAt" INTEGER NOT NULL,
            "deletedAt" INTEGER,
            "operationId" TEXT NOT NULL,
            FOREIGN KEY("accountId") REFERENCES "accounts_v14"("id") ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "transfer_records_v14" (
            "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            "fromAccountId" INTEGER NOT NULL,
            "toAccountId" INTEGER NOT NULL,
            "amount" INTEGER NOT NULL,
            "note" TEXT NOT NULL,
            "occurredAt" INTEGER NOT NULL,
            "createdAt" INTEGER NOT NULL,
            "updatedAt" INTEGER NOT NULL,
            "deletedAt" INTEGER,
            "operationId" TEXT NOT NULL,
            FOREIGN KEY("fromAccountId") REFERENCES "accounts_v14"("id") ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY("toAccountId") REFERENCES "accounts_v14"("id") ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "balance_update_records_v14" (
            "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            "accountId" INTEGER NOT NULL,
            "actualBalance" INTEGER NOT NULL,
            "systemBalanceBeforeUpdate" INTEGER NOT NULL,
            "delta" INTEGER NOT NULL,
            "occurredAt" INTEGER NOT NULL,
            "createdAt" INTEGER NOT NULL,
            "updatedAt" INTEGER NOT NULL,
            "deletedAt" INTEGER,
            "operationId" TEXT NOT NULL,
            FOREIGN KEY("accountId") REFERENCES "accounts_v14"("id") ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "balance_adjustment_records_v14" (
            "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            "accountId" INTEGER NOT NULL,
            "delta" INTEGER NOT NULL,
            "occurredAt" INTEGER NOT NULL,
            "createdAt" INTEGER NOT NULL,
            "updatedAt" INTEGER NOT NULL,
            "deletedAt" INTEGER,
            "operationId" TEXT NOT NULL,
            FOREIGN KEY("accountId") REFERENCES "accounts_v14"("id") ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "recurring_reminders_v14" (
            "id" INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            "name" TEXT NOT NULL,
            "type" TEXT NOT NULL,
            "accountId" INTEGER NOT NULL,
            "direction" TEXT NOT NULL,
            "amount" INTEGER NOT NULL,
            "periodType" TEXT NOT NULL,
            "periodValue" INTEGER NOT NULL,
            "periodMonth" INTEGER,
            "isEnabled" INTEGER NOT NULL,
            "nextDueAt" INTEGER NOT NULL,
            "anchorDueAt" INTEGER NOT NULL,
            "lastNotifiedDueAt" INTEGER,
            "lastConfirmedAt" INTEGER,
            "createdAt" INTEGER NOT NULL,
            "updatedAt" INTEGER NOT NULL,
            FOREIGN KEY("accountId") REFERENCES "accounts_v14"("id") ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "savings_goals_v14" (
            "id" INTEGER NOT NULL PRIMARY KEY,
            "targetAmount" INTEGER NOT NULL,
            "createdAt" INTEGER NOT NULL,
            "updatedAt" INTEGER NOT NULL
        )
        """.trimIndent(),
    )
}

private fun copyVersion13DataIntoVersion14Tables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        INSERT INTO "accounts_v14" (
            "id",
            "name",
            "initialBalance",
            "createdAt",
            "isHidden",
            "closedAt",
            "lastUsedAt",
            "lastBalanceUpdateAt",
            "displayOrder",
            "colorName",
            "iconName"
        )
        SELECT
            a."id",
            a."name",
            a."initialBalance",
            a."createdAt",
            0,
            CASE
                WHEN a."isArchived" = 0 THEN NULL
                ELSE MAX(
                    a."createdAt",
                    COALESCE(a."archivedAt", a."createdAt"),
                    COALESCE(a."lastUsedAt", a."createdAt"),
                    COALESCE(a."lastBalanceUpdateAt", a."createdAt"),
                    COALESCE(
                        (
                            SELECT MAX(c."occurredAt")
                            FROM "cash_flow_records" c
                            WHERE c."accountId" = a."id"
                        ),
                        a."createdAt"
                    ),
                    COALESCE(
                        (
                            SELECT MAX(t."occurredAt")
                            FROM "transfer_records" t
                            WHERE t."fromAccountId" = a."id" OR t."toAccountId" = a."id"
                        ),
                        a."createdAt"
                    ),
                    COALESCE(
                        (
                            SELECT MAX(u."occurredAt")
                            FROM "balance_update_records" u
                            WHERE u."accountId" = a."id"
                        ),
                        a."createdAt"
                    ),
                    COALESCE(
                        (
                            SELECT MAX(j."occurredAt")
                            FROM "balance_adjustment_records" j
                            WHERE j."accountId" = a."id"
                        ),
                        a."createdAt"
                    )
                )
            END,
            a."lastUsedAt",
            a."lastBalanceUpdateAt",
            a."displayOrder",
            a."colorName",
            a."iconName"
        FROM "accounts" a
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO "cash_flow_records_v14" (
            "id",
            "accountId",
            "direction",
            "amount",
            "note",
            "occurredAt",
            "createdAt",
            "updatedAt",
            "deletedAt",
            "operationId"
        )
        SELECT
            c."id",
            c."accountId",
            c."direction",
            c."amount",
            c."purpose",
            c."occurredAt",
            c."createdAt",
            MAX(c."createdAt", c."updatedAt"),
            CASE
                WHEN c."isDeleted" = 1 THEN MAX(c."createdAt", c."updatedAt")
                ELSE NULL
            END,
            'cash:legacy-v14:' || c."id"
        FROM "cash_flow_records" c
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO "transfer_records_v14" (
            "id",
            "fromAccountId",
            "toAccountId",
            "amount",
            "note",
            "occurredAt",
            "createdAt",
            "updatedAt",
            "deletedAt",
            "operationId"
        )
        SELECT
            t."id",
            t."fromAccountId",
            t."toAccountId",
            t."amount",
            t."note",
            t."occurredAt",
            t."createdAt",
            MAX(t."createdAt", t."updatedAt"),
            CASE
                WHEN t."isDeleted" = 1 THEN MAX(t."createdAt", t."updatedAt")
                ELSE NULL
            END,
            'transfer:legacy-v14:' || t."id"
        FROM "transfer_records" t
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO "balance_update_records_v14" (
            "id",
            "accountId",
            "actualBalance",
            "systemBalanceBeforeUpdate",
            "delta",
            "occurredAt",
            "createdAt",
            "updatedAt",
            "deletedAt",
            "operationId"
        )
        SELECT
            u."id",
            u."accountId",
            u."actualBalance",
            u."systemBalanceBeforeUpdate",
            u."delta",
            u."occurredAt",
            u."createdAt",
            u."createdAt",
            NULL,
            'balance-update:legacy-v14:' || u."id"
        FROM "balance_update_records" u
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO "balance_adjustment_records_v14" (
            "id",
            "accountId",
            "delta",
            "occurredAt",
            "createdAt",
            "updatedAt",
            "deletedAt",
            "operationId"
        )
        SELECT
            j."id",
            j."accountId",
            j."delta",
            j."occurredAt",
            j."createdAt",
            j."createdAt",
            NULL,
            'balance-adjustment:legacy-v14:' || j."id"
        FROM "balance_adjustment_records" j
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO "recurring_reminders_v14" (
            "id",
            "name",
            "type",
            "accountId",
            "direction",
            "amount",
            "periodType",
            "periodValue",
            "periodMonth",
            "isEnabled",
            "nextDueAt",
            "anchorDueAt",
            "lastNotifiedDueAt",
            "lastConfirmedAt",
            "createdAt",
            "updatedAt"
        )
        SELECT
            r."id",
            r."name",
            r."type",
            r."accountId",
            r."direction",
            r."amount",
            r."periodType",
            r."periodValue",
            r."periodMonth",
            CASE WHEN a."isArchived" = 1 THEN 0 ELSE r."isEnabled" END,
            r."nextDueAt",
            r."nextDueAt",
            NULL,
            r."lastConfirmedAt",
            r."createdAt",
            r."updatedAt"
        FROM "recurring_reminders" r
        INNER JOIN "accounts" a ON a."id" = r."accountId"
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO "savings_goals_v14" ("id", "targetAmount", "createdAt", "updatedAt")
        SELECT 1, g."targetAmount", g."createdAt", g."createdAt"
        FROM "savings_goals" g
        ORDER BY g."id" ASC
        LIMIT 1
        """.trimIndent(),
    )
}

private fun replaceVersion13Tables(db: SupportSQLiteDatabase) {
    db.execSQL("DROP TABLE \"cash_flow_records\"")
    db.execSQL("DROP TABLE \"transfer_records\"")
    db.execSQL("DROP TABLE \"balance_update_records\"")
    db.execSQL("DROP TABLE \"balance_adjustment_records\"")
    db.execSQL("DROP TABLE \"recurring_reminders\"")
    db.execSQL("DROP TABLE \"savings_goals\"")
    db.execSQL("DROP TABLE \"accounts\"")

    db.execSQL("ALTER TABLE \"accounts_v14\" RENAME TO \"accounts\"")
    db.execSQL("ALTER TABLE \"cash_flow_records_v14\" RENAME TO \"cash_flow_records\"")
    db.execSQL("ALTER TABLE \"transfer_records_v14\" RENAME TO \"transfer_records\"")
    db.execSQL("ALTER TABLE \"balance_update_records_v14\" RENAME TO \"balance_update_records\"")
    db.execSQL("ALTER TABLE \"balance_adjustment_records_v14\" RENAME TO \"balance_adjustment_records\"")
    db.execSQL("ALTER TABLE \"recurring_reminders_v14\" RENAME TO \"recurring_reminders\"")
    db.execSQL("ALTER TABLE \"savings_goals_v14\" RENAME TO \"savings_goals\"")
}

private fun createVersion14Indices(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE INDEX IF NOT EXISTS \"index_accounts_name\" ON \"accounts\" (\"name\")")
    db.execSQL("CREATE INDEX IF NOT EXISTS \"index_accounts_isHidden\" ON \"accounts\" (\"isHidden\")")
    db.execSQL("CREATE INDEX IF NOT EXISTS \"index_accounts_closedAt\" ON \"accounts\" (\"closedAt\")")

    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_cash_flow_records_accountId_deletedAt_occurredAt"
        ON "cash_flow_records" ("accountId", "deletedAt", "occurredAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_cash_flow_records_direction_deletedAt_occurredAt"
        ON "cash_flow_records" ("direction", "deletedAt", "occurredAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS "index_cash_flow_records_operationId"
        ON "cash_flow_records" ("operationId")
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_transfer_records_fromAccountId_deletedAt_occurredAt"
        ON "transfer_records" ("fromAccountId", "deletedAt", "occurredAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_transfer_records_toAccountId_deletedAt_occurredAt"
        ON "transfer_records" ("toAccountId", "deletedAt", "occurredAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_transfer_records_deletedAt_occurredAt"
        ON "transfer_records" ("deletedAt", "occurredAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS "index_transfer_records_operationId"
        ON "transfer_records" ("operationId")
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_balance_update_records_accountId_deletedAt_occurredAt_id"
        ON "balance_update_records" ("accountId", "deletedAt", "occurredAt", "id")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_balance_update_records_deletedAt_occurredAt"
        ON "balance_update_records" ("deletedAt", "occurredAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS "index_balance_update_records_operationId"
        ON "balance_update_records" ("operationId")
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_balance_adjustment_records_accountId_deletedAt_occurredAt"
        ON "balance_adjustment_records" ("accountId", "deletedAt", "occurredAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_balance_adjustment_records_deletedAt_occurredAt"
        ON "balance_adjustment_records" ("deletedAt", "occurredAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE UNIQUE INDEX IF NOT EXISTS "index_balance_adjustment_records_operationId"
        ON "balance_adjustment_records" ("operationId")
        """.trimIndent(),
    )

    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_recurring_reminders_accountId"
        ON "recurring_reminders" ("accountId")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_recurring_reminders_nextDueAt"
        ON "recurring_reminders" ("nextDueAt")
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS "index_recurring_reminders_isEnabled_nextDueAt"
        ON "recurring_reminders" ("isEnabled", "nextDueAt")
        """.trimIndent(),
    )
}

private fun createVersion14AuxiliaryTables(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "portable_settings" (
            "id" INTEGER NOT NULL PRIMARY KEY,
            "currencySymbol" TEXT NOT NULL,
            "amountColorMode" TEXT NOT NULL,
            "monthlyBudgetAmount" INTEGER
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "account_reminder_configs" (
            "accountId" INTEGER NOT NULL PRIMARY KEY,
            "period" TEXT NOT NULL,
            "weekday" TEXT NOT NULL,
            "monthDay" INTEGER NOT NULL,
            "hour" INTEGER NOT NULL,
            "minute" INTEGER NOT NULL,
            "isEnabled" INTEGER NOT NULL,
            "lastNotifiedBoundaryAt" INTEGER,
            FOREIGN KEY("accountId") REFERENCES "accounts"("id") ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS "local_migration_state" (
            "key" TEXT NOT NULL PRIMARY KEY,
            "state" TEXT NOT NULL,
            "completedAt" INTEGER,
            "detail" TEXT
        )
        """.trimIndent(),
    )
}

private fun requireNoForeignKeyViolations(db: SupportSQLiteDatabase) {
    db.query("PRAGMA foreign_key_check").use { cursor ->
        if (cursor.moveToFirst()) {
            val table = cursor.getString(0)
            val rowId = cursor.getLong(1)
            val parent = cursor.getString(2)
            val foreignKeyId = cursor.getInt(3)
            throw IllegalStateException(
                "Foreign-key violation after 13→14 migration: " +
                    "table=$table rowId=$rowId parent=$parent foreignKeyId=$foreignKeyId",
            )
        }
    }
}

private fun rebuildCashFlowRecords(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `cash_flow_records_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `accountId` INTEGER NOT NULL,
            `direction` TEXT NOT NULL,
            `amount` INTEGER NOT NULL,
            `purpose` TEXT NOT NULL,
            `occurredAt` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `isDeleted` INTEGER NOT NULL,
            FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO `cash_flow_records_new` (
            `id`, `accountId`, `direction`, `amount`, `purpose`, `occurredAt`, `createdAt`, `updatedAt`, `isDeleted`
        )
        SELECT `id`, `accountId`, `direction`, `amount`, `purpose`, `occurredAt`, `createdAt`, `updatedAt`, `isDeleted`
        FROM `cash_flow_records`
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE `cash_flow_records`")
    db.execSQL("ALTER TABLE `cash_flow_records_new` RENAME TO `cash_flow_records`")
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_cash_flow_records_accountId_isDeleted_occurredAt`
        ON `cash_flow_records` (`accountId`, `isDeleted`, `occurredAt`)
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_cash_flow_records_direction_isDeleted_occurredAt`
        ON `cash_flow_records` (`direction`, `isDeleted`, `occurredAt`)
        """.trimIndent(),
    )
}

private fun rebuildTransferRecords(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `transfer_records_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `fromAccountId` INTEGER NOT NULL,
            `toAccountId` INTEGER NOT NULL,
            `amount` INTEGER NOT NULL,
            `note` TEXT NOT NULL,
            `occurredAt` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            `isDeleted` INTEGER NOT NULL,
            FOREIGN KEY(`fromAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(`toAccountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO `transfer_records_new` (
            `id`, `fromAccountId`, `toAccountId`, `amount`, `note`, `occurredAt`, `createdAt`, `updatedAt`, `isDeleted`
        )
        SELECT `id`, `fromAccountId`, `toAccountId`, `amount`, `note`, `occurredAt`, `createdAt`, `updatedAt`, `isDeleted`
        FROM `transfer_records`
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE `transfer_records`")
    db.execSQL("ALTER TABLE `transfer_records_new` RENAME TO `transfer_records`")
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_transfer_records_fromAccountId_isDeleted_occurredAt`
        ON `transfer_records` (`fromAccountId`, `isDeleted`, `occurredAt`)
        """.trimIndent(),
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_transfer_records_toAccountId_isDeleted_occurredAt`
        ON `transfer_records` (`toAccountId`, `isDeleted`, `occurredAt`)
        """.trimIndent(),
    )
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_records_occurredAt` ON `transfer_records` (`occurredAt`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_transfer_records_isDeleted` ON `transfer_records` (`isDeleted`)")
}

private fun rebuildBalanceUpdateRecords(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `balance_update_records_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `accountId` INTEGER NOT NULL,
            `actualBalance` INTEGER NOT NULL,
            `systemBalanceBeforeUpdate` INTEGER NOT NULL,
            `delta` INTEGER NOT NULL,
            `occurredAt` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO `balance_update_records_new` (
            `id`, `accountId`, `actualBalance`, `systemBalanceBeforeUpdate`, `delta`, `occurredAt`, `createdAt`
        )
        SELECT `id`, `accountId`, `actualBalance`, `systemBalanceBeforeUpdate`, `delta`, `occurredAt`, `createdAt`
        FROM `balance_update_records`
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE `balance_update_records`")
    db.execSQL("ALTER TABLE `balance_update_records_new` RENAME TO `balance_update_records`")
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_balance_update_records_accountId_occurredAt_id`
        ON `balance_update_records` (`accountId`, `occurredAt`, `id`)
        """.trimIndent(),
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_balance_update_records_occurredAt` ON `balance_update_records` (`occurredAt`)",
    )
}

private fun rebuildBalanceAdjustmentRecordsForVersion8(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `balance_adjustment_records_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `accountId` INTEGER NOT NULL,
            `delta` INTEGER NOT NULL,
            `sourceUpdateRecordId` INTEGER NOT NULL,
            `occurredAt` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO `balance_adjustment_records_new` (
            `id`, `accountId`, `delta`, `sourceUpdateRecordId`, `occurredAt`, `createdAt`
        )
        SELECT `id`, `accountId`, `delta`, `sourceUpdateRecordId`, `occurredAt`, `createdAt`
        FROM `balance_adjustment_records`
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE `balance_adjustment_records`")
    db.execSQL("ALTER TABLE `balance_adjustment_records_new` RENAME TO `balance_adjustment_records`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_balance_adjustment_records_accountId` ON `balance_adjustment_records` (`accountId`)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_balance_adjustment_records_occurredAt` ON `balance_adjustment_records` (`occurredAt`)",
    )
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_balance_adjustment_records_sourceUpdateRecordId`
        ON `balance_adjustment_records` (`sourceUpdateRecordId`)
        """.trimIndent(),
    )
}

private fun rebuildBalanceAdjustmentRecordsForVersion9(db: SupportSQLiteDatabase) {
    db.execSQL("DROP INDEX IF EXISTS `index_balance_adjustment_records_sourceUpdateRecordId`")
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `balance_adjustment_records_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `accountId` INTEGER NOT NULL,
            `delta` INTEGER NOT NULL,
            `occurredAt` INTEGER NOT NULL,
            `createdAt` INTEGER NOT NULL,
            FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO `balance_adjustment_records_new` (
            `id`, `accountId`, `delta`, `occurredAt`, `createdAt`
        )
        SELECT `id`, `accountId`, `delta`, `occurredAt`, `createdAt`
        FROM `balance_adjustment_records`
        WHERE `sourceUpdateRecordId` = 0
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE `balance_adjustment_records`")
    db.execSQL("ALTER TABLE `balance_adjustment_records_new` RENAME TO `balance_adjustment_records`")
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_balance_adjustment_records_accountId` ON `balance_adjustment_records` (`accountId`)",
    )
    db.execSQL(
        "CREATE INDEX IF NOT EXISTS `index_balance_adjustment_records_occurredAt` ON `balance_adjustment_records` (`occurredAt`)",
    )
}

private fun rebuildRecurringReminders(db: SupportSQLiteDatabase) {
    db.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `recurring_reminders_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `accountId` INTEGER NOT NULL,
            `direction` TEXT NOT NULL,
            `amount` INTEGER NOT NULL,
            `periodType` TEXT NOT NULL,
            `periodValue` INTEGER NOT NULL,
            `periodMonth` INTEGER,
            `isEnabled` INTEGER NOT NULL,
            `nextDueAt` INTEGER NOT NULL,
            `lastConfirmedAt` INTEGER,
            `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL,
            FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
        )
        """.trimIndent(),
    )
    db.execSQL(
        """
        INSERT INTO `recurring_reminders_new` (
            `id`, `name`, `type`, `accountId`, `direction`, `amount`, `periodType`, `periodValue`, `periodMonth`,
            `isEnabled`, `nextDueAt`, `lastConfirmedAt`, `createdAt`, `updatedAt`
        )
        SELECT `id`, `name`, `type`, `accountId`, `direction`, `amount`, `periodType`, `periodValue`, `periodMonth`,
            `isEnabled`, `nextDueAt`, `lastConfirmedAt`, `createdAt`, `updatedAt`
        FROM `recurring_reminders`
        """.trimIndent(),
    )
    db.execSQL("DROP TABLE `recurring_reminders`")
    db.execSQL("ALTER TABLE `recurring_reminders_new` RENAME TO `recurring_reminders`")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_reminders_accountId` ON `recurring_reminders` (`accountId`)")
    db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_reminders_nextDueAt` ON `recurring_reminders` (`nextDueAt`)")
    db.execSQL(
        """
        CREATE INDEX IF NOT EXISTS `index_recurring_reminders_isEnabled_nextDueAt`
        ON `recurring_reminders` (`isEnabled`, `nextDueAt`)
        """.trimIndent(),
    )
}
