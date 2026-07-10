package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recurring_reminders",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["accountId"]),
        Index(value = ["nextDueAt"]),
        Index(value = ["isEnabled", "nextDueAt"]),
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
    val anchorDueAt: Long,
    val lastNotifiedDueAt: Long? = null,
    val lastConfirmedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
