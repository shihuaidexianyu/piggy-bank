package com.shihuaidexianyu.money.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shihuaidexianyu.money.MainActivity
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.util.AmountFormatter
import java.util.concurrent.TimeUnit

class BalanceOverviewWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        appWidgetIds.forEach { widgetId ->
            renderSafePlaceholder(context, appWidgetManager, widgetId)
        }
        WidgetUpdateRequester.requestImmediate(context)
    }

    companion object {
        const val ACTION_OPEN_WIDGET_HOME = "com.shihuaidexianyu.money.OPEN_WIDGET_HOME"

        fun widgetIds(context: Context): IntArray = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, BalanceOverviewWidgetProvider::class.java))

        fun renderSnapshot(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            snapshot: WidgetBalanceSnapshot,
        ) {
            val total = AmountFormatter.format(
                snapshot.totalAssets,
                snapshot.settings,
                snapshot.visibility,
            )
            val income = AmountFormatter.format(
                snapshot.monthInflow,
                snapshot.settings,
                snapshot.visibility,
            )
            val expense = AmountFormatter.format(
                snapshot.monthOutflow,
                snapshot.settings,
                snapshot.visibility,
            )
            val views = baseViews(context, widgetId).apply {
                setTextViewText(R.id.widget_total_assets, total)
                setTextViewText(R.id.widget_month_income, income)
                setTextViewText(R.id.widget_month_expense, expense)
                setContentDescription(R.id.widget_total_assets, "总资产 $total")
                setContentDescription(R.id.widget_month_income, "本月收入 $income")
                setContentDescription(R.id.widget_month_expense, "本月支出 $expense")
            }
            manager.updateAppWidget(widgetId, views)
        }

        fun renderSafePlaceholder(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
        ) {
            manager.updateAppWidget(widgetId, placeholderViews(context, widgetId))
        }

        fun renderAllSafePlaceholders(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            widgetIds(context).forEach { renderSafePlaceholder(context, manager, it) }
        }

        fun scheduleUpdate(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WidgetUpdateRequester.PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun placeholderViews(context: Context, widgetId: Int): RemoteViews =
            baseViews(context, widgetId).apply {
                setTextViewText(R.id.widget_total_assets, "—")
                setTextViewText(R.id.widget_month_income, "—")
                setTextViewText(R.id.widget_month_expense, "—")
                setContentDescription(R.id.widget_total_assets, "总资产已隐藏，正在刷新")
                setContentDescription(R.id.widget_month_income, "本月收入已隐藏，正在刷新")
                setContentDescription(R.id.widget_month_expense, "本月支出已隐藏，正在刷新")
            }

        private fun baseViews(context: Context, widgetId: Int): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_balance_overview)
            val openIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_WIDGET_HOME
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
            return views
        }
    }
}

object WidgetUpdateRequester {
    const val DEBOUNCE_MILLIS = 750L
    const val ONE_TIME_WORK_NAME = "widget-balance-refresh"
    const val PERIODIC_WORK_NAME = "widget-balance-update"

    fun requestDebounced(context: Context) = enqueue(context, DEBOUNCE_MILLIS)

    fun requestImmediate(context: Context) = enqueue(context, 0L)

    private fun enqueue(context: Context, delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ONE_TIME_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}
