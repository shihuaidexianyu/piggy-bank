package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryRecurringReminderRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderPeriod
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationCommand
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationKey
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationPublisher
import com.shihuaidexianyu.money.domain.notification.NotificationCapability
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.PublishResult
import com.shihuaidexianyu.money.domain.usecase.SyncMoneyNotificationsUseCase
import com.shihuaidexianyu.money.domain.usecase.SkipReminderUseCase
import com.shihuaidexianyu.money.domain.usecase.UndoSkipReminderUseCase
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncMoneyNotificationsUseCaseTest {
    @Test
    fun `due occurrence posts once and acknowledges exact due cursor`() = runBlocking {
        val fixture = Fixture()
        val reminderId = fixture.addReminder(nextDueAt = NOW - 1)

        fixture.sync()
        fixture.sync()

        assertEquals(1, fixture.publisher.posted.size)
        assertEquals(
            NOW - 1,
            fixture.reminders.getReminderById(reminderId)?.lastNotifiedDueAt,
        )
    }

    @Test
    fun `undoing a notified skipped occurrence allows it to be posted again`() = runBlocking {
        val fixture = Fixture()
        val dueAt = NOW - 1
        val reminderId = fixture.addReminder(nextDueAt = dueAt)

        fixture.sync()
        val token = fixture.skip(reminderId, dueAt)
        fixture.undo(token)
        fixture.sync()

        assertEquals(2, fixture.publisher.posted.size)
        assertEquals(dueAt, fixture.reminders.getReminderById(reminderId)?.lastNotifiedDueAt)
    }

    @Test
    fun `not due disabled and closed-account reminders are not posted`() = runBlocking {
        val fixture = Fixture()
        fixture.addReminder(nextDueAt = NOW + 1)
        fixture.addReminder(nextDueAt = NOW - 2, enabled = false)
        val closedId = fixture.addAccount("closed", closedAt = NOW - 10)
        fixture.addReminder(nextDueAt = NOW - 3, accountId = closedId)

        fixture.sync()

        assertTrue(fixture.publisher.posted.isEmpty())
    }

    @Test
    fun `stale open enabled account posts once for deterministic boundary`() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.defaultAccountId
        fixture.configs.updateReminderConfig(
            accountId,
            BalanceUpdateReminderConfig(hour = 0, minute = 0),
        )

        fixture.sync()
        fixture.sync()

        val command = fixture.publisher.posted.single() as MoneyNotificationCommand.Balance
        assertEquals(accountId, command.accountId)
        assertEquals(command.expectedBoundaryAt, fixture.configs.getReminderConfig(accountId).lastNotifiedBoundaryAt)
    }

    @Test
    fun `permission channel and post failures never acknowledge`() = runBlocking {
        for (result in listOf(PublishResult.NotAllowed, PublishResult.Failed)) {
            val fixture = Fixture()
            val reminderId = fixture.addReminder(nextDueAt = NOW - 1)
            fixture.publisher.result = result

            fixture.sync()

            assertEquals(null, fixture.reminders.getReminderById(reminderId)?.lastNotifiedDueAt)
        }
    }

    @Test
    fun `disabled recurring channel does not starve balance channel`() = runBlocking {
        val fixture = Fixture()
        val reminderId = fixture.addReminder(nextDueAt = NOW - 1)
        fixture.configs.updateReminderConfig(
            fixture.defaultAccountId,
            BalanceUpdateReminderConfig(hour = 0, minute = 0),
        )
        fixture.publisher.resultFor = { command ->
            if (command is MoneyNotificationCommand.Recurring) {
                PublishResult.NotAllowed
            } else {
                PublishResult.Posted
            }
        }

        fixture.sync()

        assertEquals(null, fixture.reminders.getReminderById(reminderId)?.lastNotifiedDueAt)
        assertTrue(
            fixture.configs.getReminderConfig(fixture.defaultAccountId).lastNotifiedBoundaryAt != null,
        )
        assertTrue(fixture.publisher.posted.any { it is MoneyNotificationCommand.Balance })
    }

    @Test
    fun `post success followed by CAS loss cancels stable key and requests resync`() = runBlocking {
        val fixture = Fixture()
        val reminderId = fixture.addReminder(nextDueAt = NOW - 1)
        fixture.publisher.afterPost = {
            val current = fixture.reminders.getReminderById(reminderId)!!
            fixture.reminders.updateReminder(current.copy(nextDueAt = NOW + 99))
        }

        fixture.sync()

        assertEquals(
            listOf<MoneyNotificationKey>(MoneyNotificationKey.Recurring(reminderId)),
            fixture.publisher.cancelled,
        )
        assertEquals(listOf(NotificationSyncReason.CAS_LOST), fixture.requests.reasons)
    }

    @Test
    fun `balance schedule change after post loses CAS and cancels notification`() = runBlocking {
        val fixture = Fixture()
        val accountId = fixture.defaultAccountId
        fixture.configs.updateReminderConfig(accountId, BalanceUpdateReminderConfig(hour = 0, minute = 0))
        fixture.publisher.afterPost = {
            fixture.configs.updateReminderConfig(
                accountId,
                BalanceUpdateReminderConfig(
                    period = BalanceUpdateReminderPeriod.MONTHLY,
                    monthDay = 1,
                    hour = 0,
                    minute = 0,
                ),
            )
        }

        fixture.sync()

        assertEquals(
            listOf<MoneyNotificationKey>(MoneyNotificationKey.Balance(accountId)),
            fixture.publisher.cancelled,
        )
        assertEquals(listOf(NotificationSyncReason.CAS_LOST), fixture.requests.reasons)
    }

    @Test
    fun `periodic and immediate races still post and acknowledge one occurrence`() = runBlocking {
        val fixture = Fixture()
        val reminderId = fixture.addReminder(nextDueAt = NOW - 1)

        val first = async { fixture.sync() }
        val second = async { fixture.sync() }
        first.await()
        second.await()

        assertEquals(1, fixture.publisher.posted.size)
        assertEquals(NOW - 1, fixture.reminders.getReminderById(reminderId)?.lastNotifiedDueAt)
    }

    @Test
    fun `obsolete active notifications are cancelled`() = runBlocking {
        val fixture = Fixture()
        fixture.publisher.active += MoneyNotificationKey.Recurring(404)
        fixture.publisher.active += MoneyNotificationKey.Balance(405)

        fixture.sync()

        assertEquals(
            setOf(MoneyNotificationKey.Recurring(404), MoneyNotificationKey.Balance(405)),
            fixture.publisher.cancelled.toSet(),
        )
    }

    @Test
    fun `privacy force refresh cancels old active notification and reposts despite dedupe cursor`() =
        runBlocking {
            val fixture = Fixture()
            val reminderId = fixture.addReminder(nextDueAt = NOW - 1)
            fixture.sync()
            assertEquals(1, fixture.publisher.active.size)

            fixture.preparePrivacyRefresh()
            assertTrue(fixture.publisher.active.isEmpty())
            fixture.forcePrivacyRefresh()

            assertTrue(MoneyNotificationKey.Recurring(reminderId) in fixture.publisher.cancelled)
            assertEquals(2, fixture.publisher.posted.size)
            assertEquals(1, fixture.publisher.active.size)
        }

    @Test
    fun `privacy force refresh failure leaves old notification cancelled`() = runBlocking {
        val fixture = Fixture()
        fixture.addReminder(nextDueAt = NOW - 1)
        fixture.sync()
        fixture.publisher.result = PublishResult.Failed

        fixture.forcePrivacyRefresh()

        assertTrue(fixture.publisher.active.isEmpty())
    }

    @Test
    fun `privacy force refresh only rebuilds previously active bounded keys`() = runBlocking {
        val fixture = Fixture(maxPosts = 1)
        fixture.addReminder(nextDueAt = NOW - 1)
        fixture.sync()
        fixture.addReminder(nextDueAt = NOW - 2)

        fixture.forcePrivacyRefresh()

        assertEquals(2, fixture.publisher.posted.size)
        assertEquals(1, fixture.publisher.active.size)
    }

    @Test
    fun `privacy force refresh completes every previously active key beyond normal batch size`() =
        runBlocking {
            val fixture = Fixture(maxPosts = 20)
            repeat(25) { fixture.addReminder(nextDueAt = NOW - it - 1L) }
            fixture.sync()
            fixture.sync()
            assertEquals(25, fixture.publisher.active.size)

            fixture.preparePrivacyRefresh()
            fixture.forcePrivacyRefresh()

            assertEquals(25, fixture.publisher.active.size)
            assertEquals(50, fixture.publisher.posted.size)
        }

    @Test
    fun `bounded successful batch requests one continuation`() = runBlocking {
        val fixture = Fixture(maxPosts = 2)
        repeat(3) { fixture.addReminder(nextDueAt = NOW - it - 1) }

        val result = fixture.sync()

        assertEquals(2, result.postedCount)
        assertTrue(result.hasMore)
        assertEquals(listOf(NotificationSyncReason.CONTINUATION), fixture.requests.reasons)
    }

    @Test
    fun `failed bounded batch does not loop continuation`() = runBlocking {
        val fixture = Fixture(maxPosts = 2)
        repeat(3) { fixture.addReminder(nextDueAt = NOW - it - 1) }
        fixture.publisher.result = PublishResult.NotAllowed

        val result = fixture.sync()

        assertFalse(result.hasMore)
        assertTrue(fixture.requests.reasons.isEmpty())
    }

    private class Fixture(maxPosts: Int = 20) {
        val accounts = InMemoryAccountRepository()
        val reminders = InMemoryRecurringReminderRepository()
        val configs = InMemoryAccountReminderSettingsRepository()
        val publisher = FakePublisher()
        val requests = RecordingRequester()
        val defaultAccountId: Long
        private val useCase: SyncMoneyNotificationsUseCase

        init {
            defaultAccountId = runBlocking { addAccount("wallet") }
            useCase = SyncMoneyNotificationsUseCase(
                accountRepository = accounts,
                reminderRepository = reminders,
                accountReminderSettingsRepository = configs,
                publisher = publisher,
                notificationSyncRequester = requests,
                accountBalanceProvider = { accounts -> accounts.associate { it.id to it.initialBalance } },
                clockProvider = { NOW },
                zoneIdProvider = { ZoneId.of("Asia/Shanghai") },
                maxPostsPerRun = maxPosts,
            )
        }

        suspend fun sync() = useCase()

        suspend fun forcePrivacyRefresh() = useCase.forceRefreshPrivacy()

        fun preparePrivacyRefresh() = useCase.preparePrivacyRefresh()

        suspend fun skip(reminderId: Long, expectedDueAt: Long) = SkipReminderUseCase(
            accountRepository = accounts,
            reminderRepository = reminders,
            clockProvider = { NOW },
            zoneIdProvider = { ZoneId.of("Asia/Shanghai") },
            notificationSyncRequester = requests,
        )(reminderId, expectedDueAt)

        suspend fun undo(token: com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken) =
            UndoSkipReminderUseCase(
                reminderRepository = reminders,
                clockProvider = { NOW },
                notificationSyncRequester = requests,
            )(token)

        suspend fun addAccount(name: String, closedAt: Long? = null): Long =
            accounts.createAccount(
                Account(
                    name = name,
                    initialBalance = 100,
                    createdAt = NOW - 40L * DAY,
                    closedAt = closedAt,
                ),
            )

        suspend fun addReminder(
            nextDueAt: Long,
            enabled: Boolean = true,
            accountId: Long = defaultAccountId,
        ): Long = reminders.insertReminder(
            RecurringReminder(
                name = "bill",
                type = "manual",
                accountId = accountId,
                direction = "outflow",
                amount = 1_000,
                periodType = "monthly",
                periodValue = 1,
                periodMonth = null,
                isEnabled = enabled,
                nextDueAt = nextDueAt,
                anchorDueAt = nextDueAt,
                createdAt = NOW - DAY,
                updatedAt = NOW - DAY,
            ),
        )
    }

    private class RecordingRequester : NotificationSyncRequester {
        val reasons = mutableListOf<NotificationSyncReason>()
        override fun request(reason: NotificationSyncReason) {
            reasons += reason
        }
    }

    private class FakePublisher : MoneyNotificationPublisher {
        var result: PublishResult = PublishResult.Posted
        var resultFor: (MoneyNotificationCommand) -> PublishResult = { result }
        var afterPost: suspend () -> Unit = {}
        val posted = mutableListOf<MoneyNotificationCommand>()
        val cancelled = mutableListOf<MoneyNotificationKey>()
        val active = mutableSetOf<MoneyNotificationKey>()

        override fun capability(key: MoneyNotificationKey): NotificationCapability =
            NotificationCapability.Allowed

        override suspend fun post(command: MoneyNotificationCommand): PublishResult {
            posted += command
            val publishResult = resultFor(command)
            if (publishResult == PublishResult.Posted) active += command.key
            afterPost()
            return publishResult
        }

        override fun cancel(key: MoneyNotificationKey) {
            cancelled += key
            active -= key
        }

        override fun activeKeys(): Set<MoneyNotificationKey> = active.toSet()
    }

    private companion object {
        const val DAY = 86_400_000L
        const val NOW = 1_765_843_200_000L // 2025-12-16T00:00:00Z
    }
}
