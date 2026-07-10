package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountKey
import com.shihuaidexianyu.money.util.appendAmountKey
import com.shihuaidexianyu.money.util.parseAmountKeypadPreview

private data class AmountKeypadButtonSpec(
    val label: String,
    val key: AmountKey? = null,
    val isPrimary: Boolean = false,
)

private val amountKeypadRows = listOf(
    listOf(
        AmountKeypadButtonSpec("7", AmountKey.Digit(7)),
        AmountKeypadButtonSpec("8", AmountKey.Digit(8)),
        AmountKeypadButtonSpec("9", AmountKey.Digit(9)),
        AmountKeypadButtonSpec("删除", AmountKey.Delete),
    ),
    listOf(
        AmountKeypadButtonSpec("4", AmountKey.Digit(4)),
        AmountKeypadButtonSpec("5", AmountKey.Digit(5)),
        AmountKeypadButtonSpec("6", AmountKey.Digit(6)),
        AmountKeypadButtonSpec("+", AmountKey.Plus),
    ),
    listOf(
        AmountKeypadButtonSpec("1", AmountKey.Digit(1)),
        AmountKeypadButtonSpec("2", AmountKey.Digit(2)),
        AmountKeypadButtonSpec("3", AmountKey.Digit(3)),
        AmountKeypadButtonSpec("-", AmountKey.Minus),
    ),
    listOf(
        AmountKeypadButtonSpec("C", AmountKey.Clear),
        AmountKeypadButtonSpec("0", AmountKey.Digit(0)),
        AmountKeypadButtonSpec(".", AmountKey.Decimal),
        AmountKeypadButtonSpec("完成", isPrimary = true),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MoneyAmountKeypadSheet(
    value: String,
    label: String,
    allowSigned: Boolean,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val previewAmount = remember(value, allowSigned) {
        parseAmountKeypadPreview(value, allowSigned)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AmountKeypadDisplay(
                label = label,
                value = value,
                previewAmount = previewAmount,
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                amountKeypadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { spec ->
                            AmountKeypadButton(
                                spec = spec,
                                onClick = {
                                    val key = spec.key
                                    if (key == null) {
                                        onDismiss()
                                    } else {
                                        onValueChange(appendAmountKey(value, key, allowSigned))
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AmountKeypadDisplay(
    label: String,
    value: String,
    previewAmount: Long?,
) {
    val expressionScrollState = rememberScrollState()
    val hasPreview = previewAmount != null && value.isNotBlank()
    val previewText = if (hasPreview) {
        "= ${AmountFormatter.formatPlain(requireNotNull(previewAmount))}"
    } else {
        " "
    }

    LaunchedEffect(value, expressionScrollState.maxValue) {
        expressionScrollState.scrollTo(expressionScrollState.maxValue)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
            ),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.End,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = value.ifBlank { "0" },
                    modifier = Modifier.horizontalScroll(expressionScrollState),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            Text(
                text = previewText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (hasPreview) MaterialTheme.colorScheme.primary else Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1,
                textAlign = TextAlign.End,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun AmountKeypadButton(
    spec: AmountKeypadButtonSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)
    val isOperator = spec.key == AmountKey.Plus || spec.key == AmountKey.Minus
    val isClear = spec.key == AmountKey.Clear
    val isDelete = spec.key == AmountKey.Delete
    val containerColor = when {
        spec.isPrimary -> MaterialTheme.colorScheme.primary
        isClear -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
        isOperator -> MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
        isDelete -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.68f)
        else -> MaterialTheme.colorScheme.surface
    }
    val contentColor = when {
        spec.isPrimary -> MaterialTheme.colorScheme.onPrimary
        isClear -> MaterialTheme.colorScheme.error
        isOperator -> MaterialTheme.colorScheme.primary
        isDelete -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        spec.isPrimary -> MaterialTheme.colorScheme.primary
        isClear -> MaterialTheme.colorScheme.error.copy(alpha = 0.22f)
        isOperator -> MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        isDelete -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .clickable {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shape = shape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (spec.key == AmountKey.Delete) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = "删除",
                    tint = contentColor,
                )
            } else {
                Text(
                    text = spec.label,
                    style = if (spec.isPrimary) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleLarge
                    },
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
