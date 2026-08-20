package com.shihuaidexianyu.money.ui.launch

import android.content.Intent
import android.os.Bundle
import com.shihuaidexianyu.money.domain.launch.AppLaunchInput
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequest
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequestFactory
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchIdentity
import com.shihuaidexianyu.money.notification.NotificationLaunchIntentConsumer

object AndroidAppLaunchIntentParser {
    fun parse(intent: Intent?, token: String): AppLaunchRequest? {
        if (intent == null) return null
        val external = intent.hasExtra(SHORTCUT_ACTION_EXTRA) ||
            intent.action == com.shihuaidexianyu.money.domain.notification.MoneyNotificationIntentIdentity.ACTION_RECURRING ||
            intent.action == com.shihuaidexianyu.money.domain.notification.MoneyNotificationIntentIdentity.ACTION_BALANCE
        if (!external) return null
        return try {
        val notification = NotificationLaunchIntentConsumer.consume(intent, 1L)?.identity
        val input = when (notification) {
            is NotificationLaunchIdentity.Recurring -> AppLaunchInput.RecurringNotification(
                reminderId = notification.reminderId,
                expectedDueAt = notification.expectedDueAt,
            )
            is NotificationLaunchIdentity.Balance -> AppLaunchInput.BalanceNotification(
                accountId = notification.accountId,
            )
            null -> when {
                intent.hasExtra(SHORTCUT_ACTION_EXTRA) -> intent
                    .getStringExtra(SHORTCUT_ACTION_EXTRA)
                    ?.let(AppLaunchInput::Shortcut)
                    ?: return null
                else -> return null
            }
        }
            AppLaunchRequestFactory.create(token, input)
        } finally {
            clearExternalSource(intent)
        }
    }

    private fun clearExternalSource(intent: Intent) {
        intent.action = null
        intent.data = null
        intent.type = null
        intent.clipData = null
        intent.replaceExtras(Bundle())
    }

    const val SHORTCUT_ACTION_EXTRA = "shortcut_action"
}
