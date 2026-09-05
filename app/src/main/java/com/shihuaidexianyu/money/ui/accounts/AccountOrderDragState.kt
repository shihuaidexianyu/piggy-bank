package com.shihuaidexianyu.money.ui.accounts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlin.math.abs

@Composable
internal fun rememberAccountOrderDragState(
    listState: LazyListState,
    accounts: List<ReorderAccountItemUiModel>,
    onMove: (Long, Long) -> Unit,
    onMoveFeedback: () -> Unit,
): AccountOrderDragState {
    val latestAccounts = rememberUpdatedState(accounts)
    val latestMove = rememberUpdatedState(onMove)
    val latestFeedback = rememberUpdatedState(onMoveFeedback)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val edgeSize = with(density) { 56.dp.toPx() }
    val speed = with(density) { 800.dp.toPx() }
    val dragState = remember(listState, scope, edgeSize, speed) {
        AccountOrderDragState(
            listState, scope, edgeSize, speed,
            accounts = { latestAccounts.value },
            onMove = { from, to -> latestMove.value(from, to) },
            onMoveFeedback = { latestFeedback.value() },
        )
    }
    LaunchedEffect(dragState, dragState.draggedId) {
        var previousFrame = withFrameNanos { it }
        while (dragState.draggedId != null && !dragState.isFinishing) {
            val frame = withFrameNanos { it }
            val seconds = ((frame - previousFrame) / 1_000_000_000f).coerceAtMost(0.032f)
            previousFrame = frame
            dragState.movePastNeighbors()
            val scroll = dragState.edgeScrollVelocity() * seconds
            if (scroll != 0f) listState.scrollBy(scroll)
        }
    }
    return dragState
}

/** Geometry stays in viewport coordinates, so a held row follows the finger during scrolling. */
@Stable
internal class AccountOrderDragState(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val edgeSize: Float,
    private val maxScrollSpeed: Float,
    private val accounts: () -> List<ReorderAccountItemUiModel>,
    private val onMove: (Long, Long) -> Unit,
    private val onMoveFeedback: () -> Unit,
) {
    var draggedId: Long? by mutableStateOf(null)
        private set
    var settlingId: Long? by mutableStateOf(null)
        private set
    var isFinishing: Boolean by mutableStateOf(false)
        private set
    private var desiredTop by mutableFloatStateOf(0f)
    private val settlingOffset = Animatable(0f)
    private var finishJob: Job? = null
    private var pendingIndex: Int? = null

    private fun item(id: Long): LazyListItemInfo? =
        listState.layoutInfo.visibleItemsInfo.find { it.key == id }

    private fun groupIds(id: Long): List<Long> {
        val items = accounts()
        val group = items.find { it.id == id }?.orderGroup ?: return emptyList()
        return items.filter { it.orderGroup == group }.map { it.id }
    }

    private fun clampedTop(id: Long, info: LazyListItemInfo): Float {
        val ids = groupIds(id)
        val layout = listState.layoutInfo
        val lower = ids.firstOrNull()?.let(::item)?.offset?.toFloat()
            ?: layout.viewportStartOffset.toFloat()
        val upper = ids.lastOrNull()?.let(::item)?.let { it.offset + it.size - info.size }?.toFloat()
            ?: (layout.viewportEndOffset - info.size).toFloat()
        return desiredTop.coerceIn(lower, maxOf(lower, upper))
    }

    // Read from graphicsLayer, not composition: dragging must not recompose every row per pixel.
    fun translation(id: Long): Float = when (id) {
        draggedId -> item(id)?.let { clampedTop(id, it) - it.offset } ?: 0f
        settlingId -> settlingOffset.value
        else -> 0f
    }

    fun start(id: Long) {
        if (groupIds(id).size < 2) return
        val info = item(id) ?: return
        finishJob?.cancel()
        settlingId = null
        pendingIndex = null
        isFinishing = false
        desiredTop = info.offset.toFloat()
        draggedId = id
    }

    fun drag(delta: Float) {
        if (draggedId == null || isFinishing) return
        desiredTop += delta
        movePastNeighbors()
    }

    fun movePastNeighbors(finishing: Boolean = false) {
        val id = draggedId ?: return
        if (isFinishing && !finishing) return
        val info = item(id) ?: return
        pendingIndex?.let { if (info.index != it) return }
        pendingIndex = null
        // Use the finger's intended position for crossing. A visual clamp alone cannot cross
        // the final row's midpoint when that row is shorter (for example, no last divider).
        val center = desiredTop + info.size / 2f
        val ownCenter = info.offset + info.size / 2f
        val ids = groupIds(id).toSet()
        val target = listState.layoutInfo.visibleItemsInfo
            .filter { candidate ->
                candidate.key != id && candidate.key in ids &&
                    if (center > ownCenter) {
                        candidate.index > info.index && center > candidate.offset + candidate.size / 2f
                    } else {
                        candidate.index < info.index && center < candidate.offset + candidate.size / 2f
                    }
            }
            .minByOrNull { abs(center - (it.offset + it.size / 2f)) } ?: return
        // Key-based anchoring would otherwise scroll the list when its first visible row moves.
        listState.requestScrollToItem(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        pendingIndex = target.index
        onMove(id, target.key as Long)
        onMoveFeedback()
    }

    fun edgeScrollVelocity(): Float {
        val id = draggedId ?: return 0f
        val info = item(id) ?: return 0f
        val ids = groupIds(id)
        val position = ids.indexOf(id)
        val layout = listState.layoutInfo
        val top = clampedTop(id, info)
        return when {
            position > 0 && top < layout.viewportStartOffset + edgeSize ->
                -maxScrollSpeed * ((layout.viewportStartOffset + edgeSize - top) / edgeSize).coerceIn(0f, 1f)
            position < ids.lastIndex && top + info.size > layout.viewportEndOffset - edgeSize ->
                maxScrollSpeed * ((top + info.size - layout.viewportEndOffset + edgeSize) / edgeSize).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    fun finish() {
        val id = draggedId ?: return
        if (isFinishing) return
        isFinishing = true
        finishJob = scope.launch {
            // A quick release can arrive before the preceding move has been measured. Commit
            // the final finger position after that layout, then settle against the new slot.
            awaitPendingMove(id)
            movePastNeighbors(finishing = true)
            awaitPendingMove(id)
            settlingOffset.snapTo(translation(id))
            settlingId = id
            draggedId = null
            pendingIndex = null
            settlingOffset.animateTo(0f, tween(160))
            settlingId = null
            isFinishing = false
        }
    }

    private suspend fun awaitPendingMove(id: Long) {
        val expectedIndex = pendingIndex ?: return
        snapshotFlow { item(id)?.index }.first { it == expectedIndex }
    }
}
