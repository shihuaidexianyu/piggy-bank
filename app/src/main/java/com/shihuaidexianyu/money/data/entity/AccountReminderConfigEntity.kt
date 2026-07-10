package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "account_reminder_configs",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
)
data class AccountReminderConfigEntity(
    @PrimaryKey
    val accountId: Long,
    val period: String,
    val weekday: String,
    val monthDay: Int,
    val hour: Int,
    val minute: Int,
    val isEnabled: Boolean,
    val lastNotifiedBoundaryAt: Long?,
)
