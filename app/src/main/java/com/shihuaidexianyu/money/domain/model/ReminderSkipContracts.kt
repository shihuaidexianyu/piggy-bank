package com.shihuaidexianyu.money.domain.model

import java.io.Serializable as JavaSerializable
import kotlinx.serialization.Serializable

@Serializable
data class ReminderSkipUndoToken(
    val reminderId: Long,
    val skippedDueAt: Long,
    val advancedDueAt: Long,
    val skippedUpdatedAt: Long,
) : JavaSerializable

enum class UndoReminderSkipResult {
    RESTORED,
    ALREADY_RESTORED,
    STALE,
    NOT_FOUND,
}
