package com.shihuaidexianyu.money.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.shihuaidexianyu.money.MainActivity
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.model.AmountColorMode
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
                val (incomeColor, expenseColor) = widgetFlowColors(
                    context,
                    snapshot.settings.amountColorMode,
                )
                setTextViewText(R.id.widget_total_assets, total)
                setTextViewText(R.id.widget_month_income, income)
                setTextViewText(R.id.widget_month_expense, expense)
                setTextColor(R.id.widget_month_income, incomeColor)
                setTextColor(R.id.widget_month_expense, expenseColor)
                setContentDescription(
                    R.id.widget_total_assets,
                    context.getString(
                        R.string.widget_amount_semantics_format,
                        context.getString(R.string.widget_balance_overview_title),
                        total,
                    ),
                )
                setContentDescription(
                    R.id.widget_month_income,
                    context.getString(
                        R.string.widget_amount_semantics_format,
                        context.getString(R.string.widget_this_month_income),
                        income,
                    ),
                )
                setContentDescription(
                    R.id.widget_month_expense,
                    context.getString(
                        R.string.widget_amount_semantics_format,
                        context.getString(R.string.widget_this_month_expense),
                        expense,
                    ),
                )
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

        // Mirrors ui/theme/Color.kt MoneyColors so the widget matches in-app amount colors,
        // including the user-selectable income/expense color direction.
        private const val LIGHT_INCOME: Int = 0xFFA94442.toInt()
        private const val LIGHT_EXPENSE: Int = 0xFF2F6B4F.toInt()
        private const val DARK_INCOME: Int = 0xFFE57373.toInt()
        private const val DARK_EXPENSE: Int = 0xFF66BB6A.toInt()

        private fun widgetFlowColors(context: Context, mode: AmountColorMode): Pair<Int, Int> {
            val dark = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val income = if (dark) DARK_INCOME else LIGHT_INCOME
            val expense = if (dark) DARK_EXPENSE else LIGHT_EXPENSE
            return when (mode) {
                AmountColorMode.RED_INCOME_GREEN_EXPENSE -> income to expense
                AmountColorMode.GREEN_INCOME_RED_EXPENSE -> expense to income
            }
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
                setContentDescription(
                    R.id.widget_total_assets,
                    context.getString(R.string.widget_total_hidden_refreshing),
                )
                setContentDescription(
                    R.id.widget_month_income,
                    context.getString(R.string.widget_income_hidden_refreshing),
                )
                setContentDescription(
                    R.id.widget_month_expense,
                    context.getString(R.string.widget_expense_hidden_refreshing),
                )
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
