package com.shihuaidexianyu.money.widget

import com.shihuaidexianyu.money.domain.model.AmountVisibility
import com.shihuaidexianyu.money.domain.model.PortableSettings

data class WidgetBalanceSnapshot(
    val totalAssets: Long,
    val monthInflow: Long,
    val monthOutflow: Long,
    val settings: PortableSettings,
    val visibility: AmountVisibility,
)

data class WidgetPrivacyEpoch(val generation: Long, val hidden: Boolean)

object WidgetPrivacyGeneration {
    private val lock = Any()
    private var state = WidgetPrivacyEpoch(generation = 0L, hidden = true)

    fun update(hidden: Boolean, afterUpdate: () -> Unit = {}): WidgetPrivacyEpoch =
        synchronized(lock) {
            val current = state
            val next = WidgetPrivacyEpoch(
                generation = if (current.generation == Long.MAX_VALUE) 1L else current.generation + 1L,
                hidden = hidden,
            )
            state = next
            afterUpdate()
            next
        }

    fun snapshot(): WidgetPrivacyEpoch = synchronized(lock) { state }

    fun renderAtomically(
        capturedGeneration: Long,
        snapshotIsMasked: Boolean,
        renderSnapshot: () -> Unit,
        renderSafe: () -> Unit,
    ) = synchronized(lock) {
        if (snapshotIsMasked || (!state.hidden && state.generation == capturedGeneration)) {
            renderSnapshot()
        } else {
            renderSafe()
        }
    }
}

fun interface WidgetSnapshotSource {
    suspend fun load(): WidgetBalanceSnapshot
}

fun interface WidgetSnapshotRenderer {
    fun render(widgetId: Int, snapshot: WidgetBalanceSnapshot)
}

class WidgetRefreshCoordinator(
    private val snapshotSource: WidgetSnapshotSource,
    private val renderer: WidgetSnapshotRenderer,
    private val safeRenderer: (Int) -> Unit = {},
    private val beforeRender: suspend (WidgetBalanceSnapshot) -> WidgetBalanceSnapshot = { it },
) {
    suspend fun refresh(widgetIds: IntArray) {
        if (widgetIds.isEmpty()) return
        widgetIds.forEach(safeRenderer)
        val snapshot = beforeRender(snapshotSource.load())
        widgetIds.forEach { renderer.render(it, snapshot) }
    }
}
