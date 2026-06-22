package com.shihuaidexianyu.money.util

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceUpdateReminderConfig

/**
 * Thin wrapper around [com.shihuaidexianyu.money.domain.usecase.AccountStatusCalculator].
 * Kept for UI/test callers; new domain code should use the calculator directly.
 */
object AccountStatusUtils {
    fun isStale(
        account: Account,
        reminderConfig: BalanceUpdateReminderConfig = BalanceUpdateReminderConfig(),
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = com.shihuaidexianyu.money.domain.usecase.AccountStatusCalculator.isStale(account, reminderConfig, nowMillis)
}
