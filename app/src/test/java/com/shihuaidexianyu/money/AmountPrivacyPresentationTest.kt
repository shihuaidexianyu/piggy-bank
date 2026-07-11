package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.domain.model.failClosedDevicePreferences
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationCommand
import com.shihuaidexianyu.money.notification.DefaultMoneyNotificationContentPolicy
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.ui.reminder.partitionReminderModels
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmountPrivacyPresentationTest {
    @Test
    fun `shared amount formatter masks value without leaking digits`() {
        val rendered = AmountFormatter.format(
            amountInMinor = 1_234_567L,
            settings = PortableSettings(currencySymbol = "¥"),
            visibility = AmountVisibility.MASKED,
        )

        assertTrue(rendered.contains("¥"))
        assertFalse(rendered.any(Char::isDigit))
        assertFalse(rendered.contains("12,345.67"))
    }

    @Test
    fun `device preference read failure fallback keeps every presentation surface private`() {
        val fallback = failClosedDevicePreferences()
        assertTrue(fallback.maskAmountsInApp)
        assertTrue(fallback.hideRecentTasks)
        assertTrue(fallback.hideWidgetAmounts)
        assertTrue(fallback.hideNotificationAmounts)
    }

    @Test
    fun `notification privacy masks every private text variant`() = runBlocking {
        val policy = DefaultMoneyNotificationContentPolicy(
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(
                DevicePreferences(hideNotificationAmounts = true),
            ),
        )

        val content = policy.content(
            MoneyNotificationCommand.Recurring(
                reminderId = 8L,
                expectedDueAt = 10L,
                reminderName = "房租",
                accountName = "现金",
                amount = 1_234_567L,
            ),
        )
        val privateText = listOf(content.title, content.text, content.expandedText).joinToString()

        assertFalse(privateText.contains("12,345.67"))
        assertFalse(privateText.contains("1234567"))
        assertFalse(privateText.any(Char::isDigit), "masked notification text must not expose amount digits")
    }

    @Test
    fun `reminder projection masks in app preformatted amount`() {
        val reminder = RecurringReminder(
            id = 1L,
            name = "测试",
            type = "manual",
            accountId = 1L,
            direction = "outflow",
            amount = 1_234_567L,
            periodType = "custom_days",
            periodValue = 1,
            periodMonth = null,
            nextDueAt = 10L,
            anchorDueAt = 10L,
            isEnabled = true,
            createdAt = 1L,
            updatedAt = 1L,
        )

        val projection = partitionReminderModels(
            reminders = listOf(reminder),
            settings = PortableSettings(),
            nowMillis = 20L,
            zoneId = ZoneId.of("Asia/Shanghai"),
            amountVisibility = AmountVisibility.MASKED,
        )

        assertFalse(projection.due.single().amountFormatted.any(Char::isDigit))
    }
}
