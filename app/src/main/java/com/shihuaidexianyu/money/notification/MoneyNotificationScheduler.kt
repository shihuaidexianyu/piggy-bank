package com.shihuaidexianyu.money.notification

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.core.app.NotificationManagerCompat
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import java.util.concurrent.TimeUnit

object NotificationWorkContract {
    const val PERIODIC_WORK_NAME = "money-notification-sync-v2"
    const val IMMEDIATE_WORK_NAME = "money-notification-immediate-sync-v2"
    const val IMMEDIATE_DELAY_MILLIS = 1_000L
    const val PERIODIC_INTERVAL_MINUTES = 15L
    val PERIODIC_POLICY = ExistingPeriodicWorkPolicy.UPDATE
    val IMMEDIATE_POLICY = ExistingWorkPolicy.REPLACE
    val CONTINUATION_POLICY = ExistingWorkPolicy.APPEND_OR_REPLACE
    val LEGACY_UNIQUE_WORK_NAMES = setOf("recurring-reminder-check", "balance-check")
}

class WorkManagerNotificationSyncRequester(context: Context) : NotificationSyncRequester {
    private val appContext = context.applicationContext

    override fun request(reason: NotificationSyncReason) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<MoneyNotificationWorker>()
                .setInitialDelay(NotificationWorkContract.IMMEDIATE_DELAY_MILLIS, TimeUnit.MILLISECONDS)
                .addTag("money:notification:reason:${reason.name.lowercase()}")
                .build()
            WorkManager.getInstance(appContext).enqueueUniqueWork(
                NotificationWorkContract.IMMEDIATE_WORK_NAME,
                if (reason == NotificationSyncReason.CONTINUATION) {
                    NotificationWorkContract.CONTINUATION_POLICY
                } else {
                    NotificationWorkContract.IMMEDIATE_POLICY
                },
                request,
            )
        }
    }
}

object MoneyNotificationScheduler {
    fun scheduleAfterReady(
        context: Context,
        legacyReminderIds: Collection<Long>,
        legacyAccountIds: Collection<Long>,
        requester: NotificationSyncRequester,
    ) {
        val workManager = WorkManager.getInstance(context)
        NotificationWorkContract.LEGACY_UNIQUE_WORK_NAMES.forEach(workManager::cancelUniqueWork)
        LegacyNotificationUpgradeCleaner.cleanup(
            context = context,
            reminderIds = legacyReminderIds,
            accountIds = legacyAccountIds,
        )
        val periodic = PeriodicWorkRequestBuilder<MoneyNotificationWorker>(
            NotificationWorkContract.PERIODIC_INTERVAL_MINUTES,
            TimeUnit.MINUTES,
        ).build()
        workManager.enqueueUniquePeriodicWork(
            NotificationWorkContract.PERIODIC_WORK_NAME,
            NotificationWorkContract.PERIODIC_POLICY,
            periodic,
        )
        runCatching { requester.request(NotificationSyncReason.APP_START) }
    }
}

fun isLegacyUntaggedMoneyNotification(tag: String?, channelId: String?): Boolean =
    tag == null && channelId in setOf(
        AndroidMoneyNotificationPublisher.RECURRING_CHANNEL_ID,
        AndroidMoneyNotificationPublisher.BALANCE_CHANNEL_ID,
    )

private object LegacyNotificationUpgradeCleaner {
    private const val LEGACY_BALANCE_ID_OFFSET = 100_000L

    fun cleanup(
        context: Context,
        reminderIds: Collection<Long>,
        accountIds: Collection<Long>,
    ) {
        val compat = NotificationManagerCompat.from(context)
        reminderIds.forEach { compat.cancel(it.toInt()) }
        accountIds.forEach { compat.cancel((it + LEGACY_BALANCE_ID_OFFSET).toInt()) }
        context.getSystemService(NotificationManager::class.java)
            ?.activeNotifications
            .orEmpty()
            .filter { status ->
                isLegacyUntaggedMoneyNotification(status.tag, status.notification.channelId)
            }
            .forEach { status -> compat.cancel(status.id) }
        LegacyExactAlarmCleaner.cancelKnown(context, reminderIds)
    }
}

private object LegacyExactAlarmCleaner {
    private const val LEGACY_RECEIVER =
        "com.shihuaidexianyu.money.notification.ReminderAlarmReceiver"
    private const val LEGACY_ACTION = "com.shihuaidexianyu.money.REMINDER_DUE"

    fun cancelKnown(context: Context, reminderIds: Collection<Long>) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        reminderIds.forEach { reminderId ->
            val intent = Intent(LEGACY_ACTION).setComponent(ComponentName(context.packageName, LEGACY_RECEIVER))
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return@forEach
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}
