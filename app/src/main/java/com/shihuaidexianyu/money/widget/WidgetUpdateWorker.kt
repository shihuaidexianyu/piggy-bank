package com.shihuaidexianyu.money.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shihuaidexianyu.money.notification.MoneyAppContainerProvider

/**
 * Periodic worker that refreshes the [BalanceOverviewWidgetProvider] every 30 minutes so the
 * widget shows up-to-date totals even when the app is not in the foreground.
 */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext.applicationContext as? MoneyAppContainerProvider)?.moneyAppContainer
            ?: return Result.success()
        if (container.startupMigrationCoordinator.withReadyLedgerAccess { true } == null) {
            return Result.retry()
        }
        BalanceOverviewWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }
}
