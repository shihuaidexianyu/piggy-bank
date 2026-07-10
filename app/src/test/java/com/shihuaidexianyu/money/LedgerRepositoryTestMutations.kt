package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.repository.TransactionRepository

internal suspend fun TransactionRepository.softDeleteCurrentCashFlowRecord(id: Long, updatedAt: Long) {
    val existing = requireNotNull(queryStoredCashFlowRecordById(id))
    check(softDeleteCashFlowRecord(id, existing.operationId, existing.updatedAt, updatedAt))
}

internal suspend fun TransactionRepository.softDeleteCurrentTransferRecord(id: Long, updatedAt: Long) {
    val existing = requireNotNull(queryStoredTransferRecordById(id))
    check(softDeleteTransferRecord(id, existing.operationId, existing.updatedAt, updatedAt))
}

internal suspend fun TransactionRepository.softDeleteCurrentBalanceUpdateRecord(id: Long, deletedAt: Long) {
    val existing = requireNotNull(queryStoredBalanceUpdateRecordById(id))
    check(softDeleteBalanceUpdateRecord(id, existing.operationId, existing.updatedAt, deletedAt))
}

internal suspend fun TransactionRepository.softDeleteCurrentBalanceAdjustmentRecord(id: Long, deletedAt: Long) {
    val existing = requireNotNull(queryStoredBalanceAdjustmentRecordById(id))
    check(softDeleteBalanceAdjustmentRecord(id, existing.operationId, existing.updatedAt, deletedAt))
}
