package com.shihuaidexianyu.money.ui.record

import java.io.Serializable

data class CashFlowFormDraft(
    val selectedAccountId: Long?,
    val amountText: String,
    val note: String,
    val occurredAtMillis: Long,
    val noteError: String?,
    val accountError: String? = null,
    val amountError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean,
    val operationId: String,
) : Serializable

data class TransferFormDraft(
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val amountText: String,
    val note: String,
    val occurredAtMillis: Long,
    val noteError: String?,
    val fromAccountError: String? = null,
    val toAccountError: String? = null,
    val amountError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean,
    val operationId: String,
) : Serializable

data class EditCashFlowFormDraft(
    val selectedAccountId: Long?,
    val amountText: String,
    val note: String,
    val occurredAtMillis: Long,
    val noteTouched: Boolean,
    val noteError: String?,
    val accountError: String? = null,
    val amountError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean,
    val hasConflict: Boolean = false,
    val expectedUpdatedAt: Long,
) : Serializable

data class EditTransferFormDraft(
    val fromAccountId: Long?,
    val toAccountId: Long?,
    val amountText: String,
    val note: String,
    val occurredAtMillis: Long,
    val noteTouched: Boolean,
    val noteError: String?,
    val fromAccountError: String? = null,
    val toAccountError: String? = null,
    val amountError: String? = null,
    val occurredAtError: String? = null,
    val isDirty: Boolean,
    val hasConflict: Boolean = false,
    val expectedUpdatedAt: Long,
) : Serializable
