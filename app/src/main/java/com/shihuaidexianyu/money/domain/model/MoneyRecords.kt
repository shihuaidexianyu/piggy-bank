package com.shihuaidexianyu.money.domain.model

data class Account(
    val id: Long = 0,
    val name: String,
    val initialBalance: Long,
    val createdAt: Long,
    val isHidden: Boolean = false,
    val closedAt: Long? = null,
    val lastUsedAt: Long? = null,
    val lastBalanceUpdateAt: Long? = null,
    val displayOrder: Int = 0,
    val colorName: String = DEFAULT_ACCOUNT_COLOR_NAME,
    val iconName: String = DEFAULT_ACCOUNT_ICON_NAME,
) {
    val isClosed: Boolean
        get() = closedAt != null
}

data class CashFlowRecord(
    val id: Long = 0,
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val note: String,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
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
    val deletedAt: Long? = null,
    val operationId: String,
)

data class BalanceUpdateRecord(
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

data class BalanceAdjustmentRecord(
    val id: Long = 0,
    val accountId: Long,
    val delta: Long,
    val occurredAt: Long,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val operationId: String,
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
    val anchorDueAt: Long,
    val lastNotifiedDueAt: Long? = null,
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

data class CashFlowAnalysisEntry(
    val accountId: Long,
    val direction: String,
    val amount: Long,
    val occurredAt: Long,
)

data class TransferPathTotal(
    val fromAccountId: Long,
    val toAccountId: Long,
    val amount: Long,
)
