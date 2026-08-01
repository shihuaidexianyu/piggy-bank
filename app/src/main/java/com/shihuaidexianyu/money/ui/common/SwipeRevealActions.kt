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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class SwipeRevealValue {
    SETTLED,
    START_REVEALED,
    END_REVEALED,
    START_FULL,
    END_FULL,
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
 * only on a tap of that button, or when the row is flung/pulled past ~half its width (full
 * swipe). Short drags and scroll drift snap straight back. The row stays interactive and is
 * returned to the settled position after any action.
 */
@Composable
fun SwipeRevealActionsBox(
    endAction: SwipeRevealAction? = null,
    startAction: SwipeRevealAction? = null,
    onEndAction: () -> Unit = {},
    onStartAction: () -> Unit = {},
    contentClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable (contentClick: () -> Unit) -> Unit,
) {
    val density = LocalDensity.current
    val revealWidthPx = with(density) { REVEAL_WIDTH_DP.dp.toPx() }
    val scope = rememberCoroutineScope()
    var widthPx by remember { mutableFloatStateOf(0f) }
    val state = remember {
        AnchoredDraggableState(
            initialValue = SwipeRevealValue.SETTLED,
            anchors = DraggableAnchors<SwipeRevealValue> { SwipeRevealValue.SETTLED at 0f },
            positionalThreshold = { distance: Float -> distance * 0.5f },
            // Distance decides, not flick speed: only a genuinely violent fling may skip the
            // revealed state; normal swipes snap to the revealed button instead of triggering.
            velocityThreshold = { with(density) { 1500.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            decayAnimationSpec = exponentialDecay(),
        )
    }

    LaunchedEffect(state, widthPx, startAction, endAction) {
        state.updateAnchors(
            DraggableAnchors<SwipeRevealValue> {
                SwipeRevealValue.SETTLED at 0f
                if (startAction != null) {
                    SwipeRevealValue.START_REVEALED at revealWidthPx
                    SwipeRevealValue.START_FULL at widthPx
                }
                if (endAction != null) {
                    SwipeRevealValue.END_REVEALED at -revealWidthPx
                    SwipeRevealValue.END_FULL at -widthPx
                }
            },
            state.targetValue,
        )
    }

    // A full swipe settles on the FULL anchor: run the action once, then spring the row back.
    LaunchedEffect(state.targetValue) {
        when (state.targetValue) {
            SwipeRevealValue.END_FULL -> {
                onEndAction()
                state.animateTo(SwipeRevealValue.SETTLED)
            }
            SwipeRevealValue.START_FULL -> {
                onStartAction()
                state.animateTo(SwipeRevealValue.SETTLED)
            }
            else -> Unit
        }
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
            .onSizeChanged { widthPx = it.width.toFloat() },
    ) {
        if (endAction != null) {
            SwipeRevealActionButton(
                action = endAction,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(REVEAL_WIDTH_DP.dp)
                    .clickable {
                        onEndAction()
                        scope.launch { state.animateTo(SwipeRevealValue.SETTLED) }
                    },
            )
        }
        if (startAction != null) {
            SwipeRevealActionButton(
                action = startAction,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(REVEAL_WIDTH_DP.dp)
                    .clickable {
                        onStartAction()
                        scope.launch { state.animateTo(SwipeRevealValue.SETTLED) }
                    },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(state.requireOffset().roundToInt(), 0) }
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
private fun SwipeRevealActionButton(
    action: SwipeRevealAction,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.background(action.containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
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
