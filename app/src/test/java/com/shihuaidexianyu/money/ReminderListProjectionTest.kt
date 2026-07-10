package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.RecurringReminder
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
