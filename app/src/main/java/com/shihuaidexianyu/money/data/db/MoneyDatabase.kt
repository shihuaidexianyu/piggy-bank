package com.shihuaidexianyu.money.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.shihuaidexianyu.money.data.dao.AccountDao
import com.shihuaidexianyu.money.data.dao.BalanceAdjustmentRecordDao
import com.shihuaidexianyu.money.data.dao.BalanceUpdateRecordDao
import com.shihuaidexianyu.money.data.dao.CashFlowRecordDao
import com.shihuaidexianyu.money.data.dao.RecurringReminderDao
import com.shihuaidexianyu.money.data.dao.TransferRecordDao
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity

const val MONEY_DATABASE_VERSION = 9

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

internal val MONEY_DATABASE_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
)

@Database(
    entities = [
        AccountEntity::class,
        CashFlowRecordEntity::class,
        TransferRecordEntity::class,
        BalanceUpdateRecordEntity::class,
        BalanceAdjustmentRecordEntity::class,
        RecurringReminderEntity::class,
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
    abstract fun recurringReminderDao(): RecurringReminderDao

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
