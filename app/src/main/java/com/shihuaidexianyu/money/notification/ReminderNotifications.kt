package com.shihuaidexianyu.money.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.shihuaidexianyu.money.R

/**
 * Centralized notification channel + posting for due recurring reminders.
 * Created once on app start; [ReminderNotificationScheduler] posts notifications when due
 * reminders are found by [com.shihuaidexianyu.money.notification.RecurringReminderWorker].
 */
object ReminderNotifications {
    const val CHANNEL_ID = "recurring_reminders"
    const val CHANNEL_NAME = "到期提醒"

    const val BALANCE_CHECK_CHANNEL_ID = "balance_check_reminders"
    const val BALANCE_CHECK_CHANNEL_NAME = "余额核对提醒"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "周期性提醒到期时通知你"
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun ensureBalanceCheckChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(BALANCE_CHECK_CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            BALANCE_CHECK_CHANNEL_ID,
            BALANCE_CHECK_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "账户余额超过提醒周期未核对时通知你"
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Posts a notification for a single due recurring reminder. Returns the notification ID (same as
     * [reminderId]) so callers can cancel it later if desired.
     */
    fun postReminder(
        context: Context,
        reminderId: Long,
        name: String,
        amountText: String,
        accountName: String?,
    ): Int {
        ensureChannel(context)
        val title = if (accountName != null) "$name · $accountName" else name
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("待处理金额 $amountText")
            .setStyle(NotificationCompat.BigTextStyle().bigText("提醒「$name」已到期，待处理金额 $amountText。"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val notificationId = reminderId.toInt()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip. The in-app reminder list still shows it.
        }
        return notificationId
    }

    /**
     * Posts a "余额待核对" notification for a single account. Uses a separate low-importance
     * channel so the user can tune vibration/sound independently from due-reminder notifications.
     *
     * Notification ID is offset by [BALANCE_CHECK_NOTIFICATION_ID_OFFSET] to avoid collision with
     * recurring-reminder notification IDs (which use the reminder ID directly).
     */
    fun postBalanceCheckReminder(
        context: Context,
        accountId: Long,
        accountName: String,
        balanceText: String,
        reminderScheduleText: String,
    ): Int {
        ensureBalanceCheckChannel(context)
        val notification = NotificationCompat.Builder(context, BALANCE_CHECK_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("$accountName 余额待核对")
            .setContentText("当前余额 $balanceText（$reminderScheduleText）")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("账户「$accountName」已超过提醒周期（$reminderScheduleText）未核对余额，当前余额 $balanceText。打开应用核对后此提醒会自动消失。"),
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        val notificationId = (accountId + BALANCE_CHECK_NOTIFICATION_ID_OFFSET).toInt()
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted — silently skip.
        }
        return notificationId
    }

    const val BALANCE_CHECK_NOTIFICATION_ID_OFFSET = 100_000L
}
