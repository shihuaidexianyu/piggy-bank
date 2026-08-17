package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountReminderSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryPortableSettingsRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.model.BalanceUpdateRecord
import com.shihuaidexianyu.money.domain.model.CashFlowDirection
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.ui.accounts.AccountsViewModel
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountsSnapshotQueryCountTest {
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
    fun `account page shares one balance aggregate across cards and closure issues`() =
        runTest(dispatcher) {
            val accounts = InMemoryAccountRepository()
            val transactions = InMemoryTransactionRepository()
            val openId = accounts.createAccount(
                Account(name = "开放", initialBalance = 100L, createdAt = 1L),
            )
            val closedId = accounts.createAccount(
                Account(name = "旧关闭", initialBalance = 50L, createdAt = 1L),
            )
            accounts.closeAccount(closedId, 2L)

            val viewModel = AccountsViewModel(
                accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
                accountRepository = accounts,
                portableSettingsRepository = InMemoryPortableSettingsRepository(),
                transactionRepository = transactions,
                calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions) { 10L },
                clockProvider = testClockProvider(10L),
                zoneIdProvider = testZoneIdProvider(ZoneOffset.UTC),
            )
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(openId, state.openAccounts.single().id)
            assertTrue(state.closedAccounts.single().requiresReopenAndSettle)
            assertEquals(1, transactions.transactionInvocationCount)

            viewModel.toggleClosedVisibility()
            runCurrent()
            assertEquals(1, transactions.transactionInvocationCount)
        }

    @Test
    fun `rows carry the signed net change of the current month`() =
        runTest(dispatcher) {
            val now = Instant.parse("2026-08-16T10:00:00Z").toEpochMilli()
            val insideMonth = Instant.parse("2026-08-10T09:00:00Z").toEpochMilli()
            val outsideMonth = Instant.parse("2026-07-31T09:00:00Z").toEpochMilli()
            val accounts = InMemoryAccountRepository()
            val transactions = InMemoryTransactionRepository()
            val fundingId = accounts.createAccount(
                Account(name = "日常", initialBalance = 0L, createdAt = 1L),
            )
            val investmentId = accounts.createAccount(
                Account(
                    name = "基金",
                    initialBalance = 0L,
                    createdAt = 1L,
                    kind = AccountKind.INVESTMENT,
                ),
            )
            insertCashFlow(transactions, fundingId, 400L, insideMonth)
            insertCashFlow(transactions, fundingId, 700L, outsideMonth)
            // A transfer in offsets part of the spending; the reconciliation delta on the
            // investment account is its P&L and simply joins the net change.
            transactions.insertTransferRecord(
                TransferRecord(
                    fromAccountId = investmentId,
                    toAccountId = fundingId,
                    amount = 100L,
                    note = "",
                    occurredAt = insideMonth,
                    createdAt = insideMonth,
                    updatedAt = insideMonth,
                    operationId = testOperationId(),
                ),
            )
            transactions.insertBalanceUpdateRecord(
                BalanceUpdateRecord(
                    accountId = investmentId,
                    actualBalance = 200L,
                    systemBalanceBeforeUpdate = 0L,
                    delta = 300L,
                    occurredAt = insideMonth,
                    createdAt = insideMonth,
                    updatedAt = insideMonth,
                    operationId = testOperationId(),
                ),
            )

            val viewModel = AccountsViewModel(
                accountReminderSettingsRepository = InMemoryAccountReminderSettingsRepository(),
                accountRepository = accounts,
                portableSettingsRepository = InMemoryPortableSettingsRepository(),
                transactionRepository = transactions,
                calculateAccountBalancesUseCase = CalculateAccountBalancesUseCase(transactions) { now },
                clockProvider = testClockProvider(now),
                zoneIdProvider = testZoneIdProvider(ZoneOffset.UTC),
            )
            runCurrent()

            val funding = viewModel.uiState.value.openAccounts.single { it.id == fundingId }
            val investment = viewModel.uiState.value.openAccounts.single { it.id == investmentId }
            // −400 spending + 100 transfer in; the July record stays out.
            assertEquals(-300L, funding.monthNetChange)
            // −100 transfer out + 300 reconciliation delta.
            assertEquals(200L, investment.monthNetChange)
        }

    private suspend fun insertCashFlow(
        transactions: InMemoryTransactionRepository,
        accountId: Long,
        amount: Long,
        occurredAt: Long,
    ) {
        transactions.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = CashFlowDirection.OUTFLOW.value,
                amount = amount,
                note = "",
                occurredAt = occurredAt,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                operationId = testOperationId(),
            ),
        )
    }
}
