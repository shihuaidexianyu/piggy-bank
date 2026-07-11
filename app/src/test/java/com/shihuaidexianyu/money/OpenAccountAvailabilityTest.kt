package com.shihuaidexianyu.money

import app.cash.turbine.test
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.navigation.OpenAccountAvailability
import com.shihuaidexianyu.money.navigation.openAccountAvailability
import java.util.concurrent.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class OpenAccountAvailabilityTest {
    @Test
    fun `never emitting source remains loading instead of becoming zero accounts`() = runTest {
        openAccountAvailability(flow<List<Account>> { awaitCancellation() }).test {
            assertIs<OpenAccountAvailability.Loading>(awaitItem())
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delayed first emission moves from loading to data`() = runTest {
        val accounts = listOf(account(id = 1L), account(id = 2L))
        openAccountAvailability(
            flow {
                delay(100)
                emit(accounts)
            },
        ).test {
            assertIs<OpenAccountAvailability.Loading>(awaitItem())
            val data = assertIs<OpenAccountAvailability.Data>(awaitItem())
            assertEquals(2, data.openAccountCount)
            awaitComplete()
        }
    }

    @Test
    fun `source failure becomes error while cancellation is rethrown`() = runTest {
        openAccountAvailability(flow<List<Account>> { error("boom") }).test {
            assertIs<OpenAccountAvailability.Loading>(awaitItem())
            assertIs<OpenAccountAvailability.Error>(awaitItem())
            awaitComplete()
        }

        openAccountAvailability(
            flow<List<Account>> { throw CancellationException("cancel") },
        ).test {
            assertIs<OpenAccountAvailability.Loading>(awaitItem())
            assertIs<CancellationException>(awaitError())
        }
    }

    private fun account(id: Long) = Account(
        id = id,
        name = "账户$id",
        initialBalance = 0L,
        createdAt = 1L,
    )
}
