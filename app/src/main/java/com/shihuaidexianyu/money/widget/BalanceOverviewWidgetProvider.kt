package com.shihuaidexianyu.money.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shihuaidexianyu.money.MainActivity
import com.shihuaidexianyu.money.MoneyAppContainer
import com.shihuaidexianyu.money.MoneyApplication
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AppSettings
import com.shihuaidexianyu.money.domain.model.HomePeriod
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import com.shihuaidexianyu.money.di.SystemClockProvider
import com.shihuaidexianyu.money.di.SystemZoneIdProvider
import com.shihuaidexianyu.money.notification.MoneyAppContainerProvider
import com.shihuaidexianyu.money.util.AmountFormatter
import java.util.concurrent.TimeUnit

/**
 * Home-screen widget showing total assets + this month's income and expense. Read-only; tapping
 * the widget opens the app to the home dashboard.
 *
 * Updated on a 30-minute periodic [WidgetUpdateWorker] (the minimum allowed by the system for
 * `updatePeriodMillis` is 30 min). [updateAll] can also be called manually after data mutations
 * to refresh immediately.
 */
class BalanceOverviewWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, BalanceOverviewWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                ids.forEach { updateWidget(context, manager, it) }
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val container = (context.applicationContext as? MoneyAppContainerProvider)?.moneyAppContainer
            val views = RemoteViews(context.packageName, R.layout.widget_balance_overview)

            // Default placeholder while data loads.
            views.setTextViewText(R.id.widget_total_assets, "—")
            views.setTextViewText(R.id.widget_month_income, "—")
            views.setTextViewText(R.id.widget_month_expense, "—")

            if (container != null) {
                kotlinx.coroutines.runBlocking {
                    runCatching {
                        val accounts = container.accountRepository.queryActiveAccounts()
                        val balances = container.calculateAccountBalancesUseCase(accounts)
                        val totalAssets = balances.values.sum()

                        val range = TimeRangeCalculator.currentMonthRange(
                            zoneId = SystemZoneIdProvider.zoneId(),
                            nowMillis = SystemClockProvider.nowMillis(),
                        )
                        val inflow = container.transactionRepository.sumCashInflowBetween(range.startInclusive, range.endExclusive)
                        val outflow = container.transactionRepository.sumCashOutflowBetween(range.startInclusive, range.endExclusive)

                        val settings = AppSettings()
                        views.setTextViewText(R.id.widget_total_assets, AmountFormatter.format(totalAssets, settings))
                        views.setTextViewText(R.id.widget_month_income, AmountFormatter.format(inflow, settings))
                        views.setTextViewText(R.id.widget_month_expense, AmountFormatter.format(outflow, settings))
                    }
                }
            }

            // Tap → open app
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            manager.updateAppWidget(widgetId, views)
        }

        fun scheduleUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "widget-balance-update",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
