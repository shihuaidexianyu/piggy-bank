package com.shihuaidexianyu.money.domain.usecase

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationCommand
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationKey
import com.shihuaidexianyu.money.domain.notification.MoneyNotificationPublisher
import com.shihuaidexianyu.money.domain.notification.NotificationSyncReason
import com.shihuaidexianyu.money.domain.notification.NotificationSyncRequester
import com.shihuaidexianyu.money.domain.notification.PublishResult
import com.shihuaidexianyu.money.domain.repository.AccountReminderSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
import com.shihuaidexianyu.money.domain.repository.RecurringReminderRepository
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.time.ZoneIdProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class NotificationSyncResult(
    val postedCount: Int,
    val hasMore: Boolean,
)

fun interface NotificationAccountBalanceProvider {
    suspend fun balances(accounts: List<Account>): Map<Long, Long>
}

class SyncMoneyNotificationsUseCase(
    private val accountRepository: AccountRepository,
    private val reminderRepository: RecurringReminderRepository,
    private val accountReminderSettingsRepository: AccountReminderSettingsRepository,
    private val publisher: MoneyNotificationPublisher,
    private val notificationSyncRequester: NotificationSyncRequester,
    private val accountBalanceProvider: NotificationAccountBalanceProvider,
    private val clockProvider: ClockProvider,
    private val zoneIdProvider: ZoneIdProvider,
    private val maxPostsPerRun: Int = 20,
) {
    private val mutex = Mutex()

    init {
        require(maxPostsPerRun > 0)
    }

    suspend operator fun invoke(): NotificationSyncResult = mutex.withLock {
        val now = clockProvider.nowMillis()
        val zoneId = zoneIdProvider.zoneId()
        val reminders = reminderRepository.queryAll()
        val accounts = accountRepository.queryAllAccounts().associateBy(Account::id)
        val configs = accountReminderSettingsRepository.queryReminderConfigs()
        val recurringCandidates = sequence<Candidate> {
            reminders.asSequence().filter { reminder ->
                val account = accounts[reminder.accountId]
                reminder.isEnabled &&
                    account != null && !account.isClosed &&
                    reminder.nextDueAt <= now &&
                    reminder.lastNotifiedDueAt != reminder.nextDueAt
            }.forEach { yield(Candidate.Recurring(it.id, it.nextDueAt)) }
        }.take(maxPostsPerRun + 1).toList()
        val balanceCandidates = sequence<Candidate> {
            accounts.values.sortedBy { it.id }.forEach { account ->
                val config = configs[account.id] ?: return@forEach
                if (!account.isClosed && config.isEnabled) {
                    val boundary = AccountStatusCalculator.latestReminderBoundaryAt(now, config, zoneId)
                    val anchor = account.lastBalanceUpdateAt ?: account.createdAt
                    if (anchor < boundary && config.lastNotifiedBoundaryAt != boundary) {
                        yield(Candidate.Balance(account.id, boundary, config.lastNotifiedBoundaryAt))
                    }
                }
            }
        }.take(maxPostsPerRun + 1).toList()
        val attemptedCandidates = interleaveCandidates(
            recurringCandidates,
            balanceCandidates,
            maxPostsPerRun,
        )
        val hasUnattemptedCandidates =
            recurringCandidates.size + balanceCandidates.size > attemptedCandidates.size
        val balanceAccounts = attemptedCandidates.mapNotNull { candidate ->
            (candidate as? Candidate.Balance)?.let { accounts[it.accountId] }
        }
        val currentBalances = if (balanceAccounts.isEmpty()) {
            emptyMap()
        } else {
            accountBalanceProvider.balances(balanceAccounts)
        }
        var postedCount = 0
        var successfulAcks = 0
        candidateLoop@ for (candidate in attemptedCandidates) {
            when (candidate) {
                is Candidate.Recurring -> {
                    val reminder = reminderRepository.getReminderById(candidate.reminderId)
                    val account = reminder?.let { accountRepository.getAccountById(it.accountId) }
                    if (reminder != null && account != null && !account.isClosed &&
                        reminder.isEnabled && reminder.nextDueAt == candidate.dueAt &&
                        reminder.nextDueAt <= now && reminder.lastNotifiedDueAt != candidate.dueAt
                    ) {
                        val command = MoneyNotificationCommand.Recurring(
                            reminderId = reminder.id,
                            expectedDueAt = candidate.dueAt,
                            reminderName = reminder.name,
                            accountName = account.name,
                            amount = reminder.amount,
                        )
                        when (publisher.post(command)) {
                            PublishResult.Posted -> {
                                postedCount++
                                if (reminderRepository.acknowledgeNotifiedOccurrence(reminder.id, candidate.dueAt)) {
                                    successfulAcks++
                                } else {
                                    publisher.cancel(command.key)
                                    request(NotificationSyncReason.CAS_LOST)
                                }
                            }
                            PublishResult.NotAllowed, PublishResult.Failed -> continue@candidateLoop
                        }
                    }
                }

                is Candidate.Balance -> {
                    val account = accountRepository.getAccountById(candidate.accountId)
                    val config = accountReminderSettingsRepository.getReminderConfig(candidate.accountId)
                    if (account != null && !account.isClosed && config.isEnabled) {
                        val boundary = AccountStatusCalculator.latestReminderBoundaryAt(now, config, zoneId)
                        val anchor = account.lastBalanceUpdateAt ?: account.createdAt
                        if (boundary == candidate.boundaryAt && anchor < boundary &&
                            config.lastNotifiedBoundaryAt == candidate.previousBoundaryAt
                        ) {
                            val currentBalance = currentBalances[account.id] ?: continue@candidateLoop
                            val command = MoneyNotificationCommand.Balance(
                                accountId = account.id,
                                expectedBoundaryAt = boundary,
                                accountName = account.name,
                                balance = currentBalance,
                                scheduleText = config.displayText,
                            )
                            when (publisher.post(command)) {
                                PublishResult.Posted -> {
                                    postedCount++
                                    if (accountReminderSettingsRepository.acknowledgeStaleBoundary(
                                        accountId = account.id,
                                        expectedConfig = config,
                                        expectedPreviousBoundaryAt = candidate.previousBoundaryAt,
                                        boundaryAt = boundary,
                                    )
                                    ) {
                                        successfulAcks++
                                    } else {
                                        publisher.cancel(command.key)
                                        request(NotificationSyncReason.CAS_LOST)
                                    }
                                }
                                PublishResult.NotAllowed, PublishResult.Failed -> continue@candidateLoop
                            }
                        }
                    }
                }
            }
        }

        cancelObsolete(now)
        val hasMore = hasUnattemptedCandidates && successfulAcks > 0
        if (hasMore) request(NotificationSyncReason.CONTINUATION)
        NotificationSyncResult(postedCount = postedCount, hasMore = hasMore)
    }

    private suspend fun cancelObsolete(now: Long) {
        val zoneId = zoneIdProvider.zoneId()
        val accounts = accountRepository.queryAllAccounts().associateBy(Account::id)
        val configs = accountReminderSettingsRepository.queryReminderConfigs()
        val desired = mutableSetOf<MoneyNotificationKey>()
        reminderRepository.queryAll().forEach { reminder ->
            val account = accounts[reminder.accountId]
            if (reminder.isEnabled && reminder.nextDueAt <= now && account != null && !account.isClosed) {
                desired += MoneyNotificationKey.Recurring(reminder.id)
            }
        }
        accounts.values.forEach { account ->
            val config = configs[account.id] ?: return@forEach
            if (!account.isClosed && config.isEnabled) {
                val boundary = AccountStatusCalculator.latestReminderBoundaryAt(now, config, zoneId)
                if ((account.lastBalanceUpdateAt ?: account.createdAt) < boundary) {
                    desired += MoneyNotificationKey.Balance(account.id)
                }
            }
        }
        (publisher.activeKeys() - desired).forEach(publisher::cancel)
    }

    private fun request(reason: NotificationSyncReason) {
        runCatching { notificationSyncRequester.request(reason) }
    }

    private fun interleaveCandidates(
        first: List<Candidate>,
        second: List<Candidate>,
        limit: Int,
    ): List<Candidate> = buildList {
        var index = 0
        while (size < limit && (index < first.size || index < second.size)) {
            if (index < first.size && size < limit) add(first[index])
            if (index < second.size && size < limit) add(second[index])
            index++
        }
    }

    private sealed interface Candidate {
        data class Recurring(val reminderId: Long, val dueAt: Long) : Candidate
        data class Balance(
            val accountId: Long,
            val boundaryAt: Long,
            val previousBoundaryAt: Long?,
        ) : Candidate
    }
}
