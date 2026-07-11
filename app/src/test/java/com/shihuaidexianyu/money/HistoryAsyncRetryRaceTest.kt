package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.PortableSettings
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryPageCursor
import com.shihuaidexianyu.money.domain.model.HistoryRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordFilters
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.TransactionRepository
import com.shihuaidexianyu.money.ui.history.HistoryViewModel
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryAsyncRetryRaceTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `retry cancellation cannot let the old initialization overwrite success with error`() =
        runTest(dispatcher) {
            val settingsRepository = FirstQueryBlocksPortableSettingsRepository()
            val viewModel = HistoryViewModel(
                accountRepository = InMemoryAccountRepository(),
                transactionRepository = InMemoryTransactionRepository(),
                portableSettingsRepository = settingsRepository,
                devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
            )
            runCurrent()
            assertEquals(1, settingsRepository.queryCount)

            viewModel.retry()
            runCurrent()

            assertEquals(2, settingsRepository.queryCount)
            assertTrue(viewModel.uiState.value.hasCommittedContent)
            assertNull(viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `late failing load more cannot pollute records after filter reload`() = runTest(dispatcher) {
        val accounts = InMemoryAccountRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 1L))
        val delegate = InMemoryTransactionRepository()
        repeat(101) { index ->
            delegate.insertCashFlowRecord(
                CashFlowRecord(
                    accountId = accountId,
                    direction = CashFlowDirection.OUTFLOW.value,
                    amount = 100L,
                    note = "记录$index",
                    occurredAt = index.toLong() + 1L,
                    createdAt = index.toLong() + 1L,
                    updatedAt = index.toLong() + 1L,
                    operationId = "history-race-$index",
                ),
            )
        }
        val repository = LateFailingLoadMoreRepository(delegate)
        val viewModel = HistoryViewModel(
            accountRepository = accounts,
            transactionRepository = repository,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
        )
        runCurrent()
        assertEquals(100, viewModel.uiState.value.records.size)

        viewModel.loadMore()
        runCurrent()
        assertTrue(repository.loadMoreStarted.isCompleted)
        viewModel.updateKeyword("不存在的筛选词")
        advanceTimeBy(300L)
        runCurrent()
        assertTrue(viewModel.uiState.value.records.isEmpty())

        repository.releaseLateFailure.complete(Unit)
        runCurrent()

        assertTrue(viewModel.uiState.value.records.isEmpty())
        assertNull(viewModel.uiState.value.loadMoreErrorMessage)
        assertNull(viewModel.uiState.value.errorMessage)
    }
}

private class FirstQueryBlocksPortableSettingsRepository(
    private val delegate: PortableSettingsRepository = InMemoryPortableSettingsRepository(),
) : PortableSettingsRepository by delegate {
    var queryCount: Int = 0
        private set

    override suspend fun query(): PortableSettings {
        queryCount += 1
        if (queryCount == 1) awaitCancellation()
        return delegate.query()
    }
}

private class LateFailingLoadMoreRepository(
    private val delegate: TransactionRepository,
) : TransactionRepository by delegate {
    val loadMoreStarted = CompletableDeferred<Unit>()
    val releaseLateFailure = CompletableDeferred<Unit>()

    override suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord> {
        if (cursor == null) return delegate.queryHistoryRecords(filters, cursor, limit)
        loadMoreStarted.complete(Unit)
        try {
            releaseLateFailure.await()
        } catch (_: CancellationException) {
            withContext(NonCancellable) { releaseLateFailure.await() }
        }
        error("late page failure")
    }
}
