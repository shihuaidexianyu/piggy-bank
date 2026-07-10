package com.shihuaidexianyu.money.notification

import android.content.Intent
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationIntentIdentity
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchIdentity
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchRequest

object NotificationLaunchIntentConsumer {
    fun consume(intent: Intent?, token: Long): NotificationLaunchRequest? {
        val identity = when (intent?.action) {
            MoneyNotificationIntentIdentity.ACTION_RECURRING -> {
                val reminderId = intent.getLongExtra(MoneyNotificationIntentIdentity.EXTRA_REMINDER_ID, -1L)
                val dueAt = intent.getLongExtra(MoneyNotificationIntentIdentity.EXTRA_EXPECTED_DUE_AT, -1L)
                clear(intent)
                if (reminderId <= 0 || dueAt <= 0) return null
                NotificationLaunchIdentity.Recurring(reminderId, dueAt)
            }
            MoneyNotificationIntentIdentity.ACTION_BALANCE -> {
                val accountId = intent.getLongExtra(MoneyNotificationIntentIdentity.EXTRA_ACCOUNT_ID, -1L)
                clear(intent)
                if (accountId <= 0) return null
                NotificationLaunchIdentity.Balance(accountId)
            }
            else -> return null
        }
        return NotificationLaunchRequest(token, identity)
    }

    private fun clear(intent: Intent) {
        intent.action = null
        intent.removeExtra(MoneyNotificationIntentIdentity.EXTRA_REMINDER_ID)
        intent.removeExtra(MoneyNotificationIntentIdentity.EXTRA_EXPECTED_DUE_AT)
        intent.removeExtra(MoneyNotificationIntentIdentity.EXTRA_ACCOUNT_ID)
    }
}
