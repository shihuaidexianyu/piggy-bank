package com.shihuaidexianyu.money.data.repository

import com.shihuaidexianyu.money.data.dao.CashFlowDailyTotalRow
import com.shihuaidexianyu.money.data.dao.PurposeTotalRow
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
import com.shihuaidexianyu.money.domain.model.CashFlowDailyTotal
import com.shihuaidexianyu.money.domain.model.PurposeTotal
import com.shihuaidexianyu.money.domain.model.RecurringReminder
import com.shihuaidexianyu.money.domain.model.TransferRecord

internal fun AccountEntity.toDomain(): Account = Account(
    id = id,
    name = name,
    initialBalance = initialBalance,
    createdAt = createdAt,
    archivedAt = archivedAt,
    isArchived = isArchived,
    lastUsedAt = lastUsedAt,
    lastBalanceUpdateAt = lastBalanceUpdateAt,
    displayOrder = displayOrder,
    colorName = colorName,
)

fun Account.toEntity(): AccountEntity = AccountEntity(
    id = id,
    name = name,
    initialBalance = initialBalance,
    createdAt = createdAt,
    archivedAt = archivedAt,
    isArchived = isArchived,
    lastUsedAt = lastUsedAt,
    lastBalanceUpdateAt = lastBalanceUpdateAt,
    displayOrder = displayOrder,
    colorName = colorName,
)

internal fun CashFlowRecordEntity.toDomain(): CashFlowRecord = CashFlowRecord(
    id = id,
    accountId = accountId,
    direction = direction,
    amount = amount,
    purpose = purpose,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
)

fun CashFlowRecord.toEntity(): CashFlowRecordEntity = CashFlowRecordEntity(
    id = id,
    accountId = accountId,
    direction = direction,
    amount = amount,
    purpose = purpose,
    occurredAt = occurredAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isDeleted = isDeleted,
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
    isDeleted = isDeleted,
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
    isDeleted = isDeleted,
)

internal fun BalanceUpdateRecordEntity.toDomain(): BalanceUpdateRecord = BalanceUpdateRecord(
    id = id,
    accountId = accountId,
    actualBalance = actualBalance,
    systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
)

fun BalanceUpdateRecord.toEntity(): BalanceUpdateRecordEntity = BalanceUpdateRecordEntity(
    id = id,
    accountId = accountId,
    actualBalance = actualBalance,
    systemBalanceBeforeUpdate = systemBalanceBeforeUpdate,
    delta = delta,
    occurredAt = occurredAt,
    createdAt = createdAt,
)

internal fun BalanceAdjustmentRecordEntity.toDomain(): BalanceAdjustmentRecord = BalanceAdjustmentRecord(
    id = id,
    accountId = accountId,
    delta = delta,
    sourceUpdateRecordId = sourceUpdateRecordId,
    occurredAt = occurredAt,
    createdAt = createdAt,
)

fun BalanceAdjustmentRecord.toEntity(): BalanceAdjustmentRecordEntity = BalanceAdjustmentRecordEntity(
    id = id,
    accountId = accountId,
    delta = delta,
    sourceUpdateRecordId = sourceUpdateRecordId,
    occurredAt = occurredAt,
    createdAt = createdAt,
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
    lastConfirmedAt = lastConfirmedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun PurposeTotalRow.toDomain(): PurposeTotal = PurposeTotal(
    purpose = purpose,
    amount = amount,
)

internal fun CashFlowDailyTotalRow.toDomain(): CashFlowDailyTotal = CashFlowDailyTotal(
    epochDay = epochDay,
    direction = direction,
    amount = amount,
)
