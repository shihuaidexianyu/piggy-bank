package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemoryAccountRepository
import com.shihuaidexianyu.money.data.repository.InMemoryTransactionRepository
import com.shihuaidexianyu.money.domain.model.Account
import com.shihuaidexianyu.money.domain.model.AccountKind
import com.shihuaidexianyu.money.domain.repository.DatabaseTransactionRunner
import com.shihuaidexianyu.money.domain.time.ClockProvider
import com.shihuaidexianyu.money.domain.usecase.CalculateAccountBalancesUseCase
import com.shihuaidexianyu.money.domain.usecase.UpdateAccountDisplayOrderUseCase
import com.shihuaidexianyu.money.ui.accounts.AccountOrderGroup
import com.shihuaidexianyu.money.ui.accounts.ReorderAccountsViewModel
import com.shihuaidexianyu.money.ui.accounts.orderGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ReorderAccountsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = InMemoryAccountRepository()
    private var writes = 0
    private var rejectSave = false
    private var saveGate: CompletableDeferred<Unit>? = null
    private val runner = object : DatabaseTransactionRunner {
        override suspend fun <T> runInTransaction(block: suspend () -> T): T {
            writes++
            saveGate?.await()
            check(!rejectSave) { "save failed" }
            return block()
        }
    }

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun teardown() = Dispatchers.resetMain()

    private suspend fun createViewModel(): ReorderAccountsViewModel {
        listOf(
            Account(name = "C", initialBalance = 100L, createdAt = 1L),
            Account(name = "投资B", kind = AccountKind.INVESTMENT, initialBalance = 200L, createdAt = 1L),
            Account(name = "隐藏B", isHidden = true, initialBalance = 300L, createdAt = 1L),
            Account(name = "A", initialBalance = 400L, createdAt = 1L),
            Account(name = "B", initialBalance = 500L, createdAt = 1L),
            Account(name = "隐藏A", isHidden = true, kind = AccountKind.INVESTMENT, initialBalance = 600L, createdAt = 1L),
            Account(name = "投资A", kind = AccountKind.INVESTMENT, initialBalance = 700L, createdAt = 1L),
            Account(name = "已关闭", initialBalance = 0L, createdAt = 1L, closedAt = 10L),
        ).forEachIndexed { index, account ->
            repository.createAccount(account.copy(createdAt = 1L, displayOrder = index, lastUsedAt = index.toLong()))
        }
        return ReorderAccountsViewModel(
            repository,
            CalculateAccountBalancesUseCase(InMemoryTransactionRepository(), object : ClockProvider {
                override fun nowMillis() = 100L
            }),
            UpdateAccountDisplayOrderUseCase(repository, runner),
        )
    }

    @Test fun `drag moves within a group without moving other groups or changing account state`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        assertEquals((1L..7L).toList(), vm.uiState.value.accounts.map { it.id })
        vm.moveAccount(1L, 5L)
        assertEquals(listOf(4L, 2L, 3L, 5L, 1L, 6L, 7L), vm.uiState.value.accounts.map { it.id })
        assertTrue(vm.uiState.value.isDirty)
        vm.moveAccount(1L, 2L)
        vm.moveAccount(1L, 3L)
        vm.moveAccount(1L, 999L)
        assertEquals(listOf(4L, 2L, 3L, 5L, 1L, 6L, 7L), vm.uiState.value.accounts.map { it.id })
        vm.save()
        advanceUntilIdle()
        assertEquals(listOf(4L, 2L, 3L, 5L, 1L, 6L, 7L), repository.queryOpenAccounts().sortedBy { it.displayOrder }.map { it.id })
        assertEquals(100L, repository.getAccountById(1L)?.initialBalance)
        assertEquals(AccountKind.INVESTMENT, repository.getAccountById(2L)?.kind)
        assertTrue(repository.getAccountById(3L)!!.isHidden)
        assertEquals(10L, repository.getAccountById(8L)?.closedAt)
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test fun `accessible moves skip other groups and stop at group boundaries`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.moveAccountUp(1L)
        vm.moveAccountDown(7L)
        assertFalse(vm.uiState.value.isDirty)
        vm.moveAccountDown(1L)
        assertEquals(listOf(4L, 2L, 3L, 1L, 5L, 6L, 7L), vm.uiState.value.accounts.map { it.id })
        vm.moveAccountUp(6L)
        assertEquals(listOf(4L, 2L, 6L, 1L, 5L, 3L, 7L), vm.uiState.value.accounts.map { it.id })
    }

    @Test fun `quick sorting acts once within each group and undo restores entry order`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.sortByBalance()
        assertEquals(listOf(5L, 7L, 6L, 4L, 1L, 3L, 2L), vm.uiState.value.accounts.map { it.id })
        vm.moveAccount(1L, 5L)
        assertEquals(listOf(1L, 5L, 4L), vm.uiState.value.accounts.filter { it.orderGroup == AccountOrderGroup.FUNDING }.map { it.id })
        vm.sortByName()
        assertEquals(listOf(4L, 5L, 1L), vm.uiState.value.accounts.filter { it.orderGroup == AccountOrderGroup.FUNDING }.map { it.id })
        vm.sortByRecentUse()
        assertEquals(listOf(5L, 7L, 6L, 4L, 1L, 3L, 2L), vm.uiState.value.accounts.map { it.id })
        vm.undoChanges()
        assertEquals((1L..7L).toList(), vm.uiState.value.accounts.map { it.id })
        assertFalse(vm.uiState.value.isDirty)
        vm.save()
        advanceUntilIdle()
        assertEquals(0, writes)
    }

    @Test fun `saving freezes the draft and ignores repeated save requests`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.moveAccount(1L, 5L)
        val draft = vm.uiState.value.accounts
        saveGate = CompletableDeferred()
        vm.save()
        vm.save()
        vm.sortByName()
        vm.moveAccountUp(1L)
        vm.undoChanges()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSaving)
        assertEquals(draft, vm.uiState.value.accounts)
        assertEquals(1, writes)
        saveGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaving)
        assertFalse(vm.uiState.value.isDirty)
    }

    @Test fun `failed save retains the draft for retry`() = runTest(dispatcher) {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.moveAccount(1L, 5L)
        val draft = vm.uiState.value.accounts
        rejectSave = true
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSaving)
        assertTrue(vm.uiState.value.isDirty)
        assertEquals(draft, vm.uiState.value.accounts)
        rejectSave = false
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isDirty)
        assertEquals(2, writes)
    }
}
