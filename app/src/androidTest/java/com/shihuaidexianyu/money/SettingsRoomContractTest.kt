package com.shihuaidexianyu.money

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shihuaidexianyu.money.data.db.MoneyDatabase
import com.shihuaidexianyu.money.data.repository.AccountReminderSettingsRepositoryImpl
import com.shihuaidexianyu.money.data.repository.AccountRepositoryImpl
import com.shihuaidexianyu.money.data.repository.PortableSettingsRepositoryImpl
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AmountColorMode
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.PortableSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRoomContractTest {
    private lateinit var database: MoneyDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, MoneyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun portableSettings_defaultFlowAndValidatedUpdates() = runBlocking {
        val repository = PortableSettingsRepositoryImpl(database, database.portableSettingsDao())
        assertEquals(PortableSettings(), repository.query())
        assertEquals(PortableSettings(), repository.observe().first())

        repository.updateCurrencySymbol("  元人民币  ")
        repository.updateAmountColorMode(AmountColorMode.GREEN_INCOME_RED_EXPENSE)
        repository.updateMonthlyBudgetAmount(10_000L)

        assertEquals(
            PortableSettings("元人民币", AmountColorMode.GREEN_INCOME_RED_EXPENSE, 10_000L),
            repository.query(),
        )
    }

    @Test
    fun reminderConfig_enforcesFkAndProvidesEnabledCasResetParity() = runBlocking {
        val repository = AccountReminderSettingsRepositoryImpl(database, database.accountReminderConfigDao())
        var fkFailure: Throwable? = null
        try {
            repository.updateReminderConfig(404L, BalanceUpdateReminderConfig())
        } catch (error: Throwable) {
            fkFailure = error
        }
        assertTrue(fkFailure != null)

        val accountId = AccountRepositoryImpl(database.accountDao()).createAccount(
            Account(name = "现金", initialBalance = 0L, createdAt = 1L),
        )
        repository.updateReminderConfig(accountId, BalanceUpdateReminderConfig())
        assertTrue(repository.compareAndSetLastNotifiedBoundary(accountId, null, 100L))
        assertFalse(repository.compareAndSetLastNotifiedBoundary(accountId, null, 200L))
        assertEquals(100L, repository.getReminderConfig(accountId).lastNotifiedBoundaryAt)
        repository.resetLastNotifiedBoundary(accountId)
        assertNull(repository.getReminderConfig(accountId).lastNotifiedBoundaryAt)
        repository.setEnabled(accountId, false)
        assertFalse(repository.getReminderConfig(accountId).isEnabled)
        assertEquals(setOf(accountId), repository.observeReminderConfigs().first().keys)
    }
}
