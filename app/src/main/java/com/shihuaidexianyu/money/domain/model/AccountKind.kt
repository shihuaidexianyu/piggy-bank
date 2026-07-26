package com.shihuaidexianyu.money.domain.model

/**
 * What an account *is*, decided once at account level — deliberately NOT a per-record category
 * system. The meaning of a ledger event is derived at read time from its account's kind, so
 * reclassifying an account retroactively reinterprets its whole history: a reconciliation delta
 * on a FUNDING account reads as an error correction, the same delta on an INVESTMENT account
 * reads as investment profit or loss.
 *
 * An earlier heavyweight design (the `investment_settlements` table, dropped in DB migration
 * 3→4) tracked investments as separate record machinery; this is intentionally the minimal
 * replacement.
 */
enum class AccountKind(val value: String) {
    /** Everyday cash/bank accounts (日常). */
    FUNDING("funding"),

    /** Investment holdings — funds, stocks, wealth products (投资). */
    INVESTMENT("investment"),
    ;

    companion object {
        val DEFAULT: AccountKind = FUNDING

        fun fromValue(value: String?): AccountKind =
            entries.firstOrNull { it.value == value } ?: DEFAULT
    }
}
