package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.notification.MoneyNotificationCommand
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationIntentIdentity
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationKey
import com.shihuaidexianyu.money.notification.NotificationWorkContract
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.notification.DefaultMoneyNotificationContentPolicy
import com.shihuaidexianyu.money.notification.isLegacyUntaggedMoneyNotification
import kotlinx.coroutines.runBlocking
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

class MoneyNotificationContractTest {
    @Test
    fun `stable notification tags use small fixed ids`() {
        assertEquals("money:recurring:42", MoneyNotificationKey.Recurring(42).tag)
        assertEquals(1, MoneyNotificationKey.Recurring(42).notificationId)
        assertEquals("money:balance:9", MoneyNotificationKey.Balance(9).tag)
        assertEquals(2, MoneyNotificationKey.Balance(9).notificationId)
    }

    @Test
    fun `recurring deep link contains identity only`() {
        val identity = MoneyNotificationIntentIdentity.from(
            MoneyNotificationCommand.Recurring(
                reminderId = 42,
                expectedDueAt = 1234,
                reminderName = "private name",
                accountName = "private account",
                amount = 999_00,
            ),
        )

        assertEquals(mapOf("reminder_id" to 42L, "expected_due_at" to 1234L), identity.longExtras)
        assertFalse(identity.longExtras.keys.any { it.contains("amount") || it.contains("name") })
    }

    @Test
    fun `work names and debounce remain stable`() {
        assertEquals("money-notification-sync-v2", NotificationWorkContract.PERIODIC_WORK_NAME)
        assertEquals("money-notification-immediate-sync-v2", NotificationWorkContract.IMMEDIATE_WORK_NAME)
        assertEquals(1_000L, NotificationWorkContract.IMMEDIATE_DELAY_MILLIS)
        assertEquals(15L, NotificationWorkContract.PERIODIC_INTERVAL_MINUTES)
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, NotificationWorkContract.PERIODIC_POLICY)
        assertEquals(ExistingWorkPolicy.REPLACE, NotificationWorkContract.IMMEDIATE_POLICY)
        assertEquals(
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            NotificationWorkContract.CONTINUATION_POLICY,
        )
        assertEquals(
            setOf("recurring-reminder-check", "balance-check"),
            NotificationWorkContract.LEGACY_UNIQUE_WORK_NAMES,
        )
    }

    @Test
    fun `public lock screen content never includes account reminder or amount`() = runBlocking {
        val content = DefaultMoneyNotificationContentPolicy(InMemoryPortableSettingsRepository()).content(
            MoneyNotificationCommand.Recurring(
                reminderId = 1,
                expectedDueAt = 2,
                reminderName = "private-reminder",
                accountName = "private-account",
                amount = 987_65,
            ),
        )

        assertTrue(content.title.contains("private-reminder"))
        assertTrue(content.text.contains("987.65"))
        assertFalse(content.publicTitle.contains("private"))
        assertFalse(content.publicText.contains("private"))
        assertFalse(content.publicText.contains("987"))
    }

    @Test
    fun `upgrade cleanup identifies only untagged notifications from legacy money channels`() {
        assertTrue(isLegacyUntaggedMoneyNotification(null, "recurring_reminders"))
        assertTrue(isLegacyUntaggedMoneyNotification(null, "balance_check_reminders"))
        assertFalse(isLegacyUntaggedMoneyNotification("money:recurring:1", "recurring_reminders"))
        assertFalse(isLegacyUntaggedMoneyNotification(null, "unrelated"))
    }
}
