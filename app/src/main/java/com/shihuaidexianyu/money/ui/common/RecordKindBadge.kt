package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NorthEast
import androidx.compose.material.icons.rounded.SouthWest
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.ui.history.HistoryRecordKind
import com.shihuaidexianyu.money.ui.theme.LocalMoneyColors

@Composable
private fun recordKindAccent(kind: HistoryRecordKind, amount: Long): Color {
    val moneyColors = LocalMoneyColors.current
    return when (kind) {
        HistoryRecordKind.CASH_FLOW ->
            if (amount > 0) moneyColors.income else moneyColors.expense
        HistoryRecordKind.TRANSFER -> moneyColors.transfer
        HistoryRecordKind.BALANCE_UPDATE,
        HistoryRecordKind.BALANCE_ADJUSTMENT,
        -> moneyColors.current
    }
}

/**
 * Circular type badge for ledger record rows: a semantic-tinted disc (16% alpha) with a
 * semantic-colored icon — income points down-left (SouthWest arrow, money coming in), expense
 * points up-right (NorthEast arrow, money going out), transfer swaps, balance events check. The
 * diagonal arrows have no AutoMirrored variant in the icons library (they are not text-direction
 * sensitive); `Check` is direction-neutral. Shares its visual language with [AccountIconBadge].
 */
@Composable
fun RecordKindBadge(
    kind: HistoryRecordKind,
    amount: Long,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 17.dp,
) {
    val accent = recordKindAccent(kind, amount)
    val icon = when (kind) {
        HistoryRecordKind.CASH_FLOW ->
            if (amount > 0) Icons.Rounded.SouthWest else Icons.Rounded.NorthEast
        HistoryRecordKind.TRANSFER -> Icons.Rounded.SwapHoriz
        HistoryRecordKind.BALANCE_UPDATE,
        HistoryRecordKind.BALANCE_ADJUSTMENT,
        -> Icons.Rounded.Check
    }
    Surface(
        modifier = modifier.size(size),
        color = accent.copy(alpha = 0.16f),
        shape = CircleShape,
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                // Decorative: the row's merged semantics already describe the record fully.
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
