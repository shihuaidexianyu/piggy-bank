package com.shihuaidexianyu.money.ui.balance

import java.io.Serializable

data class BalanceFormDraft(
    val selectedAccountId: Long?,
    val actualBalanceText: String,
    val occurredAtMillis: Long,
    val actualBalanceEdited: Boolean,
    val accountError: String? = null,
    val actualBalanceError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean,
    val operationId: String,
) : Serializable

data class EditBalanceUpdateFormDraft(
    val actualBalanceText: String,
    val occurredAtMillis: Long,
    val actualBalanceError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean,
    val hasConflict: Boolean = false,
    val expectedUpdatedAt: Long,
) : Serializable

data class BatchReconcileDraft(
    val selectedAccountIds: List<Long> = emptyList(),
    val actualBalances: Map<Long, Long> = emptyMap(),
    val occurredAtMillis: Long? = null,
    val operationIds: Map<Long, String> = emptyMap(),
    val isDirty: Boolean = false,
) : Serializable
