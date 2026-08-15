package com.shihuaidexianyu.money.ui.record

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.ValidationErrorText

const val MAX_LEDGER_NOTE_LENGTH = 200

data class TransferAccountSelection(
    val fromAccountId: Long?,
    val toAccountId: Long?,
)

fun defaultCashAccountId(
    accounts: List<Account>,
    recentAccountIds: List<Long>,
    explicitAccountId: Long?,
): Long? {
    val openIds = accounts.asSequence()
        .filter { it.closedAt == null }
        .map(Account::id)
        .toList()
    return explicitAccountId?.takeIf(openIds::contains)
        ?: recentAccountIds.firstOrNull(openIds::contains)
        ?: openIds.firstOrNull()
}

fun defaultTransferAccountIds(
    accounts: List<Account>,
    recentAccountIds: List<Long>,
    explicitFromAccountId: Long?,
): TransferAccountSelection {
    val openAccounts = accounts.filter { it.closedAt == null }
    val fromAccountId = defaultCashAccountId(
        accounts = openAccounts,
        recentAccountIds = recentAccountIds,
        explicitAccountId = explicitFromAccountId,
    )
    return TransferAccountSelection(
        fromAccountId = fromAccountId,
        toAccountId = openAccounts.firstOrNull { it.id != fromAccountId }?.id,
    )
}

/**
 * Returns the note-length error text when [value] (trimmed) exceeds [MAX_LEDGER_NOTE_LENGTH],
 * or null when the note is acceptable. Shared by all ledger form ViewModels so the live
 * `noteError` preview and the final `normalizeLedgerNote` check never drift apart.
 */
fun ledgerNoteLengthError(value: String): String? =
    if (value.trim().length > MAX_LEDGER_NOTE_LENGTH) {
        ValidationErrorText.noteTooLong(MAX_LEDGER_NOTE_LENGTH)
    } else {
        null
    }

fun normalizeLedgerNote(value: String): String {
    val normalized = value.trim()
    require(normalized.length <= MAX_LEDGER_NOTE_LENGTH) {
        ValidationErrorText.noteTooLong(MAX_LEDGER_NOTE_LENGTH)
    }
    return normalized
}
