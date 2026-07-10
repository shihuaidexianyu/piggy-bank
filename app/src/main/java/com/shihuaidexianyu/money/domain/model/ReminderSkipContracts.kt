package com.shihuaidexianyu.money.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ReminderSkipUndoToken(
    val reminderId: Long,
    val skippedDueAt: Long,
    val advancedDueAt: Long,
    val skippedUpdatedAt: Long,
)

enum class UndoReminderSkipResult {
    RESTORED,
    STALE,
    NOT_FOUND,
}
