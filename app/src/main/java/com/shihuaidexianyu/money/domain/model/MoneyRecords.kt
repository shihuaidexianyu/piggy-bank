package com.shihuaidexianyu.money.domain.model

data class Account(
    val id: Long = 0,
    val name: String,
    val initialBalance: Long,
    val createdAt: Long,
    val archivedAt: Long? = null,
    val isArchived: Boolean = false,
    val lastUsedAt: Long? = null,
    val lastBalanceUpdateAt: Long? = null,
    val displayOrder: Int = 0,
    val colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
)

data class CashFlowRecord(
    val id: Long = 0,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val purpose: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

data class TransferRecord(
    val id: Long = 0,
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Long,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean = false,
)

data class BalanceUpdateRecord(
    val id: Long = 0,
    val accountId: Long,
    val actualBalance: Long,
    val systemBalanceBeforeUpdate: Long,
    val delta: Long,
    val occurredAt: Long,
    val createdAt: Long,
)

data class BalanceAdjustmentRecord(
    val id: Long = 0,
    val accountId: Long,
    val delta: Long,
    val sourceUpdateRecordId: Long,
    val occurredAt: Long,
    val createdAt: Long,
)

data class RecurringReminder(
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

data class PurposeTotal(
    val purpose: String,
    val amount: Long,
)

data class CashFlowDailyTotal(
    val epochDay: Long,
    val direction: String,
    val amount: Long,
)
