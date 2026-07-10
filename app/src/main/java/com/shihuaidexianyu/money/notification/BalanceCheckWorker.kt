package com.shihuaidexianyu.money.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase

internal suspend fun calculateBalanceCheckBalances(
    accounts: List<Account>,
    calculateAccountBalancesUseCase: CalculateAccountBalancesUseCase,
): Map<Long, Long> = calculateAccountBalancesUseCase(accounts)

/**
 * Compatibility shell for already persisted WorkManager requests from releases before 2.2.
 * It must remain side-effect free for one upgrade cycle.
 */
class BalanceCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()
}
