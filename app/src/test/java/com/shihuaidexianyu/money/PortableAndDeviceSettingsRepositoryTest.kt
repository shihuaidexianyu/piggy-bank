package com.shihuaidexianyu.money

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.shihuaidexianyu.money.data.repository.DevicePreferencesMapper
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.AppRelockDelay
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.HistoryFilters
import com.shihuaidexianyu.money.domain.model.MAX_RECENT_ACCOUNT_IDS
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PortableAndDeviceSettingsRepositoryTest {
    @Test
    fun `portable settings normalize currency and validate monthly budget`() = runBlocking {
        val repository = InMemoryPortableSettingsRepository()

        assertEquals(PortableSettings(), repository.query())
        repository.updateCurrencySymbol("  元人民币  ")
        repository.updateAmountColorMode(AmountColorMode.GREEN_INCOME_RED_EXPENSE)
        repository.updateMonthlyBudgetAmount(10_000L)

        assertEquals(
            PortableSettings(
                currencySymbol = "元人民币",
                amountColorMode = AmountColorMode.GREEN_INCOME_RED_EXPENSE,
                monthlyBudgetAmount = 10_000L,
            ),
            repository.observe().first(),
        )
        repository.updateMonthlyBudgetAmount(null)
        assertEquals(null, repository.query().monthlyBudgetAmount)
        assertFailsWith<IllegalArgumentException> { repository.updateMonthlyBudgetAmount(0L) }
        assertFailsWith<IllegalArgumentException> { repository.updateMonthlyBudgetAmount(-1L) }
        Unit
    }

    @Test
    fun `device preferences round trip every field and normalize recent accounts`() = runBlocking {
        val repository = InMemoryDevicePreferencesRepository()
        val expected = DevicePreferences(
            themeMode = ThemeMode.DARK,
            biometricLock = true,
            relockDelay = AppRelockDelay.FIVE_MINUTES,
            maskAmountsInApp = true,
            hideWidgetAmounts = true,
            hideNotificationAmounts = true,
            hideRecentTasks = true,
            notificationPermissionRequested = true,
            historyFilters = HistoryFilters(
                keyword = "咖啡",
                excludeKeyword = "公司",
                recordTypes = setOf("CASH_FLOW", "TRANSFER"),
                accountId = 9L,
                dateStartAt = 100L,
                dateEndAt = 200L,
                minAmountText = "1.00",
                maxAmountText = "20.00",
                amountDirection = "decrease",
            ),
            recentAccountIds = listOf(3L, 2L, 3L, -1L, 1L, 4L, 5L, 6L),
        )

        repository.replace(expected)

        assertEquals(
            expected.copy(recentAccountIds = listOf(3L, 2L, 1L, 4L, 5L).take(MAX_RECENT_ACCOUNT_IDS)),
            repository.query(),
        )
        assertEquals(repository.query(), repository.observe().first())
    }

    @Test
    fun `device preference mapper falls back from corrupt enums and normalizes history`() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey("theme_mode") to "unknown",
            stringPreferencesKey("relock_delay") to "corrupt",
            stringPreferencesKey("last_history_record_types") to "CASH_FLOW,INVALID,TRANSFER",
            longPreferencesKey("last_history_account_id") to -9L,
            longPreferencesKey("last_history_date_start_at") to -1L,
            longPreferencesKey("last_history_date_end_at") to 20L,
            stringPreferencesKey("recent_account_ids") to "3,2,3,-1,x,1,4,5,6",
            booleanPreferencesKey("biometric_lock") to true,
        )

        val result = DevicePreferencesMapper.fromPreferences(preferences)

        assertEquals(ThemeMode.SYSTEM, result.themeMode)
        assertEquals(AppRelockDelay.THIRTY_SECONDS, result.relockDelay)
        assertEquals(true, result.biometricLock)
        assertEquals(null, result.historyFilters.accountId)
        assertEquals(null, result.historyFilters.dateStartAt)
        assertEquals(20L, result.historyFilters.dateEndAt)
        assertEquals(setOf("CASH_FLOW", "TRANSFER"), result.historyFilters.recordTypes)
        assertEquals(listOf(3L, 2L, 1L, 4L, 5L), result.recentAccountIds)
    }

    @Test
    fun `device preference mapper persists advanced history record types`() {
        val preferences = mutablePreferencesOf()
        val expected = DevicePreferences(
            historyFilters = HistoryFilters(
                keyword = "工资",
                excludeKeyword = "内部",
                recordTypes = setOf("TRANSFER", "CASH_FLOW"),
                accountId = 7L,
                dateStartAt = 100L,
                dateEndAt = 200L,
                minAmountText = "1.00",
                maxAmountText = "9.00",
                amountDirection = "increase",
            ),
        )

        DevicePreferencesMapper.write(preferences, expected)

        assertEquals(expected, DevicePreferencesMapper.fromPreferences(preferences))
    }
}
