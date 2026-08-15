package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.ReminderPeriodType
import com.shihuaidexianyu.money.ui.reminder.partitionReminderModels
import com.shihuaidexianyu.money.navigation.reminderCashFlowRoute
import java.time.ZoneId
import kotlin.test.assertEquals
import org.junit.Test

class ReminderListProjectionTest {
    @Test
    fun `reminders are partitioned by exact due boundary and stably sorted`() {
        val now = 10_000L
        val projection = partitionReminderModels(
            reminders = listOf(
                reminder(id = 5, dueAt = now + 1, enabled = false),
                reminder(id = 4, dueAt = now + 2),
                reminder(id = 3, dueAt = now),
                reminder(id = 2, dueAt = now - 1),
                reminder(id = 1, dueAt = now),
            ),
            settings = PortableSettings(),
            nowMillis = now,
            zoneId = ZoneId.of("UTC"),
        )

        assertEquals(listOf(2L, 1L, 3L), projection.due.map { it.id })
        assertEquals(listOf(4L), projection.upcoming.map { it.id })
        assertEquals(listOf(5L), projection.paused.map { it.id })
        assertEquals(true, projection.due.last().isOverdue)
        assertEquals(true, reminderCashFlowRoute(projection.due.last()).contains("expectedDueAt=$now"))
    }

    @Test
    fun `reminders linked to closed accounts are projected read only`() {
        val projection = partitionReminderModels(
            reminders = listOf(
                reminder(id = 1, dueAt = 10_000L, enabled = false),
                reminder(id = 2, dueAt = 10_000L, enabled = false).copy(accountId = 2L),
            ),
            settings = PortableSettings(),
            nowMillis = 10_000L,
            zoneId = ZoneId.of("UTC"),
            closedAccountIds = setOf(1L),
        )

        assertEquals(false, projection.paused.first { it.id == 1L }.canMutate)
        assertEquals(true, projection.paused.first { it.id == 2L }.canMutate)
    }

    @Test
    fun `structured period fields are projected for list formatting`() {
        val projection = partitionReminderModels(
            reminders = listOf(
                reminder(id = 1, dueAt = 20_000L).copy(periodType = "monthly", periodValue = 15),
                reminder(id = 2, dueAt = 20_001L).copy(periodType = "yearly", periodValue = 9, periodMonth = 6),
                reminder(id = 3, dueAt = 20_002L).copy(periodType = "custom_days", periodValue = 30),
            ),
            settings = PortableSettings(),
            nowMillis = 10_000L,
            zoneId = ZoneId.of("UTC"),
        )

        val monthly = projection.upcoming.first { it.id == 1L }
        assertEquals(ReminderPeriodType.MONTHLY, monthly.periodType)
        assertEquals(15, monthly.periodValue)
        assertEquals(null, monthly.periodMonth)
        val yearly = projection.upcoming.first { it.id == 2L }
        assertEquals(ReminderPeriodType.YEARLY, yearly.periodType)
        assertEquals(9, yearly.periodValue)
        assertEquals(6, yearly.periodMonth)
        val custom = projection.upcoming.first { it.id == 3L }
        assertEquals(ReminderPeriodType.CUSTOM_DAYS, custom.periodType)
        assertEquals(30, custom.periodValue)
    }

    private fun reminder(id: Long, dueAt: Long, enabled: Boolean = true) = RecurringReminder(
        id = id,
        name = "reminder-$id",
        type = "manual",
        accountId = 1,
        direction = "outflow",
        amount = 100,
        periodType = "custom_days",
        periodValue = 1,
        periodMonth = null,
        nextDueAt = dueAt,
        anchorDueAt = dueAt,
        isEnabled = enabled,
        createdAt = 1,
        updatedAt = 1,
    )
}
