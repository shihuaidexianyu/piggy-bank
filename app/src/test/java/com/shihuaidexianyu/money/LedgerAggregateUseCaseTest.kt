package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountActivityMaxima
import com.shihuaidexianyu.money.domain.model.AccountLedgerAggregate
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.LedgerOverflowException
import com.shihuaidexianyu.money.domain.repository.LedgerAggregateRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.LedgerBalanceCalculator
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import org.junit.Test

class LedgerAggregateUseCaseTest {
    @Test
    fun `batch balances use one aggregate read and return zero for unopened account`() = runBlocking {
        val ledger = InMemoryTransactionRepository()
        val spy = CountingAggregateRepository(ledger)
        val open = Account(id = 1L, name = "开放", initialBalance = 10L, createdAt = 0L)
        val future = Account(id = 2L, name = "未来", initialBalance = 99L, createdAt = 120_035L)
        ledger.insertCashFlowRecord(cash(1L, amount = 5L, occurredAt = 10L))
        val useCase = CalculateAccountBalancesUseCase(
            ledgerAggregateRepository = spy,
            clockProvider = testClockProvider(60_000L),
        )

        val balances = useCase.before(listOf(open, future), endExclusive = 120_000L)

        assertEquals(mapOf(1L to 15L, 2L to 0L), balances)
        assertEquals(1, spy.beforeCalls)
    }

    @Test
    fun `empty batch does not call aggregate repository`() = runBlocking {
        val spy = CountingAggregateRepository(InMemoryTransactionRepository())
        val useCase = CalculateAccountBalancesUseCase(spy, testClockProvider())

        assertEquals(emptyMap(), useCase(emptyList()))
        assertEquals(0, spy.beforeCalls + spy.atCalls)
    }

    @Test
    fun `single current balance uses aggregate and final addition overflow is domain failure`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(
            Account(name = "溢出", initialBalance = Long.MAX_VALUE, createdAt = 0L),
        )
        ledger.insertCashFlowRecord(cash(accountId, amount = 1L, occurredAt = 1L))
        val spy = CountingAggregateRepository(ledger)
        val useCase = CalculateCurrentBalanceUseCase(
            accountRepository = accounts,
            ledgerAggregateRepository = spy,
            clockProvider = testClockProvider(1L),
        )

        assertFailsWith<LedgerOverflowException> { useCase(accountId) }
        assertEquals(1, spy.beforeCalls)
        Unit
    }

    @Test
    fun `balance update context uses one aggregate read and excludes edited record id`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 100L, createdAt = 0L))
        val excludedId = ledger.insertBalanceUpdateRecord(
            balanceUpdate(accountId, delta = 50L, operationId = "excluded"),
        ).recordId
        ledger.insertBalanceUpdateRecord(balanceUpdate(accountId, delta = -20L, operationId = "included"))
        val spy = CountingAggregateRepository(ledger)
        val useCase = ResolveBalanceUpdateContextUseCase(accounts, spy)

        val context = useCase(accountId, occurredAt = 10L, excludingRecordId = excludedId)

        assertEquals(80L, context.systemBalanceBeforeUpdate)
        assertEquals(1, spy.beforeCalls)
        assertEquals(excludedId, spy.lastExcludedBalanceUpdateId)
    }

    @Test
    fun `activity refresh uses one maxima read and updates both timestamps`() = runBlocking {
        val accounts = InMemoryAccountRepository()
        val ledger = InMemoryTransactionRepository()
        val accountId = accounts.createAccount(Account(name = "现金", initialBalance = 0L, createdAt = 100L))
        ledger.insertCashFlowRecord(cash(accountId, amount = 1L, occurredAt = 300L))
        ledger.insertBalanceUpdateRecord(
            balanceUpdate(accountId, delta = 1L, operationId = "activity").copy(occurredAt = 200L),
        )
        val spy = CountingAggregateRepository(ledger)
        val useCase = RefreshAccountActivityStateUseCase(
            accountRepository = accounts,
            ledgerAggregateRepository = spy,
        )

        useCase(accountId)

        val refreshed = requireNotNull(accounts.getAccountById(accountId))
        assertEquals(300L, refreshed.lastUsedAt)
        assertEquals(200L, refreshed.lastBalanceUpdateAt)
        assertEquals(1, spy.activityCalls)
    }

    @Test
    fun `record helper component overflow uses the shared domain failure`() {
        val account = Account(id = 1L, name = "溢出", initialBalance = 0L, createdAt = 0L)

        assertFailsWith<LedgerOverflowException> {
            LedgerBalanceCalculator.deltasFromRecords(
                account = account,
                cashFlows = listOf(
                    cash(account.id, Long.MAX_VALUE, 1L),
                    cash(account.id, 1L, 2L),
                ),
                transfers = emptyList(),
                balanceUpdates = emptyList(),
                adjustments = emptyList(),
                atTimeMillis = 2L,
            )
        }
    }

    private fun cash(accountId: Long, amount: Long, occurredAt: Long) = CashFlowRecord(
        accountId = accountId,
        direction = CashFlowDirection.INFLOW.value,
        amount = amount,
        note = "cash",
        occurredAt = occurredAt,
        createdAt = occurredAt,
        updatedAt = occurredAt,
        operationId = "cash-$accountId-$occurredAt",
    )

    private fun balanceUpdate(accountId: Long, delta: Long, operationId: String) = BalanceUpdateRecord(
        accountId = accountId,
        actualBalance = 9_999L,
        systemBalanceBeforeUpdate = -9_999L,
        delta = delta,
        occurredAt = 10L,
        createdAt = 10L,
        updatedAt = 10L,
        operationId = operationId,
    )

    private class CountingAggregateRepository(
        private val delegate: LedgerAggregateRepository,
    ) : LedgerAggregateRepository {
        var beforeCalls = 0
        var atCalls = 0
        var activityCalls = 0
        var lastExcludedBalanceUpdateId: Long? = null

        override suspend fun queryBefore(
            accounts: List<Account>,
            endExclusive: Long,
            excludingBalanceUpdateId: Long?,
        ): Map<Long, AccountLedgerAggregate> {
            beforeCalls += 1
            lastExcludedBalanceUpdateId = excludingBalanceUpdateId
            return delegate.queryBefore(accounts, endExclusive, excludingBalanceUpdateId)
        }

        override suspend fun queryAt(
            accounts: List<Account>,
            atTimeMillis: Long,
            excludingBalanceUpdateId: Long?,
        ): Map<Long, AccountLedgerAggregate> {
            atCalls += 1
            lastExcludedBalanceUpdateId = excludingBalanceUpdateId
            return delegate.queryAt(accounts, atTimeMillis, excludingBalanceUpdateId)
        }

        override suspend fun queryActivityMaxima(accountId: Long): AccountActivityMaxima {
            activityCalls += 1
            return delegate.queryActivityMaxima(accountId)
        }
    }
}
