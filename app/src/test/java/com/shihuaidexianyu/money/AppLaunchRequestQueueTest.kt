package com.shihuaidexianyu.money

import com.shihuaidexianyu.money.data.migration.StartupMigrationState
import com.shihuaidexianyu.money.domain.launch.AppLaunchDestination
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequest
import com.shihuaidexianyu.money.domain.launch.AppLaunchRequestQueue
import com.shihuaidexianyu.money.ui.launch.pendingRequestForRouting
import com.shihuaidexianyu.money.ui.lock.AppLockState
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppLaunchRequestQueueTest {
    @Test
    fun `initial and multiple new intents route once in fifo token order`() {
        val queue = AppLaunchRequestQueue()
        val requests = listOf(
            AppLaunchRequest("initial", AppLaunchDestination.Home),
            AppLaunchRequest("new-1", AppLaunchDestination.BatchReconcile),
            AppLaunchRequest("new-2", AppLaunchDestination.SharePreview("支出 ¥1,234.56")),
        )
        requests.forEach(queue::offer)
        queue.offer(requests.first())

        assertEquals("initial", queue.pending.value.first().token)
        queue.acknowledge("wrong")
        assertEquals("initial", queue.pending.value.first().token)
        requests.forEach { request ->
            assertEquals(request.token, queue.pending.value.first().token)
            queue.acknowledge(request.token)
        }
        assertEquals(emptyList(), queue.pending.value)

        queue.offer(requests.last())
        assertEquals(emptyList(), queue.pending.value, "acknowledged token must not route again")
    }

    @Test
    fun `request is not routable before startup ready and app unlocked`() {
        val request = AppLaunchRequest("locked", AppLaunchDestination.Home)

        assertNull(
            pendingRequestForRouting(
                StartupMigrationState.Loading,
                AppLockState.Unlocked,
                request,
            ),
        )
        assertNull(
            pendingRequestForRouting(
                StartupMigrationState.Ready,
                AppLockState.Locked,
                request,
            ),
        )
        assertEquals(
            request,
            pendingRequestForRouting(
                StartupMigrationState.Ready,
                AppLockState.Unlocked,
                request,
            ),
        )
    }

    @Test
    fun `pending state remains bounded and consecutive notifications keep fifo order`() {
        val queue = AppLaunchRequestQueue()
        repeat(120) { index ->
            queue.offer(
                AppLaunchRequest(
                    "notification-$index",
                    AppLaunchDestination.BalanceNotification(index + 1L),
                ),
            )
        }

        assertEquals(8, queue.pending.value.size)
        assertEquals((112 until 120).map { "notification-$it" }, queue.pending.value.map { it.token })
        queue.pending.value.map { it.token }.forEach(queue::acknowledge)
        assertEquals(emptyList(), queue.pending.value)
    }
}
