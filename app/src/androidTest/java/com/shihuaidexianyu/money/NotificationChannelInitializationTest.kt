package com.shihuaidexianyu.money

import android.app.NotificationManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.notification.AndroidMoneyNotificationPublisher
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationChannelInitializationTest {
    @Test
    fun ensureChannelsCreatesBothChannelsWhenTheyAreInitiallyMissing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel(AndroidMoneyNotificationPublisher.RECURRING_CHANNEL_ID)
        manager.deleteNotificationChannel(AndroidMoneyNotificationPublisher.BALANCE_CHANNEL_ID)

        AndroidMoneyNotificationPublisher.ensureChannels(context)

        assertNotNull(
            manager.getNotificationChannel(AndroidMoneyNotificationPublisher.RECURRING_CHANNEL_ID),
        )
        assertNotNull(
            manager.getNotificationChannel(AndroidMoneyNotificationPublisher.BALANCE_CHANNEL_ID),
        )
    }
}
