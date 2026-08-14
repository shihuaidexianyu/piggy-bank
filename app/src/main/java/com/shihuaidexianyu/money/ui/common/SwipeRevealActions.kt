package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
 * iOS-style swipe reveal: dragging reveals an action button at the row edge; the action fires
 * only on an explicit tap of that button. A long or fast swipe can reveal the action but never
 * dispatch it. Short drags and scroll drift snap straight back. The row stays interactive and is
 * returned to the settled position after a button tap.
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
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 1500.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            decayAnimationSpec = exponentialDecay(),
        )
    }

    val rowClick: () -> Unit = {
        if (state.currentValue == SwipeRevealValue.SETTLED) {
            contentClick()
        } else {
            scope.launch { state.animateTo(SwipeRevealValue.SETTLED) }
        }
    }

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
                onClick = {
                    onEndAction()
                    scope.launch { state.animateTo(SwipeRevealValue.SETTLED) }
                },
                modifier = Modifier.matchParentSize(),
            )
            state.requireOffset() > 0f && startAction != null -> SwipeRevealActionBacking(
                action = startAction,
                alignment = Alignment.CenterStart,
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
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    enabled = startAction != null || endAction != null,
                ),
        ) {
            content(rowClick)
        }
    }
}

@Composable
private fun SwipeRevealActionBacking(
    action: SwipeRevealAction,
    alignment: Alignment,
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
                modifier = Modifier.padding(horizontal = 16.dp),
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
