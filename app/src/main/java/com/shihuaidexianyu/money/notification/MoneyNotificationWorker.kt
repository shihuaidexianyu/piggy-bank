package com.shihuaidexianyu.money.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

enum class NotificationWorkerOutcome { SUCCESS, RETRY }

class NotificationWorkerRunner(
    private val isStartupReady: () -> Boolean,
    private val sync: suspend () -> Unit,
) {
    suspend fun run(): NotificationWorkerOutcome {
        if (!isStartupReady()) return NotificationWorkerOutcome.RETRY
        return try {
            sync()
            NotificationWorkerOutcome.SUCCESS
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            NotificationWorkerOutcome.RETRY
        }
    }
}

class MoneyNotificationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as? MoneyAppContainerProvider)?.moneyAppContainer
            ?: return Result.success()
        return processMutex.withLock {
            when (
                NotificationWorkerRunner(
                    isStartupReady = { container.startupMigrationCoordinator.isReady },
                    sync = { container.syncMoneyNotificationsUseCase() },
                ).run()
            ) {
                NotificationWorkerOutcome.SUCCESS -> Result.success()
                NotificationWorkerOutcome.RETRY -> Result.retry()
            }
        }
    }

    private companion object {
        val processMutex = Mutex()
    }
}
