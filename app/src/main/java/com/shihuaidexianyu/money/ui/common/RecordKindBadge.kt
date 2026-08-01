package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.automirrored.rounded.FactCheck
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Badge
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

/** Compact Material 3 badge marker for dense ledger lists. */
@Composable
fun RecordKindDot(
    kind: HistoryRecordKind,
    amount: Long,
    modifier: Modifier = Modifier,
) {
    Badge(
        modifier = modifier.size(8.dp),
        containerColor = recordKindAccent(kind, amount),
    )
}

/**
 * Small circular badge for a ledger record row, color-coded and icon-coded by record kind.
 * Shares its visual language with [AccountIconBadge] (tinted icon on a 12% alpha tinted disc).
 */
@Composable
fun RecordKindBadge(
    kind: HistoryRecordKind,
    amount: Long,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
) {
    val accent = recordKindAccent(kind, amount)
    val icon = when (kind) {
        HistoryRecordKind.CASH_FLOW ->
            if (amount > 0) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown
        HistoryRecordKind.TRANSFER -> Icons.Rounded.SwapHoriz
        HistoryRecordKind.BALANCE_UPDATE,
        HistoryRecordKind.BALANCE_ADJUSTMENT,
        -> Icons.AutoMirrored.Rounded.FactCheck
    }
    Surface(
        modifier = modifier.size(size),
        color = accent.copy(alpha = 0.12f),
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
