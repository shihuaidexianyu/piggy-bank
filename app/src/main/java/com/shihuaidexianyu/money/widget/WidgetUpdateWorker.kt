package com.shihuaidexianyu.money.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Periodic worker that refreshes the [BalanceOverviewWidgetProvider] every 30 minutes so the
 * widget shows up-to-date totals even when the app is not in the foreground.
 */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        BalanceOverviewWidgetProvider.updateAll(applicationContext)
        return Result.success()
    }
}
