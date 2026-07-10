package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "balance_update_records",
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
        Index(value = ["accountId", "deletedAt", "occurredAt", "id"]),
        Index(value = ["deletedAt", "occurredAt"]),
        Index(value = ["operationId"], unique = true),
    ],
)
data class BalanceUpdateRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val accountId: Long,
    val actualBalance: Long,
    val systemBalanceBeforeUpdate: Long,
    val delta: Long,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

