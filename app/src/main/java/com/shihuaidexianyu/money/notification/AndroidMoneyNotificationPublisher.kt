package com.shihuaidexianyu.money.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.shihuaidexianyu.money.MainActivity
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationCommand
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationContent
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationContentPolicy
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationIntentIdentity
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationKey
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationPublisher
import com.shihuaidexianyu.money.domain.notification.NotificationCapability
import com.shihuaidexianyu.money.domain.notification.PublishResult
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.util.AmountFormatter
import kotlinx.coroutines.CancellationException

class DefaultMoneyNotificationContentPolicy(
    private val portableSettingsRepository: PortableSettingsRepository,
) : MoneyNotificationContentPolicy {
    override suspend fun content(command: MoneyNotificationCommand): MoneyNotificationContent {
        val settings = portableSettingsRepository.query()
        return when (command) {
            is MoneyNotificationCommand.Recurring -> {
                val amount = AmountFormatter.format(command.amount, settings)
                MoneyNotificationContent(
                    title = "${command.reminderName} · ${command.accountName}",
                    text = "待处理金额 $amount",
                    expandedText = "提醒「${command.reminderName}」已到期，待处理金额 $amount。",
                    publicTitle = "记账提醒",
                    publicText = "有一项提醒待处理",
                )
            }
            is MoneyNotificationCommand.Balance -> {
                val amount = AmountFormatter.format(command.balance, settings)
                MoneyNotificationContent(
                    title = "${command.accountName} 余额待核对",
                    text = "当前余额 $amount（${command.scheduleText}）",
                    expandedText = "账户「${command.accountName}」已超过提醒周期未核对余额，请打开应用处理。",
                    publicTitle = "余额核对提醒",
                    publicText = "有一个账户需要核对余额",
                )
            }
        }
    }
}

class AndroidMoneyNotificationPublisher(
    context: Context,
    private val contentPolicy: MoneyNotificationContentPolicy,
) : MoneyNotificationPublisher {
    private val appContext = context.applicationContext
    private val notificationManager = appContext.getSystemService(NotificationManager::class.java)

    override fun capability(key: MoneyNotificationKey): NotificationCapability {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return NotificationCapability.PermissionDenied
        }
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            return NotificationCapability.ApplicationDisabled
        }
        val channelId = channelId(key)
        val channel = notificationManager?.getNotificationChannel(channelId)
        if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) {
            return NotificationCapability.ChannelDisabled
        }
        return NotificationCapability.Allowed
    }

    override suspend fun post(command: MoneyNotificationCommand): PublishResult {
        ensureChannels(appContext)
        if (capability(command.key) != NotificationCapability.Allowed) return PublishResult.NotAllowed
        return try {
            val content = contentPolicy.content(command)
            val contentIntent = contentIntent(command)
            val publicVersion = NotificationCompat.Builder(appContext, channelId(command.key))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(content.publicTitle)
                .setContentText(content.publicText)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(contentIntent)
                .build()
            val notification = NotificationCompat.Builder(appContext, channelId(command.key))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(content.title)
                .setContentText(content.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content.expandedText))
                .setPriority(
                    if (command.key is MoneyNotificationKey.Recurring) {
                        NotificationCompat.PRIORITY_DEFAULT
                    } else {
                        NotificationCompat.PRIORITY_LOW
                    },
                )
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .build()
            NotificationManagerCompat.from(appContext).notify(
                command.key.tag,
                command.key.notificationId,
                notification,
            )
            PublishResult.Posted
        } catch (error: CancellationException) {
            throw error
        } catch (_: SecurityException) {
            PublishResult.NotAllowed
        } catch (_: Throwable) {
            PublishResult.Failed
        }
    }

    override fun cancel(key: MoneyNotificationKey) {
        NotificationManagerCompat.from(appContext).cancel(key.tag, key.notificationId)
    }

    override fun activeKeys(): Set<MoneyNotificationKey> =
        notificationManager?.activeNotifications.orEmpty().mapNotNullTo(mutableSetOf()) { status ->
            parseKey(status.tag, status.id)
        }

    private fun contentIntent(command: MoneyNotificationCommand): PendingIntent {
        val identity = MoneyNotificationIntentIdentity.from(command)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = identity.action
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data = when (val key = command.key) {
                is MoneyNotificationKey.Recurring ->
                    "money://notification/recurring/${key.reminderId}".toUri()
                is MoneyNotificationKey.Balance ->
                    "money://notification/balance/${key.accountId}".toUri()
            }
            identity.longExtras.forEach(::putExtra)
        }
        return PendingIntent.getActivity(
            appContext,
            command.key.tag.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun channelId(key: MoneyNotificationKey): String = when (key) {
        is MoneyNotificationKey.Recurring -> RECURRING_CHANNEL_ID
        is MoneyNotificationKey.Balance -> BALANCE_CHANNEL_ID
    }

    private fun parseKey(tag: String?, id: Int): MoneyNotificationKey? {
        if (tag == null) return null
        return when {
            id == 1 && tag.startsWith(RECURRING_TAG_PREFIX) ->
                tag.removePrefix(RECURRING_TAG_PREFIX).toLongOrNull()?.let(MoneyNotificationKey::Recurring)
            id == 2 && tag.startsWith(BALANCE_TAG_PREFIX) ->
                tag.removePrefix(BALANCE_TAG_PREFIX).toLongOrNull()?.let(MoneyNotificationKey::Balance)
            else -> null
        }
    }

    companion object {
        const val RECURRING_CHANNEL_ID = "recurring_reminders"
        const val BALANCE_CHANNEL_ID = "balance_check_reminders"
        private const val RECURRING_TAG_PREFIX = "money:recurring:"
        private const val BALANCE_TAG_PREFIX = "money:balance:"

        fun ensureChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(RECURRING_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        RECURRING_CHANNEL_ID,
                        "到期提醒",
                        NotificationManager.IMPORTANCE_DEFAULT,
                    ).apply {
                        description = "周期性提醒到期时通知你"
                        enableVibration(true)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    },
                )
            }
            if (manager.getNotificationChannel(BALANCE_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        BALANCE_CHANNEL_ID,
                        "余额核对提醒",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = "账户余额超过提醒周期未核对时通知你"
                        enableVibration(false)
                        lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                    },
                )
            }
        }
    }
}
