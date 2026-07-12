package com.shihuaidexianyu.money.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.room.withTransaction
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.di.SystemClockProvider
import com.shihuaidexianyu.money.di.SystemZoneIdProvider
import com.shihuaidexianyu.money.domain.model.AmountPrivacy
import com.shihuaidexianyu.money.domain.model.AmountSurface
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.usecase.TimeRangeCalculator
import com.shihuaidexianyu.money.notification.MoneyAppContainerProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException

class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val widgetIds = BalanceOverviewWidgetProvider.widgetIds(applicationContext)
        if (widgetIds.isEmpty()) return Result.success()

        val container = (applicationContext.applicationContext as? MoneyAppContainerProvider)
            ?.moneyAppContainer ?: return Result.retry()
        val startupState = container.startupMigrationCoordinator.state.first {
            it != StartupMigrationState.Loading
        }
        if (startupState != StartupMigrationState.Ready) return Result.retry()

        val manager = AppWidgetManager.getInstance(applicationContext)
        val renderGeneration = WidgetPrivacyGeneration.snapshot().generation
        return try {
            WidgetRefreshCoordinator(
                snapshotSource = {
                    val snapshotTimeMillis = SystemClockProvider.nowMillis()
                    val snapshotZoneId = SystemZoneIdProvider.zoneId()
                    val devicePreferences = container.devicePreferencesRepository.query()
                    val visibility = AmountPrivacy.from(devicePreferences)
                        .visibilityFor(AmountSurface.WIDGET)
                    container.moneyDatabase.withTransaction {
                        val accounts = container.accountRepository.queryAllAccounts()
                        val balances = container.calculateAccountBalancesUseCase(
                            accounts,
                            snapshotTimeMillis,
                        )
                        val totalAssets = balances.values.ledgerSumExact()
                        val range = TimeRangeCalculator.currentMonthRange(
                            zoneId = snapshotZoneId,
                            nowMillis = snapshotTimeMillis,
                        )
                        val periodSummary = container.transactionRepository.queryHomePeriodLedgerSummary(
                            range.startInclusive,
                            range.endExclusive,
                        )
                        WidgetBalanceSnapshot(
                            totalAssets = totalAssets,
                            monthInflow = periodSummary.cashInflow,
                            monthOutflow = periodSummary.cashOutflow,
                            settings = container.portableSettingsRepository.query(),
                            visibility = visibility,
                        )
                    }
                },
                renderer = { widgetId, value ->
                    WidgetPrivacyGeneration.renderAtomically(
                        capturedGeneration = renderGeneration,
                        snapshotIsMasked = value.visibility ==
                            com.shihuaidexianyu.money.domain.model.AmountVisibility.MASKED,
                        renderSnapshot = {
                            BalanceOverviewWidgetProvider.renderSnapshot(
                                context = applicationContext,
                                manager = manager,
                                widgetId = widgetId,
                                snapshot = value,
                            )
                        },
                        renderSafe = {
                            BalanceOverviewWidgetProvider.renderSafePlaceholder(
                                applicationContext,
                                manager,
                                widgetId,
                            )
                        },
                    )
                },
                safeRenderer = { widgetId ->
                    BalanceOverviewWidgetProvider.renderSafePlaceholder(
                        applicationContext,
                        manager,
                        widgetId,
                    )
                },
                beforeRender = { snapshot ->
                    val latestPrivacy = AmountPrivacy.from(
                        container.devicePreferencesRepository.query(),
                    ).visibilityFor(AmountSurface.WIDGET)
                    snapshot.copy(visibility = latestPrivacy)
                },
            ).refresh(widgetIds)
            Result.success()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}
