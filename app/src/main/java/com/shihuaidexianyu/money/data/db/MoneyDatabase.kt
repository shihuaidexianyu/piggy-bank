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

@Database(
    entities = [
        AccountEntity::class,
        CashFlowRecordEntity::class,
        TransferRecordEntity::class,
        BalanceUpdateRecordEntity::class,
        BalanceAdjustmentRecordEntity::class,
        RecurringReminderEntity::class,
    ],
    version = 4,
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
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { INSTANCE = it }
            }
        }
    }
}
