package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

enum class SwipeRevealValue {
    SETTLED,
    START_REVEALED,
    END_REVEALED,
}

data class SwipeRevealAction(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val iconTint: Color? = null,
)

private const val REVEAL_WIDTH_DP = 104

/**
 * A mostly-vertical flick must never reveal the actions: the horizontal component has to
 * dominate by this factor before the gesture is claimed (~27° from the horizontal axis).
 */
private const val HORIZONTAL_DOMINANCE = 2f

/** Fling speed that settles into the revealed state even without a 50% drag. */
private val REVEAL_VELOCITY_THRESHOLD = 125.dp

/**
 * iOS-style swipe reveal: dragging reveals an action button at the row edge; the action fires
 * only on an explicit tap of that button. A long or fast swipe can reveal the action but never
 * dispatch it. Short drags and scroll drift snap straight back. The row stays interactive and is
 * returned to the settled position after a button tap.
 *
 * Gesture entry goes through a custom direction gate instead of `anchoredDraggable`: the row
 * claims the gesture only once the horizontal component crosses touch slop AND dominates the
 * vertical one by [HORIZONTAL_DOMINANCE]. Diagonal scrolls through the list therefore stay with
 * the LazyColumn and no longer flash the action buttons mid-scroll.
 */
@Composable
fun SwipeRevealActionsBox(
    endAction: SwipeRevealAction? = null,
    startAction: SwipeRevealAction? = null,
    onEndAction: () -> Unit = {},
    onStartAction: () -> Unit = {},
    contentClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    content: @Composable (contentClick: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { REVEAL_WIDTH_DP.dp.toPx() }
    val scope = rememberCoroutineScope()
    val hasStartAction = startAction != null
    val hasEndAction = endAction != null
    val anchors = remember(revealWidthPx, hasStartAction, hasEndAction) {
        DraggableAnchors {
            SwipeRevealValue.SETTLED at 0f
            if (hasStartAction) SwipeRevealValue.START_REVEALED at revealWidthPx
            if (hasEndAction) SwipeRevealValue.END_REVEALED at -revealWidthPx
        }
    }
    val state = remember(anchors) {
        AnchoredDraggableState(
            initialValue = SwipeRevealValue.SETTLED,
            anchors = anchors,
        )
    }

    val rowClick: () -> Unit = {
        if (state.currentValue == SwipeRevealValue.SETTLED) {
            contentClick()
        } else {
            scope.launch { state.animateTo(SwipeRevealValue.SETTLED) }
        }
    }

    // 0 → 1 as the user drags the lid aside; drives the backing button's fade/scale.
    val revealFraction = (abs(state.requireOffset()) / revealWidthPx).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape),
    ) {
        // Full-width backing in the direction of the drag: the exposed strip is always the action
        // color with its label pinned to the revealed edge, exactly like a native swipe row.
        when {
            state.requireOffset() < 0f && endAction != null -> SwipeRevealActionBacking(
                action = endAction,
                alignment = Alignment.CenterEnd,
                revealFraction = revealFraction,
                onClick = {
                    onEndAction()
                    scope.launch { state.animateTo(SwipeRevealValue.SETTLED) }
                },
                modifier = Modifier.matchParentSize(),
            )
            state.requireOffset() > 0f && startAction != null -> SwipeRevealActionBacking(
                action = startAction,
                alignment = Alignment.CenterStart,
                revealFraction = revealFraction,
                onClick = {
                    onStartAction()
                    scope.launch { state.animateTo(SwipeRevealValue.SETTLED) }
                },
                modifier = Modifier.matchParentSize(),
            )
            else -> Unit
        }
        // The row itself is an opaque lid that slides over the backing; nothing shows through.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .directionGatedSwipeReveal(
                    state = state,
                    revealWidthPx = revealWidthPx,
                    hasStartAction = hasStartAction,
                    hasEndAction = hasEndAction,
                    scope = scope,
                ),
        ) {
            content(rowClick)
        }
    }
}

/**
 * Drives [state] from raw pointer events with a direction gate. Nothing is consumed while the
 * gesture is ambiguous: if the vertical component crosses touch slop first (or the horizontal
 * drag has no action to reveal), the gesture is left for the surrounding scrollable untouched.
 * Only a clearly horizontal drag is claimed and forwarded via [AnchoredDraggableState.dispatchRawDelta];
 * release settles to the nearest anchor by 50% displacement or a [REVEAL_VELOCITY_THRESHOLD] fling.
 */
private fun Modifier.directionGatedSwipeReveal(
    state: AnchoredDraggableState<SwipeRevealValue>,
    revealWidthPx: Float,
    hasStartAction: Boolean,
    hasEndAction: Boolean,
    scope: CoroutineScope,
): Modifier {
    if (!hasStartAction && !hasEndAction) return this
    return pointerInput(hasStartAction, hasEndAction) {
        val velocityThresholdPx = REVEAL_VELOCITY_THRESHOLD.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            var totalDx = 0f
            var totalDy = 0f
            var claimed = false
            // Direction gate: watch the initial movement without consuming anything. Claim only
            // a clearly horizontal drag toward a side that actually has an action; otherwise the
            // LazyColumn keeps the scroll.
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.positionChangeIgnoreConsumed()
                totalDx += delta.x
                totalDy += delta.y
                val absDx = abs(totalDx)
                val absDy = abs(totalDy)
                if (absDy > viewConfiguration.touchSlop && absDy >= absDx) break
                if (absDx > viewConfiguration.touchSlop && absDx > absDy * HORIZONTAL_DOMINANCE) {
                    claimed = (totalDx > 0f && hasStartAction) || (totalDx < 0f && hasEndAction)
                    break
                }
            }
            if (!claimed) return@awaitEachGesture

            // Over-slop only: the row starts moving from where the finger is, no visual jump.
            state.dispatchRawDelta(totalDx - totalDx.sign * viewConfiguration.touchSlop)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                state.dispatchRawDelta(change.positionChange().x)
                change.consume()
            }

            val velocity = velocityTracker.calculateVelocity().x
            val offset = state.requireOffset()
            val target = when {
                velocity <= -velocityThresholdPx && hasEndAction -> SwipeRevealValue.END_REVEALED
                velocity >= velocityThresholdPx && hasStartAction -> SwipeRevealValue.START_REVEALED
                offset <= -revealWidthPx / 2f && hasEndAction -> SwipeRevealValue.END_REVEALED
                offset >= revealWidthPx / 2f && hasStartAction -> SwipeRevealValue.START_REVEALED
                else -> SwipeRevealValue.SETTLED
            }
            // awaitEachGesture is a restricted suspension scope, so the settle animation has to
            // leave it: launch on the composable's scope instead of calling animateTo inline.
            // The fling velocity already decided the target above; the spring settle itself
            // doesn't need it.
            scope.launch { state.animateTo(target) }
        }
    }
}

@Composable
private fun SwipeRevealActionBacking(
    action: SwipeRevealAction,
    alignment: Alignment,
    revealFraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(action.containerColor),
        contentAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(REVEAL_WIDTH_DP.dp)
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    contentDescription = action.label
                    role = Role.Button
                    this.onClick { onClick(); true }
                },
            contentAlignment = alignment,
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .graphicsLayer {
                        alpha = revealFraction
                        val scale = 0.85f + 0.15f * revealFraction
                        scaleX = scale
                        scaleY = scale
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = action.icon,
                    contentDescription = null,
                    tint = action.iconTint ?: action.contentColor,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = action.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = action.contentColor,
                    maxLines = 1,
                )
            }
        }
    }
}
