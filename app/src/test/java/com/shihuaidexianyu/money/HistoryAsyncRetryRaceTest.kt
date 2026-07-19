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
import com.shihuaidexianyu.money.domain.model.DevicePreferences
import com.shihuaidexianyu.money.domain.model.HistoryFilters
import com.shihuaidexianyu.money.domain.repository.PortableSettingsRepository
import com.shihuaidexianyu.money.domain.repository.AccountRepository
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
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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
            assertNull(viewModel.uiState.value.errorMessageRes)
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
        assertNull(viewModel.uiState.value.loadMoreErrorMessageRes)
        assertNull(viewModel.uiState.value.errorMessageRes)
    }

    @Test
    fun `account rename reloads search corpus so old name disappears and new name appears`() = runTest(dispatcher) {
        val names = mutableMapOf<Long, String>()
        val accounts = InMemoryAccountRepository()
        val accountId = accounts.createAccount(Account(name = "旧工资卡", initialBalance = 0L, createdAt = 1L))
        names[accountId] = "旧工资卡"
        val transactions = InMemoryTransactionRepository(names::get)
        transactions.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100L,
                note = "工资",
                occurredAt = 2L,
                createdAt = 2L,
                updatedAt = 2L,
                operationId = "history-account-rename",
            ),
        )
        val viewModel = HistoryViewModel(
            accountRepository = accounts,
            transactionRepository = transactions,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(
                DevicePreferences(historyFilters = HistoryFilters(keyword = "旧工资卡")),
            ),
        )
        runCurrent()
        assertEquals(1, viewModel.uiState.value.records.size)

        names[accountId] = "新工资卡"
        accounts.updateAccount(requireNotNull(accounts.getAccountById(accountId)).copy(name = "新工资卡"))
        runCurrent()

        assertTrue(viewModel.uiState.value.records.isEmpty())
        viewModel.updateKeyword("新工资卡")
        advanceTimeBy(300L)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.records.size)
    }

    @Test
    fun `invalid or overflowing amount text shows field error and never runs broadened query`() = runTest(dispatcher) {
        val delegate = InMemoryTransactionRepository()
        delegate.insertCashFlowRecord(
            CashFlowRecord(
                accountId = 1L,
                direction = CashFlowDirection.INFLOW.value,
                amount = 100L,
                note = "不应在非法筛选下显示",
                occurredAt = 1L,
                createdAt = 1L,
                updatedAt = 1L,
                operationId = "history-invalid-filter",
            ),
        )
        val repository = CountingHistoryRepository(delegate)
        val viewModel = HistoryViewModel(
            accountRepository = InMemoryAccountRepository(),
            transactionRepository = repository,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
        )
        runCurrent()
        val initialQueries = repository.queryCount
        val initialCounts = repository.countCount
        assertEquals(1, viewModel.uiState.value.records.size)

        viewModel.updateMinAmount("999999999999999999999999999999")
        advanceTimeBy(300L)
        runCurrent()

        assertEquals(R.string.validation_valid_amount, viewModel.uiState.value.minAmountErrorRes)
        assertTrue(viewModel.uiState.value.records.isEmpty())
        assertEquals(initialQueries, repository.queryCount)
        assertEquals(initialCounts, repository.countCount)

        viewModel.updateMinAmount("")
        viewModel.updateMaxAmount("not-an-amount")
        advanceTimeBy(300L)
        runCurrent()
        assertEquals(R.string.validation_valid_amount, viewModel.uiState.value.maxAmountErrorRes)
        assertEquals(initialQueries, repository.queryCount)
        assertEquals(initialCounts, repository.countCount)

        viewModel.updateMaxAmount("")
        viewModel.updateMinAmount("1.00")
        advanceTimeBy(300L)
        runCurrent()
        assertNull(viewModel.uiState.value.minAmountErrorRes)
        assertNull(viewModel.uiState.value.maxAmountErrorRes)
        assertTrue(repository.queryCount > initialQueries)
    }

    @Test
    fun `invalid amount clears an earlier repository error and retry token`() = runTest(dispatcher) {
        val viewModel = HistoryViewModel(
            accountRepository = InMemoryAccountRepository(),
            transactionRepository = AlwaysFailingHistoryRepository(InMemoryTransactionRepository()),
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
        )
        runCurrent()
        assertEquals(R.string.history_load_failed, viewModel.uiState.value.errorMessageRes)
        assertTrue(viewModel.uiState.value.retryToken != null)

        viewModel.updateMinAmount("overflow-overflow")
        advanceTimeBy(300L)
        runCurrent()

        assertNull(viewModel.uiState.value.errorMessageRes)
        assertNull(viewModel.uiState.value.retryToken)
        assertEquals(R.string.validation_valid_amount, viewModel.uiState.value.minAmountErrorRes)
    }

    @Test
    fun `single account flow subscription cannot lose an emission between first value and collection`() =
        runTest(dispatcher) {
            val old = Account(id = 1L, name = "旧名称", initialBalance = 0L, createdAt = 1L)
            val renamed = old.copy(name = "新名称")
            val viewModel = HistoryViewModel(
                accountRepository = GapEmittingAccountRepository(old, renamed),
                transactionRepository = InMemoryTransactionRepository(),
                portableSettingsRepository = InMemoryPortableSettingsRepository(),
                devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
            )

            runCurrent()

            assertEquals("新名称", viewModel.uiState.value.accountOptions.single().name)
        }

}

private class CapturingHistoryRepository(
    private val delegate: TransactionRepository,
) : TransactionRepository by delegate {
    var lastFilters: HistoryRecordFilters? = null

    override suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord> {
        lastFilters = filters
        return delegate.queryHistoryRecords(filters, cursor, limit)
    }
}

private class AlwaysFailingHistoryRepository(
    private val delegate: TransactionRepository,
) : TransactionRepository by delegate {
    override suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord> = error("history failure")
}

private class GapEmittingAccountRepository(
    private val old: Account,
    private val renamed: Account,
    private val delegate: InMemoryAccountRepository = InMemoryAccountRepository(),
) : AccountRepository by delegate {
    private var subscriptions = 0

    override fun observeAllAccounts(): Flow<List<Account>> = flow {
        subscriptions += 1
        if (subscriptions == 1) {
            emit(listOf(old))
            yield()
            emit(listOf(renamed))
        } else {
            emit(listOf(renamed))
        }
    }
}

private class CountingHistoryRepository(
    private val delegate: TransactionRepository,
) : TransactionRepository by delegate {
    var queryCount = 0
    var countCount = 0

    override suspend fun queryHistoryRecords(
        filters: HistoryRecordFilters,
        cursor: HistoryPageCursor?,
        limit: Int,
    ): List<HistoryRecord> {
        queryCount += 1
        return delegate.queryHistoryRecords(filters, cursor, limit)
    }

    override suspend fun countHistoryRecords(filters: HistoryRecordFilters): Int {
        countCount += 1
        return delegate.countHistoryRecords(filters)
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
