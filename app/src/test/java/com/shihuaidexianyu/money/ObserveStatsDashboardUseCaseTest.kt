package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.StatsPeriod
import com.shihuaidexianyu.money.domain.model.StatsRangeSelection
import com.shihuaidexianyu.money.domain.usecase.ObserveStatsDashboardUseCase
import com.shihuaidexianyu.money.util.TimeRangeUtils
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test

class ObserveStatsDashboardUseCaseTest {
    @Test
    fun `stats dashboard targets a past natural month and coerces legacy periods to month`() = runBlocking {
        val zone = ZoneId.of("UTC")
        val januaryAnchor = java.time.LocalDate.of(2024, 1, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val januaryRange = TimeRangeUtils.statsRange(StatsPeriod.MONTH, zone, januaryAnchor)
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(Account(name = "主账户", initialBalance = 0L, createdAt = 1L))
        ledger.insertCashFlowRecord(cash(accountId, 3_000L, CashFlowDirection.INFLOW, januaryRange.startInclusive + 1L))
        val useCase = useCase(accounts, ledger, zone)

        val snapshot = useCase(MutableStateFlow(StatsRangeSelection(StatsPeriod.WEEK, januaryAnchor))).first()

        assertEquals(StatsPeriod.MONTH, snapshot.period)
        assertEquals(januaryRange, snapshot.range)
        assertEquals(3_000L, snapshot.totalInflow)
    }

    @Test
    fun `hidden and closed account history remains in monthly cash analysis`() = runBlocking {
        val zone = ZoneId.of("UTC")
        val anchor = java.time.LocalDate.of(2024, 3, 15).atStartOfDay(zone).toInstant().toEpochMilli()
        val range = TimeRangeUtils.statsRange(StatsPeriod.MONTH, zone, anchor)
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val hidden = accounts.createAccount(Account(name = "隐藏", initialBalance = 0L, createdAt = 1L))
        accounts.setHidden(hidden, true)
        val closed = accounts.createAccount(Account(name = "关闭", initialBalance = 0L, createdAt = 1L))
        ledger.insertCashFlowRecord(cash(hidden, 2_000L, CashFlowDirection.INFLOW, range.startInclusive + 1L))
        ledger.insertCashFlowRecord(cash(closed, 800L, CashFlowDirection.OUTFLOW, range.startInclusive + 2L))
        accounts.closeAccount(closed, range.startInclusive + 3L)

        val snapshot = useCase(accounts, ledger, zone)
            .invoke(MutableStateFlow(StatsRangeSelection(StatsPeriod.MONTH, anchor)))
            .first()

        assertEquals(2_000L, snapshot.totalInflow)
        assertEquals(800L, snapshot.totalOutflow)
        assertEquals(setOf(hidden, closed), snapshot.accountCashFlows.map { it.accountId }.toSet())
        assertTrue(snapshot.hasSourceAccounts)
    }

    private fun useCase(
        accounts: InMemoryAccountRepository,
        ledger: InMemoryTransactionRepository,
        zoneId: ZoneId,
    ) = ObserveStatsDashboardUseCase(
        accountRepository = accounts,
        portableSettingsRepository = InMemoryPortableSettingsRepository(),
        transactionRepository = ledger,
        zoneIdProvider = testZoneIdProvider(zoneId),
    )

    private fun cash(
        accountId: Long,
        amount: Long,
        direction: CashFlowDirection,
        occurredAt: Long,
    ) = CashFlowRecord(
        accountId = accountId,
        direction = direction.value,
        amount = amount,
        note = "备注不参与聚合",
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = testOperationId(),
    )
}
