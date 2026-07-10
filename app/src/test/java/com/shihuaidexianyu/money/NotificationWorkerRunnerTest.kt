package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.notification.NotificationWorkerOutcome
import com.shihuaidexianyu.money.notification.NotificationWorkerRunner
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import org.junit.Test

class NotificationWorkerRunnerTest {
    @Test
    fun `startup not ready retries without ledger access`() = runBlocking {
        var ledgerReads = 0
        val runner = NotificationWorkerRunner(
            isStartupReady = { false },
            sync = { ledgerReads++ },
        )

        assertEquals(NotificationWorkerOutcome.RETRY, runner.run())
        assertEquals(0, ledgerReads)
    }

    @Test
    fun `ready sync succeeds and failure retries`() = runBlocking {
        assertEquals(
            NotificationWorkerOutcome.SUCCESS,
            NotificationWorkerRunner({ true }, {}).run(),
        )
        assertEquals(
            NotificationWorkerOutcome.RETRY,
            NotificationWorkerRunner({ true }, { error("db") }).run(),
        )
    }
}
