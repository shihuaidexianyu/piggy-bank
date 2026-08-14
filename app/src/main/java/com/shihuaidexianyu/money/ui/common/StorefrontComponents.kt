package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.shihuaidexianyu.money.R

/**
 * Material 3 card used as the white foreground layer above the softly tinted page canvas.
 */
@Composable
fun MoneyCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
    if (onClick == null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = moneyGroupCardColors(),
            content = cardContent,
        )
    } else {
        Card(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            colors = moneyGroupCardColors(),
            content = cardContent,
        )
    }
}

/** Stock M3 card colors for every grouped container in the app. */
@Composable
private fun moneyGroupCardColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
)

/**
 * Stock Material 3 tonal button wrapper so every secondary action uses the same component.
 */
@Composable
fun MoneyTonalButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        enabled = enabled,
        contentPadding = contentPadding,
        content = content,
    )
}

@Composable
fun MoneyBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.action_back),
        )
    }
}

@Composable
fun MoneySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (trailingContent != null) {
            trailingContent()
        } else {
            trailing?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MoneyStatusPill(
    text: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
fun MoneyMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
) {
    Surface(
        modifier = modifier,
        color = accent.copy(alpha = 0.08f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = accent,
            )
        }
    }
}

@Composable
fun MoneyEmptyStateCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable ColumnScope.() -> Unit)? = null,
) {
    MoneyCard(modifier = modifier) {
        if (icon != null) {
            Surface(
                modifier = Modifier
                    .size(48.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = CircleShape,
            ) {
                Box(
                    modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        action?.invoke(this)
    }
}

@Composable
fun MoneyInlineLabelValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun MoneyListSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = moneyGroupCardColors(),
    ) {
        Column(content = content)
    }
}

@Composable
fun MoneyListRow(
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    subtitle: String? = null,
    trailing: String? = null,
    showChevron: Boolean = onClick != null,
    isClickable: Boolean = onClick != null,
    leading: (@Composable () -> Unit)? = null,
    accessory: (@Composable () -> Unit)? = null,
    switchChecked: Boolean? = null,
) {
    // Build a single spoken description so Talkback reads the row as one item, e.g.
    // "招商银行 最近核对 2024-01-15 余额 12345 元".
    val switchStateText = switchChecked?.let {
        stringResource(if (it) R.string.settings_channel_enabled else R.string.settings_channel_disabled)
    }
    val rowDescription = buildString {
        append(title)
        subtitle?.let { append("，$it") }
        trailing?.let { append("，$it") }
        switchStateText?.let { append("，$it") }
    }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { text -> { Text(text) } },
        leadingContent = leading,
        trailingContent = if (trailing != null || accessory != null || showChevron) {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    trailing?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    accessory?.invoke()
                    if (showChevron) {
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .semantics(mergeDescendants = true) {
                contentDescription = rowDescription
                if (isClickable || onClick != null) {
                    role = if (switchChecked != null) Role.Switch else Role.Button
                }
                switchStateText?.let { stateDescription = it }
                if (!enabled) disabled()
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun MoneySectionDivider() {
    HorizontalDivider()
}

@Composable
fun MoneySelectionField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    subtitle: String? = null,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clearAndSetSemantics {} else Modifier),
            readOnly = true,
            enabled = true,
            singleLine = true,
            label = { Text(label) },
            supportingText = (supportingText ?: subtitle)?.let { text -> { Text(text) } },
            isError = isError,
            trailingIcon = if (onClick != null) {
                {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                null
            },
        )
        if (onClick != null) {
            Surface(
                onClick = onClick,
                modifier = Modifier
                    .matchParentSize()
                    .semantics {
                        contentDescription = "$label，$value"
                        subtitle?.let { stateDescription = it }
                        if (isError && supportingText != null) {
                            error(supportingText)
                        }
                    },
                color = Color.Transparent,
                shape = MaterialTheme.shapes.extraSmall,
                content = {},
            )
        }
    }
}
