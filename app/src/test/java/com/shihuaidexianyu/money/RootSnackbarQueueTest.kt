package com.shihuaidexianyu.money

import androidx.lifecycle.SavedStateHandle
import com.shihuaidexianyu.money.domain.model.LedgerRecordKind
import com.shihuaidexianyu.money.domain.model.LedgerUndoToken
import com.shihuaidexianyu.money.domain.model.ReminderSkipUndoToken
import com.shihuaidexianyu.money.ui.common.RootSnackbarAction
import com.shihuaidexianyu.money.ui.common.RootSnackbarEffect
import com.shihuaidexianyu.money.ui.common.RootSnackbarQueueViewModel
import com.shihuaidexianyu.money.ui.common.executeRootSnackbarAction
import com.shihuaidexianyu.money.ui.common.RootActionExecutionResult
import com.shihuaidexianyu.money.ui.common.rootSnackbarEffect
import com.shihuaidexianyu.money.domain.model.RestoreLedgerResult
import com.shihuaidexianyu.money.domain.model.UndoReminderSkipResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test
import kotlinx.coroutines.test.runTest

class RootSnackbarQueueTest {
    @Test
    fun `queue is fifo conditional ack and survives recreation`() {
        val handle = SavedStateHandle()
        val first = RootSnackbarQueueViewModel(handle)
        val firstToken = first.enqueue("第一条")
        val secondToken = first.enqueue("第二条")

        assertEquals(firstToken, first.current?.token)
        assertFalse(first.ack(secondToken))
        val recreated = RootSnackbarQueueViewModel(handle)
        assertEquals(listOf(firstToken, secondToken), recreated.queue.value.map { it.token })
        assertTrue(recreated.ack(firstToken))
        assertEquals(secondToken, recreated.current?.token)
        assertTrue(recreated.ack(secondToken))
        assertEquals(null, RootSnackbarQueueViewModel(handle).current)
    }

    @Test
    fun `duplicate token is enqueued once`() {
        val queue = RootSnackbarQueueViewModel(SavedStateHandle())
        val effect = RootSnackbarEffect("stable", "已删除", "撤销")
        queue.enqueue(effect)
        queue.enqueue(effect)
        assertEquals(1, queue.queue.value.size)
    }

    @Test
    fun `action follow up is appended only after current is acknowledged`() {
        val queue = RootSnackbarQueueViewModel(SavedStateHandle())
        val current = queue.enqueue("撤销中")
        val alreadyQueued = queue.enqueue("已有消息")
        assertTrue(queue.ack(current))
        val failure = queue.enqueue("操作失败")
        assertEquals(listOf(alreadyQueued, failure), queue.queue.value.map { it.token })
    }

    @Test
    fun `ledger and reminder undo payloads round trip through java serialization`() {
        val ledger = RootSnackbarAction.RestoreLedger(
            LedgerUndoToken(1, LedgerRecordKind.CASH_FLOW, 7, "op", 99),
        )
        val reminder = RootSnackbarAction.UndoReminderSkip(
            ReminderSkipUndoToken(2, 3, 4, 5),
        )
        assertEquals(ledger, javaRoundTrip(ledger))
        assertEquals(reminder, javaRoundTrip(reminder))
    }

    @Test
    fun `idempotent undo outcomes are success and exceptions become retry message`() = runTest {
        val ledger = RootSnackbarAction.RestoreLedger(
            LedgerUndoToken(1, LedgerRecordKind.CASH_FLOW, 7, "op", 99),
        )
        assertEquals(
            RootActionExecutionResult.Success,
            executeRootSnackbarAction(
                ledger,
                restoreLedger = { RestoreLedgerResult.ALREADY_ACTIVE },
                undoReminderSkip = { UndoReminderSkipResult.ALREADY_RESTORED },
                createAccount = {},
                manageAccounts = {},
            ),
        )
        assertEquals(
            RootActionExecutionResult.RetryableFailure("操作失败，请稍后重试"),
            executeRootSnackbarAction(
                ledger,
                restoreLedger = { error("disk") },
                undoReminderSkip = { UndoReminderSkipResult.RESTORED },
                createAccount = {},
                manageAccounts = {},
            ),
        )
    }

    @Test
    fun `retryable failure atomically replaces head and keeps action after recreation`() {
        val handle = SavedStateHandle()
        val queue = RootSnackbarQueueViewModel(handle)
        val action = RootSnackbarAction.RestoreLedger(
            LedgerUndoToken(1, LedgerRecordKind.CASH_FLOW, 7, "op", 99),
        )
        val token = queue.enqueue("已删除", "撤销", action)
        val retry = rootSnackbarEffect("操作失败，请稍后重试", "重试", action)
        assertTrue(queue.replaceHead(token, retry))

        val recreated = RootSnackbarQueueViewModel(handle)
        assertEquals(retry, recreated.current)
        assertEquals(action, recreated.current?.action)
        assertFalse(recreated.ack(token))
        assertTrue(recreated.ack(retry.token))
    }

    private fun javaRoundTrip(value: RootSnackbarAction): RootSnackbarAction {
        val bytes = ByteArrayOutputStream().use { output ->
            ObjectOutputStream(output).use { it.writeObject(value) }
            output.toByteArray()
        }
        return ObjectInputStream(ByteArrayInputStream(bytes)).use {
            it.readObject() as RootSnackbarAction
        }
    }
}
