package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.HistoryRecordRow
import com.shihuaidexianyu.money.data.entity.AccountEntity
import com.shihuaidexianyu.money.data.entity.BalanceAdjustmentRecordEntity
import com.shihuaidexianyu.money.data.entity.BalanceUpdateRecordEntity
import com.shihuaidexianyu.money.data.entity.CashFlowRecordEntity
import com.shihuaidexianyu.money.data.entity.RecurringReminderEntity
import com.shihuaidexianyu.money.data.entity.TransferRecordEntity
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.TransferRecord

internal fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    initialBalance = initialBalance,
    createdAt = createdAt,
    isHidden = isHidden,
    closedAt = closedAt,
    lastUsedAt = lastUsedAt,
    lastBalanceUpdateAt = lastBalanceUpdateAt,
    displayOrder = displayOrder,
    colorName = colorName,
    iconName = iconName,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    initialBalance = initialBalance,
    createdAt = createdAt,
    isHidden = isHidden,
    closedAt = closedAt,
    lastUsedAt = lastUsedAt,
    lastBalanceUpdateAt = lastBalanceUpdateAt,
    displayOrder = displayOrder,
    colorName = colorName,
    iconName = iconName,
)

internal fun CashFlowRecordEntity.toDomain(): CashFlowRecord = CashFlowRecord(
    id = id,
    accountId = accountId,
    direction = direction,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

fun CashFlowRecord.toEntity(): CashFlowRecordEntity = CashFlowRecordEntity(
    id = id,
    accountId = accountId,
    direction = direction,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

internal fun TransferRecordEntity.toDomain(): TransferRecord = TransferRecord(
    id = id,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

fun TransferRecord.toEntity(): TransferRecordEntity = TransferRecordEntity(
    id = id,
    fromAccountId = fromAccountId,
    toAccountId = toAccountId,
    amount = amount,
    note = note,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

internal fun BalanceUpdateRecordEntity.toDomain(): BalanceUpdateRecord = BalanceUpdateRecord(
    id = id,
    accountId = accountId,
    actualBalance = actualBalance,
    systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

fun BalanceUpdateRecord.toEntity(): BalanceUpdateRecordEntity = BalanceUpdateRecordEntity(
    id = id,
    accountId = accountId,
    actualBalance = actualBalance,
    systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

internal fun BalanceAdjustmentRecordEntity.toDomain(): BalanceAdjustmentRecord = BalanceAdjustmentRecord(
    id = id,
    accountId = accountId,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

fun BalanceAdjustmentRecord.toEntity(): BalanceAdjustmentRecordEntity = BalanceAdjustmentRecordEntity(
    id = id,
    accountId = accountId,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    operationId = operationId,
)

internal fun RecurringReminderEntity.toDomain(): RecurringReminder = RecurringReminder(
    id = id,
    name = name,
    type = type,
    accountId = accountId,
    direction = direction,
    amount = amount,
    periodType = periodType,
    periodValue = periodValue,
    periodMonth = periodMonth,
    isEnabled = isEnabled,
    nextDueAt = nextDueAt,
    anchorDueAt = anchorDueAt,
    lastNotifiedDueAt = lastNotifiedDueAt,
    lastConfirmedAt = lastConfirmedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecurringReminder.toEntity(): RecurringReminderEntity = RecurringReminderEntity(
    id = id,
    name = name,
    type = type,
    accountId = accountId,
    direction = direction,
    amount = amount,
    periodType = periodType,
    periodValue = periodValue,
    periodMonth = periodMonth,
    isEnabled = isEnabled,
    nextDueAt = nextDueAt,
    anchorDueAt = anchorDueAt,
    lastNotifiedDueAt = lastNotifiedDueAt,
    lastConfirmedAt = lastConfirmedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun HistoryRecordRow.toDomain(): HistoryRecord = HistoryRecord(
    recordId = recordId,
    type = HistoryRecordType.valueOf(type),
    sourceOrder = sourceOrder,
    accountId = accountId,
    relatedAccountId = relatedAccountId,
    title = title,
    amount = amount,
    occurredAt = occurredAt,
    keywordSource = keywordSource,
)
