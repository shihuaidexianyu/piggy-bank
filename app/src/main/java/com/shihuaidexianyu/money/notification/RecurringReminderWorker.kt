package com.shihuaidexianyu.money.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shihuaidexianyu.money.MoneyAppContainer

/**
 * Compatibility shell for already persisted WorkManager requests from releases before 2.2.
 * It must remain side-effect free for one upgrade cycle.
 */
class RecurringReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = Result.success()
}

/** Implemented by [com.shihuaidexianyu.money.MoneyApplication] so the worker can reach the DI container. */
interface MoneyAppContainerProvider {
    val moneyAppContainer: MoneyAppContainer
}
