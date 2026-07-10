package com.shihuaidexianyu.money

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationIntentIdentity
import com.shihuaidexianyu.money.domain.notification.NotificationLaunchIdentity
import com.shihuaidexianyu.money.notification.NotificationLaunchIntentConsumer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationLaunchIntentConsumerTest {
    @Test
    fun consumedNotificationIdentityDoesNotReplayAfterActivityRecreate() {
        val intent = Intent(MoneyNotificationIntentIdentity.ACTION_RECURRING).apply {
            putExtra(MoneyNotificationIntentIdentity.EXTRA_REMINDER_ID, 7L)
            putExtra(MoneyNotificationIntentIdentity.EXTRA_EXPECTED_DUE_AT, 99L)
        }

        val first = NotificationLaunchIntentConsumer.consume(intent, token = 1L)
        val recreated = NotificationLaunchIntentConsumer.consume(intent, token = 2L)

        assertEquals(NotificationLaunchIdentity.Recurring(7L, 99L), first?.identity)
        assertNull(recreated)
    }
}
