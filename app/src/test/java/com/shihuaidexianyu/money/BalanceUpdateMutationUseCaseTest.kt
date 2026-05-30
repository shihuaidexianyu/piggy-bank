package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.BalanceAdjustmentRecord
import com.shihuaidexianyu.money.domain.model.CashFlowRecord
import com.shihuaidexianyu.money.domain.model.TransferRecord
import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.usecase.CalculateCurrentBalanceUseCase
import com.shihuaidexianyu.money.domain.usecase.DeleteBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.RecalculateBalanceUpdateChainUseCase
import com.shihuaidexianyu.money.domain.usecase.RefreshAccountActivityStateUseCase
import com.shihuaidexianyu.money.domain.usecase.ResolveBalanceUpdateContextUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUpdateRecordUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateBalanceUseCase
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

class BalanceUpdateMutationUseCaseTest {
    @Test
    fun `editing balance update recalculates baseline and current balance`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val recalculateChain = RecalculateBalanceUpdateChainUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val updateBalanceUpdateRecordUseCase = UpdateBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val accountId = accountRepository.createAccount(
            Account(
                name = "银行卡",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 2_000,
                purpose = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
            ),
        )

        updateBalanceUseCase(accountId = accountId, actualBalance = 11_000, occurredAt = 3_000)
        val recordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing record")

        updateBalanceUpdateRecordUseCase(
            recordId = recordId,
            actualBalance = 9_500,
            occurredAt = 1_500,
        )

        val updatedRecord = transactionRepository.getBalanceUpdateRecordById(recordId) ?: error("missing updated record")
        assertEquals(10_000, updatedRecord.systemBalanceBeforeUpdate)
        assertEquals(-500, updatedRecord.delta)
        assertEquals(11_500, calculateCurrentBalanceUseCase(accountId))
    }

    @Test
    fun `deleting latest balance update restores last balance update timestamp`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val recalculateChain = RecalculateBalanceUpdateChainUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val accountId = accountRepository.createAccount(
            Account(
                name = "证券账户",
                initialBalance = 100_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertTransferRecord(
            TransferRecord(
                fromAccountId = 99,
                toAccountId = accountId,
                amount = 20_000,
                note = "入金",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
            ),
        )

        updateBalanceUseCase(accountId = accountId, actualBalance = 130_000, occurredAt = 3_000)
        updateBalanceUseCase(accountId = accountId, actualBalance = 140_000, occurredAt = 5_000)
        val latestRecordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing latest record")

        deleteBalanceUpdateRecordUseCase(latestRecordId)

        assertEquals(3_000, accountRepository.getAccountById(accountId)?.lastBalanceUpdateAt)
        assertNull(transactionRepository.getBalanceUpdateRecordById(latestRecordId))
    }

    @Test
    fun `deleting balance update removes it and restores current balance`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val recalculateChain = RecalculateBalanceUpdateChainUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val accountId = accountRepository.createAccount(
            Account(
                name = "活期",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 2_000,
                purpose = "工资",
                occurredAt = 2_000,
                createdAt = 2_000,
                updatedAt = 2_000,
            ),
        )

        updateBalanceUseCase(accountId = accountId, actualBalance = 11_000, occurredAt = 3_000)
        val recordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing record")

        deleteBalanceUpdateRecordUseCase(recordId)
        deleteBalanceUpdateRecordUseCase(recordId)

        assertNull(transactionRepository.getBalanceUpdateRecordById(recordId))
        assertEquals(emptyList(), transactionRepository.queryAllBalanceUpdateRecords())
        assertNull(accountRepository.getAccountById(accountId)?.lastBalanceUpdateAt)
        assertEquals(12_000, calculateCurrentBalanceUseCase(accountId))
    }

    @Test
    fun `deleting balance update also removes legacy linked adjustment`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val recalculateChain = RecalculateBalanceUpdateChainUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val calculateCurrentBalanceUseCase = CalculateCurrentBalanceUseCase(accountRepository, transactionRepository)
        val accountId = accountRepository.createAccount(
            Account(
                name = "银河",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )

        updateBalanceUseCase(accountId = accountId, actualBalance = 9_000, occurredAt = 3_000)
        val recordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing record")
        transactionRepository.insertBalanceAdjustmentRecord(
            BalanceAdjustmentRecord(
                accountId = accountId,
                delta = -1_000,
                sourceUpdateRecordId = recordId,
                occurredAt = 3_000,
                createdAt = 3_000,
            ),
        )

        deleteBalanceUpdateRecordUseCase(recordId)

        assertEquals(emptyList(), transactionRepository.queryAllBalanceAdjustmentRecords())
        assertEquals(10_000, calculateCurrentBalanceUseCase(accountId))
    }

    @Test
    fun `inserting earlier balance update recalculates later snapshots`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val recalculateChain = RecalculateBalanceUpdateChainUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val accountId = accountRepository.createAccount(
            Account(
                name = "主账户",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 1_000,
                purpose = "补贴",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
            ),
        )

        updateBalanceUseCase(accountId = accountId, actualBalance = 13_000, occurredAt = 5_000)
        val laterRecordId = transactionRepository.getLatestBalanceUpdate(accountId)?.id ?: error("missing later record")

        updateBalanceUseCase(accountId = accountId, actualBalance = 9_000, occurredAt = 3_000)

        val laterRecord = transactionRepository.getBalanceUpdateRecordById(laterRecordId) ?: error("missing recalculated record")
        assertEquals(10_000, laterRecord.systemBalanceBeforeUpdate)
        assertEquals(3_000, laterRecord.delta)
    }

    @Test
    fun `deleting earlier balance update recalculates surviving snapshots`() = runBlocking {
        val accountRepository = InMemoryAccountRepository()
        val transactionRepository = InMemoryTransactionRepository()
        val resolveContext = ResolveBalanceUpdateContextUseCase(accountRepository, transactionRepository)
        val recalculateChain = RecalculateBalanceUpdateChainUseCase(accountRepository, transactionRepository)
        val refreshActivity = RefreshAccountActivityStateUseCase(accountRepository, transactionRepository)
        val updateBalanceUseCase = UpdateBalanceUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            resolveBalanceUpdateContextUseCase = resolveContext,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val deleteBalanceUpdateRecordUseCase = DeleteBalanceUpdateRecordUseCase(
            accountRepository = accountRepository,
            transactionRepository = transactionRepository,
            recalculateBalanceUpdateChainUseCase = recalculateChain,
            refreshAccountActivityStateUseCase = refreshActivity,
        )
        val accountId = accountRepository.createAccount(
            Account(
                name = "副账户",
                initialBalance = 10_000,
                createdAt = 1_000,
            ),
        )
        val earlierRecordId = updateBalanceUseCase(
            accountId = accountId,
            actualBalance = 9_000,
            occurredAt = 3_000,
        ).let {
            transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
                .first { record -> record.occurredAt == 3_000L }
                .id
        }
        transactionRepository.insertCashFlowRecord(
            CashFlowRecord(
                accountId = accountId,
                direction = "inflow",
                amount = 1_000,
                purpose = "返现",
                occurredAt = 4_000,
                createdAt = 4_000,
                updatedAt = 4_000,
            ),
        )
        updateBalanceUseCase(accountId = accountId, actualBalance = 13_000, occurredAt = 5_000)
        val laterRecordId = transactionRepository.queryBalanceUpdateRecordsByAccountId(accountId)
            .first { record -> record.occurredAt == 5_000L }
            .id

        deleteBalanceUpdateRecordUseCase(earlierRecordId)

        val laterRecord = transactionRepository.getBalanceUpdateRecordById(laterRecordId) ?: error("missing surviving record")
        assertEquals(11_000, laterRecord.systemBalanceBeforeUpdate)
        assertEquals(2_000, laterRecord.delta)
    }
}
