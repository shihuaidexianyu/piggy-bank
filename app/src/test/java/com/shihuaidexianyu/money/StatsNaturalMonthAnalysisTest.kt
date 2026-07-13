package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryDevicePreferencesRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.HistoryRecordType
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.model.ledgerSumExact
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.navigation.HistoryFilterRequestCodec
import com.shihuaidexianyu.money.ui.stats.StatsViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

class StatsNaturalMonthAnalysisTest {
    private val zoneId = ZoneId.of("America/New_York")

    @Test
    fun `equal transfer totals use stable account-id tie breaks`() = runBlocking {
        val ledger = InMemoryTransactionRepository()
        val occurredAt = millis(2024, 3, 10, 12)
        suspend fun insert(from: Long, to: Long, operationId: String) {
            ledger.insertTransferRecord(
                TransferRecord(
                    fromAccountId = from,
                    toAccountId = to,
                    amount = 50L,
                    note = "",
                    occurredAt = occurredAt,
                    createdAt = occurredAt,
                    updatedAt = occurredAt,
                    operationId = operationId,
                ),
            )
        }
        insert(2L, 3L, "stable-2-3")
        insert(1L, 4L, "stable-1-4")
        insert(1L, 3L, "stable-1-3")

        assertEquals(
            listOf(1L to 3L, 1L to 4L, 2L to 3L),
            ledger.queryTransferPathTotalsBetween(
                startInclusive = millis(2024, 3, 1),
                endExclusive = millis(2024, 4, 1),
            ).map { it.fromAccountId to it.toAccountId },
        )
    }

    @Test
    fun `analysis builds DST-safe daily account and transfer modules with exact drill-downs`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val hiddenId = accounts.createAccount(Account(name = "隐藏账户", initialBalance = 0L, createdAt = millis(2024, 2, 1)))
        accounts.setHidden(hiddenId, true)
        val closedId = accounts.createAccount(Account(name = "关闭账户", initialBalance = 0L, createdAt = millis(2024, 2, 1)))
        val march10Start = millis(2024, 3, 10)
        val march11Start = millis(2024, 3, 11)
        insertCash(ledger, hiddenId, CashFlowDirection.INFLOW, 100L, millis(2024, 3, 10, 0, 30))
        insertCash(ledger, closedId, CashFlowDirection.OUTFLOW, 40L, millis(2024, 3, 10, 23, 30))
        insertCash(ledger, closedId, CashFlowDirection.INFLOW, 20L, millis(2024, 3, 11, 0, 30))
        ledger.insertTransferRecord(
            TransferRecord(
                fromAccountId = hiddenId,
                toAccountId = closedId,
                amount = 70L,
                note = "转存",
                occurredAt = millis(2024, 3, 10, 12),
                createdAt = millis(2024, 3, 10, 12),
                updatedAt = millis(2024, 3, 10, 12),
                operationId = "stats-transfer-path",
            ),
        )
        accounts.closeAccount(closedId, millis(2024, 3, 20))
        val now = millis(2024, 3, 15, 12)
        val useCase = analysisUseCase(accounts, ledger)

        val snapshot = useCase(MutableStateFlow(StatsRangeSelection(StatsPeriod.MONTH, now))).first()

        assertEquals(120L, snapshot.totalInflow)
        assertEquals(40L, snapshot.totalOutflow)
        assertEquals(80L, snapshot.netCashFlow)
        assertEquals(23L * 60L * 60L * 1_000L, march11Start - march10Start)
        val march10 = snapshot.dailyPoints.single { it.date == LocalDate.of(2024, 3, 10) }
        assertEquals(100L, march10.inflow)
        assertEquals(40L, march10.outflow)
        assertEquals(60L, march10.netFlow)
        assertEquals(march10Start, march10.historyFilters.dateStartAt)
        assertEquals(march11Start, march10.historyFilters.dateEndAt)
        assertEquals(setOf(HistoryRecordType.CASH_FLOW), march10.historyFilters.recordTypes)

        assertTrue(snapshot.accountCashFlows.isEmpty())
        assertTrue(snapshot.transferPaths.isEmpty())

        val allDrillDowns = buildList {
            add(snapshot.inflowHistoryFilters)
            add(snapshot.outflowHistoryFilters)
            add(snapshot.netCashFlowHistoryFilters)
            snapshot.dailyPoints.forEach { add(it.historyFilters) }
        }
        allDrillDowns.forEach { filters ->
            assertEquals(filters, HistoryFilterRequestCodec.decode(HistoryFilterRequestCodec.encode(filters)))
            assertTrue(requireNotNull(filters.dateStartAt) >= snapshot.range.startInclusive)
            assertTrue(requireNotNull(filters.dateEndAt) <= snapshot.range.endExclusive)
            assertTrue(filters.dateStartAt < filters.dateEndAt)
            assertTrue(filters.recordTypes.isNotEmpty())
        }
        suspend fun signedTotal(filters: com.shihuaidexianyu.money.domain.model.HistoryRecordFilters): Long {
            val amounts = mutableListOf<Long>()
            var cursor: com.shihuaidexianyu.money.domain.model.HistoryPageCursor? = null
            do {
                val page = ledger.queryHistoryRecords(filters, cursor, 100)
                amounts += page.map { it.amount }
                cursor = page.lastOrNull()?.cursor
            } while (page.size == 100)
            return amounts.ledgerSumExact()
        }
        assertEquals(snapshot.totalInflow, signedTotal(snapshot.inflowHistoryFilters))
        assertEquals(-snapshot.totalOutflow, signedTotal(snapshot.outflowHistoryFilters))
        assertEquals(snapshot.netCashFlow, signedTotal(snapshot.netCashFlowHistoryFilters))
        snapshot.dailyPoints.forEach { point ->
            assertEquals(point.netFlow, signedTotal(point.historyFilters))
        }
    }

    private fun analysisUseCase(
        accounts: InMemoryAccountRepository,
        ledger: InMemoryTransactionRepository,
    ) = ObserveStatsDashboardUseCase(
        accountRepository = accounts,
        portableSettingsRepository = InMemoryPortableSettingsRepository(),
        transactionRepository = ledger,
        calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(ledger),
        zoneIdProvider = testZoneIdProvider(zoneId),
    )

    private suspend fun insertCash(
        ledger: InMemoryTransactionRepository,
        accountId: Long,
        direction: CashFlowDirection,
        amount: Long,
        occurredAt: Long,
    ) {
        ledger.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = direction.value,
                amount = amount,
                note = "普通备注，不作为分类",
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        )
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zoneId).toInstant().toEpochMilli()
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsMonthNavigationViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val zoneId = ZoneId.of("Asia/Shanghai")

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `analysis defaults to injected current month and never moves into future`() = runBlocking {
        val now = ZonedDateTime.of(2024, 3, 15, 12, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val savingsGoalRepository = InMemorySavingsGoalRepository().also {
            it.upsert(targetAmount = 100_000L, now = now)
        }
        accounts.createAccount(Account(name = "账户", initialBalance = 0L, createdAt = 1L))
        val useCase = ObserveStatsDashboardUseCase(
            accountRepository = accounts,
            portableSettingsRepository = InMemoryPortableSettingsRepository(),
            transactionRepository = ledger,
            calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(ledger),
            zoneIdProvider = testZoneIdProvider(zoneId),
        )
        val viewModel = StatsViewModel(
            observeStatsDashboardUseCase = useCase,
            devicePreferencesRepository = InMemoryDevicePreferencesRepository(),
            savingsGoalRepository = savingsGoalRepository,
            clockProvider = testClockProvider(now),
            zoneIdProvider = testZoneIdProvider(zoneId),
        )
        val current = withTimeout(5_000L) { viewModel.uiState.first { it.hasCommittedContent || it.errorMessageRes != null } }
        assertEquals(null, current.errorMessageRes)
        assertEquals("2024年3月", current.rangeText)
        assertFalse(current.canNavigateNext)
        assertEquals(100_000L, current.netWorthGoalTargetAmount)
        val currentStart = current.rangeStartInclusive
        viewModel.moveToNextRange()
        assertEquals(currentStart, viewModel.uiState.value.rangeStartInclusive)

        viewModel.moveToPreviousRange()
        val previous = withTimeout(5_000L) { viewModel.uiState.first { it.rangeStartInclusive != currentStart } }
        assertEquals("2024年2月", previous.rangeText)
        assertTrue(previous.canNavigateNext)
        viewModel.moveToNextRange()
        val returned = withTimeout(5_000L) { viewModel.uiState.first { it.rangeStartInclusive == currentStart } }
        assertEquals("2024年3月", returned.rangeText)
        assertFalse(returned.canNavigateNext)
    }
}
