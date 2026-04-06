package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_reminders",
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["nextDueAt"]),
        Index(value = ["isEnabled"]),
    ],
)
data class RecurringReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: String,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val periodType: String,
    val periodValue: Int,
    val periodMonth: Int?,
    val isEnabled: Boolean = true,
    val nextDueAt: Long,
    val lastConfirmedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
