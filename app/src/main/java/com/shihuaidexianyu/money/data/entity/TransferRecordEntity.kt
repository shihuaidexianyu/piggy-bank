package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transfer_records",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromAccountId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.RESTRICT,
            onUpdate = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index(value = ["fromAccountId", "deletedAt", "occurredAt"]),
        Index(value = ["toAccountId", "deletedAt", "occurredAt"]),
        Index(value = ["deletedAt", "occurredAt"]),
        Index(value = ["operationId"], unique = true),
    ],
)
data class TransferRecordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Long,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
)

