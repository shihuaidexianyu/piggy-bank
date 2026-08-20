package com.shihuaidexianyu.money

import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shihuaidexianyu.money.domain.launch.AppLaunchDestination
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequestQueue
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationIntentIdentity
import com.shihuaidexianyu.money.ui.launch.AndroidAppLaunchIntentParser
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

@RunWith(AndroidJUnit4::class)
class AndroidAppLaunchIntentPipelineTest {
    @Test
    fun initialAndMultipleNewIntentsAreFifoOnceAndSourcesAreCleared() {
        val initial = shortcutIntent("record_outflow")
        val firstNew = shortcutIntent("record_transfer")
        val secondNew = shortcutIntent("balance_check")
        val queue = AppLaunchRequestQueue()
        listOf(initial, firstNew, secondNew).mapIndexed { index, source ->
            requireNotNull(AndroidAppLaunchIntentParser.parse(source, "token-$index"))
        }.forEach(queue::offer)

        assertEquals(listOf("token-0", "token-1", "token-2"), queue.pending.value.map { it.token })
        listOf(initial, firstNew, secondNew).forEach(::assertCleared)
        assertNull(AndroidAppLaunchIntentParser.parse(initial, "recreate"))

        listOf("token-0", "token-1", "token-2").forEach(queue::acknowledge)
        assertTrue(queue.pending.value.isEmpty())
    }

    @Test
    fun shortcutAndNotificationMapOnlyToTypedPayloads() {
        assertEquals(
            AppLaunchDestination.Transfer,
            AndroidAppLaunchIntentParser.parse(shortcutIntent("record_transfer"), "shortcut")?.destination,
        )
        val notification = Intent(MoneyNotificationIntentIdentity.ACTION_RECURRING).apply {
            data = "money://notification/recurring/9".toUri()
            putExtra(MoneyNotificationIntentIdentity.EXTRA_REMINDER_ID, 9L)
            putExtra(MoneyNotificationIntentIdentity.EXTRA_EXPECTED_DUE_AT, 99L)
            putExtra("sensitive_name", "不应保留")
        }
        assertEquals(
            AppLaunchDestination.RecurringNotification(9L, 99L),
            AndroidAppLaunchIntentParser.parse(notification, "notification")?.destination,
        )
        assertCleared(notification)
    }

    private fun shortcutIntent(value: String) = Intent(Intent.ACTION_VIEW).apply {
        putExtra(AndroidAppLaunchIntentParser.SHORTCUT_ACTION_EXTRA, value)
    }

    private fun assertCleared(intent: Intent) {
        assertNull(intent.action)
        assertNull(intent.data)
        assertNull(intent.type)
        assertNull(intent.clipData)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
    }
}
