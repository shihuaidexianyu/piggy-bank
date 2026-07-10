package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountReminderConfigRepositoryTest {
    @Test
    fun `config supports enabled state and notification boundary CAS`() = runBlocking {
        val repository = InMemoryAccountReminderSettingsRepository()
        val schedule = BalanceUpdateReminderConfig(
            period = BalanceUpdateReminderPeriod.MONTHLY,
            monthDay = 20,
            isEnabled = true,
        )

        repository.updateReminderConfig(7L, schedule)
        assertEquals(schedule, repository.getReminderConfig(7L))
        assertEquals(schedule, repository.observeReminderConfigs().first().getValue(7L))

        assertTrue(repository.compareAndSetLastNotifiedBoundary(7L, expected = null, newValue = 100L))
        assertFalse(repository.compareAndSetLastNotifiedBoundary(7L, expected = null, newValue = 200L))
        assertEquals(100L, repository.getReminderConfig(7L).lastNotifiedBoundaryAt)

        repository.resetLastNotifiedBoundary(7L)
        assertNull(repository.getReminderConfig(7L).lastNotifiedBoundaryAt)
        repository.setEnabled(7L, false)
        assertFalse(repository.getReminderConfig(7L).isEnabled)
        repository.setEnabled(7L, true)
        assertTrue(repository.getReminderConfig(7L).isEnabled)
    }

    @Test
    fun `schedule update preserves dedupe boundary and portable replacement resets it`() = runBlocking {
        val repository = InMemoryAccountReminderSettingsRepository()
        repository.updateReminderConfig(3L, BalanceUpdateReminderConfig())
        assertTrue(repository.compareAndSetLastNotifiedBoundary(3L, null, 99L))

        repository.updateReminderConfig(
            3L,
            BalanceUpdateReminderConfig(period = BalanceUpdateReminderPeriod.MONTHLY),
        )
        assertEquals(99L, repository.getReminderConfig(3L).lastNotifiedBoundaryAt)

        repository.replaceReminderConfigs(
            mapOf(3L to BalanceUpdateReminderConfig(period = BalanceUpdateReminderPeriod.MONTHLY)),
        )
        assertNull(repository.getReminderConfig(3L).lastNotifiedBoundaryAt)
    }
}
