package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.repository.InMemorySavingsGoalRepository
import com.shihuaidexianyu.money.domain.model.SAVINGS_GOAL_ID
import com.shihuaidexianyu.money.domain.time.MutationTimestampOverflowException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SavingsGoalRepositoryContractTest {
    @Test
    fun `singleton upsert preserves creation and monotonic update timestamps`() = runBlocking {
        val repository = InMemorySavingsGoalRepository()
        assertNull(repository.query())
        assertNull(repository.observe().first())

        repository.upsert(targetAmount = 10_000L, now = 100L)
        val created = assertNotNull(repository.query())
        assertEquals(SAVINGS_GOAL_ID, created.id)
        assertEquals(100L, created.createdAt)
        assertEquals(100L, created.updatedAt)

        repository.upsert(targetAmount = 10_000L, now = 50L)
        assertEquals(created, repository.query())

        repository.upsert(targetAmount = 20_000L, now = 50L)
        val changed = assertNotNull(repository.query())
        assertEquals(100L, changed.createdAt)
        assertEquals(101L, changed.updatedAt)
        assertEquals(20_000L, changed.targetAmount)
        Unit
    }

    @Test
    fun `singleton rejects invalid target and changed target at max timestamp`() = runBlocking {
        val repository = InMemorySavingsGoalRepository()
        assertFailsWith<IllegalArgumentException> { repository.upsert(0L, 1L) }
        assertFailsWith<IllegalArgumentException> { repository.upsert(-1L, 1L) }

        repository.upsert(1L, Long.MAX_VALUE)
        assertFailsWith<MutationTimestampOverflowException> {
            repository.upsert(2L, Long.MAX_VALUE)
        }
        Unit
    }

    @Test
    fun `concurrent first upserts serialize to one singleton and clear is idempotent`() = runBlocking {
        val repository = InMemorySavingsGoalRepository()
        coroutineScope {
            repeat(20) { index ->
                launch { repository.upsert(index + 1L, index + 1L) }
            }
        }

        val goal = assertNotNull(repository.query())
        assertEquals(SAVINGS_GOAL_ID, goal.id)
        assertTrue(goal.targetAmount in 1L..20L)
        repository.clear()
        repository.clear()
        assertNull(repository.query())
        Unit
    }
}
