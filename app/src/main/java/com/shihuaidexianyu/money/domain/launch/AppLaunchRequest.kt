package com.shihuaidexianyu.money.domain.launch

import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import java.io.Serializable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppLaunchRequest(
    val token: String,
    val destination: AppLaunchDestination,
) : Serializable

sealed interface AppLaunchDestination : Serializable {
    data object Home : AppLaunchDestination
    data object BatchReconcile : AppLaunchDestination
    data object Transfer : AppLaunchDestination
    data class CashFlow(val direction: CashFlowDirection) : AppLaunchDestination
    data class SharePreview(val originalText: String) : AppLaunchDestination
    data class RecurringNotification(
        val reminderId: Long,
        val expectedDueAt: Long,
    ) : AppLaunchDestination
    data class BalanceNotification(val accountId: Long) : AppLaunchDestination
}

sealed interface AppLaunchInput {
    data class Shortcut(val action: String) : AppLaunchInput
    data class SharedText(val text: String) : AppLaunchInput
    data object WidgetHome : AppLaunchInput
    data class RecurringNotification(val reminderId: Long, val expectedDueAt: Long) : AppLaunchInput
    data class BalanceNotification(val accountId: Long) : AppLaunchInput
}

object AppLaunchRequestFactory {
    private const val MAX_SHARED_TEXT_LENGTH = 4_000

    fun create(token: String, input: AppLaunchInput): AppLaunchRequest? {
        if (token.isBlank()) return null
        val destination = when (input) {
            is AppLaunchInput.Shortcut -> when (input.action) {
                "record_outflow" -> AppLaunchDestination.CashFlow(CashFlowDirection.OUTFLOW)
                "record_inflow" -> AppLaunchDestination.CashFlow(CashFlowDirection.INFLOW)
                "balance_check" -> AppLaunchDestination.BatchReconcile
                "record_transfer" -> AppLaunchDestination.Transfer
                else -> return null
            }
            is AppLaunchInput.SharedText -> AppLaunchDestination.SharePreview(
                input.text.takeIf { it.isNotBlank() && it.length <= MAX_SHARED_TEXT_LENGTH }
                    ?: return null,
            )
            AppLaunchInput.WidgetHome -> AppLaunchDestination.Home
            is AppLaunchInput.RecurringNotification -> AppLaunchDestination.RecurringNotification(
                reminderId = input.reminderId.takeIf { it > 0L } ?: return null,
                expectedDueAt = input.expectedDueAt.takeIf { it > 0L } ?: return null,
            )
            is AppLaunchInput.BalanceNotification -> AppLaunchDestination.BalanceNotification(
                accountId = input.accountId.takeIf { it > 0L } ?: return null,
            )
        }
        return AppLaunchRequest(token, destination)
    }
}

class AppLaunchRequestQueue(
    initialPending: List<AppLaunchRequest> = emptyList(),
    initialAcknowledged: Set<String> = emptySet(),
    private val onChanged: (List<AppLaunchRequest>, Set<String>) -> Unit = { _, _ -> },
) {
    private val mutablePending = MutableStateFlow(
        initialPending.distinctBy { it.token }.takeLast(MAX_PENDING_REQUESTS),
    )
    val pending: StateFlow<List<AppLaunchRequest>> = mutablePending.asStateFlow()
    private val acknowledged = LinkedHashSet(initialAcknowledged.toList().takeLast(MAX_ACKNOWLEDGED_TOKENS))

    init {
        initialPending.distinctBy { it.token }
            .dropLast(MAX_PENDING_REQUESTS)
            .forEach { acknowledged += it.token }
        trimAcknowledged()
    }

    fun offer(request: AppLaunchRequest) {
        if (request.token.isBlank() || request.token in acknowledged) return
        if (mutablePending.value.any { it.token == request.token }) return
        val combined = mutablePending.value + request
        if (combined.size > MAX_PENDING_REQUESTS) {
            acknowledged += combined.first().token
            trimAcknowledged()
        }
        mutablePending.value = combined.takeLast(MAX_PENDING_REQUESTS)
        publish()
    }

    fun acknowledge(token: String) {
        val current = mutablePending.value
        if (current.firstOrNull()?.token != token) return
        mutablePending.value = current.drop(1)
        acknowledged += token
        trimAcknowledged()
        publish()
    }

    private fun publish() = onChanged(mutablePending.value, acknowledged.toSet())

    private fun trimAcknowledged() {
        while (acknowledged.size > MAX_ACKNOWLEDGED_TOKENS) acknowledged.remove(acknowledged.first())
    }

    private companion object {
        const val MAX_ACKNOWLEDGED_TOKENS = 64
        const val MAX_PENDING_REQUESTS = 8
    }
}
