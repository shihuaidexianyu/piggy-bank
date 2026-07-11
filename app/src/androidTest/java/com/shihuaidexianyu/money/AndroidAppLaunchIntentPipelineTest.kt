package com.shihuaidexianyu.money

import android.content.ClipData
import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shihuaidexianyu.money.domain.launch.AppLaunchDestination
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequestQueue
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationIntentIdentity
import com.shihuaidexianyu.money.ui.launch.AndroidAppLaunchIntentParser
import com.shihuaidexianyu.money.widget.BalanceOverviewWidgetProvider
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
        val firstNew = shareIntent("支付 ￥１，２３４．５６")
        val secondNew = Intent(BalanceOverviewWidgetProvider.ACTION_OPEN_WIDGET_HOME).apply {
            data = "money://widget/home".toUri()
        }
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
    fun shortcutShareWidgetAndNotificationMapOnlyToTypedPayloads() {
        assertEquals(
            AppLaunchDestination.Transfer,
            AndroidAppLaunchIntentParser.parse(shortcutIntent("record_transfer"), "shortcut")?.destination,
        )
        assertEquals(
            AppLaunchDestination.SharePreview("收入 ¥88.00"),
            AndroidAppLaunchIntentParser.parse(shareIntent("收入 ¥88.00"), "share")?.destination,
        )
        assertEquals(
            AppLaunchDestination.Home,
            AndroidAppLaunchIntentParser.parse(
                Intent(BalanceOverviewWidgetProvider.ACTION_OPEN_WIDGET_HOME),
                "widget",
            )?.destination,
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

    private fun shareIntent(text: String) = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        data = "content://private/share".toUri()
        clipData = ClipData.newPlainText("shared", text)
        putExtra(Intent.EXTRA_TEXT, text)
    }

    private fun assertCleared(intent: Intent) {
        assertNull(intent.action)
        assertNull(intent.data)
        assertNull(intent.type)
        assertNull(intent.clipData)
        assertTrue(intent.extras == null || intent.extras!!.isEmpty)
    }
}
