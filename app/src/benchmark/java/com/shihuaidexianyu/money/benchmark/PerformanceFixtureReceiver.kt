package com.shihuaidexianyu.money.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.room.withTransaction
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Benchmark-variant-only fixture loader. It is absent from debug and release APKs. */
class PerformanceFixtureReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recordCount = intent.getIntExtra(EXTRA_RECORD_COUNT, 0)
        require(recordCount == 10_000 || recordCount == 100_000)
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        if (preferences.getInt(KEY_RECORD_COUNT, 0) == recordCount) return

        runBlocking(Dispatchers.IO) {
            val database = MoneyDatabase.getInstance(context)
            database.withTransaction {
                val sqlite = database.openHelper.writableDatabase
                sqlite.execSQL("DELETE FROM cash_flow_records")
                sqlite.execSQL("DELETE FROM transfer_records")
                sqlite.execSQL("DELETE FROM balance_update_records")
                sqlite.execSQL("DELETE FROM balance_adjustment_records")
                sqlite.execSQL("DELETE FROM accounts")
                sqlite.execSQL(
                    """
                    INSERT INTO accounts(
                        id, name, initialBalance, createdAt, isHidden, closedAt,
                        lastUsedAt, lastBalanceUpdateAt, displayOrder, colorName, iconName
                    ) VALUES(1, 'Benchmark', 0, 1, 0, NULL, 1, NULL, 0, 'blue', 'wallet')
                    """.trimIndent(),
                )
                val insert = sqlite.compileStatement(
                    """
                    INSERT INTO cash_flow_records(
                        accountId, direction, amount, note, occurredAt,
                        createdAt, updatedAt, deletedAt, operationId
                    ) VALUES(1, ?, 1, '', ?, ?, ?, NULL, ?)
                    """.trimIndent(),
                )
                repeat(recordCount) { index ->
                    val timestamp = 1_700_000_000_000L + index
                    insert.clearBindings()
                    insert.bindString(1, if (index % 2 == 0) "inflow" else "outflow")
                    insert.bindLong(2, timestamp)
                    insert.bindLong(3, timestamp)
                    insert.bindLong(4, timestamp)
                    insert.bindString(5, "benchmark:$recordCount:$index")
                    insert.executeInsert()
                }
            }
        }
        preferences.edit().putInt(KEY_RECORD_COUNT, recordCount).commit()
    }

    private companion object {
        const val EXTRA_RECORD_COUNT = "record_count"
        const val PREFERENCES = "benchmark_fixture"
        const val KEY_RECORD_COUNT = "record_count"
    }
}
