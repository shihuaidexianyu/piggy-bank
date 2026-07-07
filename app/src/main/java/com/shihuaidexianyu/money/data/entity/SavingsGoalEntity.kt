package com.shihuaidexianyu.money.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "savings_goals",
)
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val targetAmount: Long,
    val createdAt: Long,
)

@Entity(
    tableName = "savings_goal_account_links",
    primaryKeys = ["goalId", "accountId"],
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = SavingsGoalEntity::class,
            parentColumns = ["id"],
            childColumns = ["goalId"],
            onDelete = androidx.room.ForeignKey.CASCADE,
        ),
        androidx.room.ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = androidx.room.ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["goalId"]),
        Index(value = ["accountId"]),
        Index(value = ["goalId", "accountId"], unique = true),
    ],
)
data class SavingsGoalAccountLinkEntity(
    val goalId: Long,
    val accountId: Long,
)
