package com.shihuaidexianyu.money.domain.notification

enum class NotificationSyncReason {
    APP_START,
    REMINDER_CHANGED,
    REMINDER_PROCESSED,
    REMINDER_SKIPPED,
    REMINDER_UNDO,
    BALANCE_RECONCILED,
    ACCOUNT_CLOSED,
    IMPORT,
    ROLLBACK,
    CONFIG_CHANGED,
    PERMISSION_GRANTED,
    CAS_LOST,
    CONTINUATION,
}

fun interface NotificationSyncRequester {
    /** Best effort: implementations must not propagate enqueue failures to business mutations. */
    fun request(reason: NotificationSyncReason)
}

object NoOpNotificationSyncRequester : NotificationSyncRequester {
    override fun request(reason: NotificationSyncReason) = Unit
}

sealed interface MoneyNotificationKey {
    val tag: String
    val notificationId: Int

    data class Recurring(val reminderId: Long) : MoneyNotificationKey {
        override val tag: String = "money:recurring:$reminderId"
        override val notificationId: Int = 1
    }

    data class Balance(val accountId: Long) : MoneyNotificationKey {
        override val tag: String = "money:balance:$accountId"
        override val notificationId: Int = 2
    }
}

sealed interface MoneyNotificationCommand {
    val key: MoneyNotificationKey

    data class Recurring(
        val reminderId: Long,
        val expectedDueAt: Long,
        val reminderName: String,
        val accountName: String,
        val amount: Long,
    ) : MoneyNotificationCommand {
        override val key: MoneyNotificationKey = MoneyNotificationKey.Recurring(reminderId)
    }

    data class Balance(
        val accountId: Long,
        val expectedBoundaryAt: Long,
        val accountName: String,
        val balance: Long,
        val scheduleText: String,
    ) : MoneyNotificationCommand {
        override val key: MoneyNotificationKey = MoneyNotificationKey.Balance(accountId)
    }
}

data class MoneyNotificationIntentIdentity(
    val action: String,
    val longExtras: Map<String, Long>,
) {
    companion object {
        const val ACTION_RECURRING = "com.shihuaidexianyu.money.OPEN_RECURRING_REMINDER"
        const val ACTION_BALANCE = "com.shihuaidexianyu.money.OPEN_BALANCE_REMINDER"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_EXPECTED_DUE_AT = "expected_due_at"
        const val EXTRA_ACCOUNT_ID = "account_id"

        fun from(command: MoneyNotificationCommand): MoneyNotificationIntentIdentity = when (command) {
            is MoneyNotificationCommand.Recurring -> MoneyNotificationIntentIdentity(
                action = ACTION_RECURRING,
                longExtras = mapOf(
                    EXTRA_REMINDER_ID to command.reminderId,
                    EXTRA_EXPECTED_DUE_AT to command.expectedDueAt,
                ),
            )
            is MoneyNotificationCommand.Balance -> MoneyNotificationIntentIdentity(
                action = ACTION_BALANCE,
                longExtras = mapOf(EXTRA_ACCOUNT_ID to command.accountId),
            )
        }
    }
}

sealed interface NotificationLaunchIdentity {
    data class Recurring(val reminderId: Long, val expectedDueAt: Long) : NotificationLaunchIdentity
    data class Balance(val accountId: Long) : NotificationLaunchIdentity
}

data class NotificationLaunchRequest(
    val token: Long,
    val identity: NotificationLaunchIdentity,
)

sealed interface NotificationLaunchDestination {
    data class ProcessReminder(val reminder: com.shihuaidexianyu.money.domain.model.RecurringReminder) :
        NotificationLaunchDestination
    data class ReconcileBalance(val accountId: Long) : NotificationLaunchDestination
    data class ReminderCenter(val stateChanged: Boolean) : NotificationLaunchDestination
}

data class MoneyNotificationContent(
    val title: String,
    val text: String,
    val expandedText: String,
    val publicTitle: String,
    val publicText: String,
)

fun interface MoneyNotificationContentPolicy {
    suspend fun content(command: MoneyNotificationCommand): MoneyNotificationContent
}

enum class NotificationCapability {
    Allowed,
    PermissionDenied,
    ApplicationDisabled,
    ChannelDisabled,
}

enum class PublishResult {
    Posted,
    NotAllowed,
    Failed,
}

interface MoneyNotificationPublisher {
    fun capability(key: MoneyNotificationKey): NotificationCapability
    suspend fun post(command: MoneyNotificationCommand): PublishResult
    fun cancel(key: MoneyNotificationKey)
    fun activeKeys(): Set<MoneyNotificationKey>
}
