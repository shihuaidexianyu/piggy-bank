package com.shihuaidexianyu.money.ui.record

import com.shihuaidexianyu.money.domain.model.Account

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

fun normalizeLedgerNote(value: String): String {
    val normalized = value.trim()
    require(normalized.length <= MAX_LEDGER_NOTE_LENGTH) {
        "备注不能超过 $MAX_LEDGER_NOTE_LENGTH 个字符"
    }
    return normalized
}
