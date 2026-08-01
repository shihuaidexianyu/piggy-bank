package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.shihuaidexianyu.money.R
import com.shihuaidexianyu.money.util.AmountFormatter
import com.shihuaidexianyu.money.util.AmountKey
import com.shihuaidexianyu.money.util.appendAmountKey
import com.shihuaidexianyu.money.util.parseAmountKeypadPreview

private data class AmountKeypadButtonSpec(
    val label: String? = null,
    val key: AmountKey? = null,
    val isOperator: Boolean = false,
    val isClear: Boolean = false,
    val isDone: Boolean = false,
    val weight: Float = 1f,
    @param:StringRes val labelRes: Int? = null,
)

private val amountKeypadRows = listOf(
    listOf(
        AmountKeypadButtonSpec("7", AmountKey.Digit(7)),
        AmountKeypadButtonSpec("8", AmountKey.Digit(8)),
        AmountKeypadButtonSpec("9", AmountKey.Digit(9)),
        AmountKeypadButtonSpec(labelRes = R.string.action_delete, key = AmountKey.Delete),
    ),
    listOf(
        AmountKeypadButtonSpec("4", AmountKey.Digit(4)),
        AmountKeypadButtonSpec("5", AmountKey.Digit(5)),
        AmountKeypadButtonSpec("6", AmountKey.Digit(6)),
        AmountKeypadButtonSpec("+", AmountKey.Plus, isOperator = true),
    ),
    listOf(
        AmountKeypadButtonSpec("1", AmountKey.Digit(1)),
        AmountKeypadButtonSpec("2", AmountKey.Digit(2)),
        AmountKeypadButtonSpec("3", AmountKey.Digit(3)),
        AmountKeypadButtonSpec("-", AmountKey.Minus, isOperator = true),
    ),
    listOf(
        AmountKeypadButtonSpec("C", AmountKey.Clear, isClear = true),
        AmountKeypadButtonSpec("0", AmountKey.Digit(0)),
        AmountKeypadButtonSpec(".", AmountKey.Decimal),
        AmountKeypadButtonSpec(labelRes = R.string.action_done, isDone = true),
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
    val currencySymbol = LocalCurrencySymbol.current
    val previewAmount = remember(value, allowSigned) {
        parseAmountKeypadPreview(value, allowSigned)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AmountKeypadDisplay(
                label = label,
                value = value,
                previewAmount = previewAmount,
                currencySymbol = currencySymbol,
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                amountKeypadRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { spec ->
                            AmountKeypadButton(
                                spec = spec,
                                onClick = {
                                    if (spec.isDone) {
                                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                        onDismiss()
                                    } else {
                                        haptics.performHapticFeedback(HapticFeedbackType.VirtualKey)
                                        onValueChange(appendAmountKey(value, requireNotNull(spec.key), allowSigned))
                                    }
                                },
                                modifier = Modifier.weight(spec.weight),
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
    currencySymbol: String,
) {
    val expressionScrollState = rememberScrollState()
    val hasPreview = previewAmount != null && value.isNotBlank()
    val previewText = if (hasPreview) {
        "= $currencySymbol${AmountFormatter.formatPlain(requireNotNull(previewAmount))}"
    } else {
        ""
    }

    LaunchedEffect(value, expressionScrollState.maxValue) {
        expressionScrollState.scrollTo(expressionScrollState.maxValue)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = value.ifBlank { "0" },
                    modifier = Modifier
                        .horizontalScroll(expressionScrollState)
                        .clearAndSetSemantics {},
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
            if (previewText.isNotEmpty()) {
                Text(
                    text = previewText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun AmountKeypadButton(
    spec: AmountKeypadButtonSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resolvedLabel = spec.labelRes?.let { stringResource(it) } ?: requireNotNull(spec.label)
    val isDelete = spec.key == AmountKey.Delete
    val containerColor = when {
        spec.isClear -> MaterialTheme.colorScheme.errorContainer
        spec.isOperator -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val contentColor = when {
        spec.isClear -> MaterialTheme.colorScheme.error
        spec.isOperator -> MaterialTheme.colorScheme.onSecondaryContainer
        isDelete -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val keyHeight = 58.dp

    val buttonModifier = modifier
        .height(keyHeight)
        .semantics { contentDescription = resolvedLabel }
    val content: @Composable () -> Unit = {
        Box(contentAlignment = Alignment.Center) {
            when {
                isDelete -> Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Backspace,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                spec.isDone -> Text(
                    text = resolvedLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                else -> Text(
                    text = resolvedLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
    if (spec.isDone) {
        Button(
            onClick = onClick,
            modifier = buttonModifier,
            contentPadding = PaddingValues(0.dp),
            shape = MaterialTheme.shapes.large,
            content = { content() },
        )
    } else {
        FilledTonalButton(
            onClick = onClick,
            modifier = buttonModifier,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            contentPadding = PaddingValues(0.dp),
            shape = MaterialTheme.shapes.large,
            content = { content() },
        )
    }
}
