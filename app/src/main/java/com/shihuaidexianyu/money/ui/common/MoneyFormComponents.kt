package com.shihuaidexianyu.money.ui.common

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

enum class MoneyDateTimePickerField {
    DATE,
    TIME,
}

@Composable
fun MoneyFormPage(
    title: String,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    trailing: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 112.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(20.dp),
    content: LazyListScope.() -> Unit,
) {
    Column(modifier = modifier) {
        snackbarHostState?.let { SnackbarHost(hostState = it) }
        LazyColumn(
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
        ) {
            item { MoneyPageTitle(title = title, trailing = trailing) }
            content()
        }
    }
}

@Composable
fun MoneyConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

@Composable
fun MoneyTextInputDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            MoneySingleLineField(
                value = value,
                onValueChange = onValueChange,
                label = null,
                modifier = modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

@Composable
fun MoneySingleLineField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String?,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    enabled: Boolean = true,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        textStyle = textStyle,
    )
}

@Composable
fun <T> MoneyChoiceDialog(
    title: String,
    options: List<T>,
    selected: T? = null,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "关闭",
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                options.forEach { option ->
                    TextButton(
                        onClick = { onSelect(option) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = label(option),
                            color = if (option == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyDatePickerDialogHost(
    initialSelectedDateMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long?) -> Unit,
) {
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = initialSelectedDateMillis,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.selectedDateMillis) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
fun MoneyTimePickerDialogHost(
    initialTimeMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val zoneDateTime = java.time.Instant.ofEpochMilli(initialTimeMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    androidx.compose.runtime.LaunchedEffect(initialTimeMillis) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onConfirm(hour, minute) },
            zoneDateTime.hour,
            zoneDateTime.minute,
            true,
        ).apply {
            setOnDismissListener { onDismiss() }
        }.show()
    }
}

@Composable
fun MoneyDateTimeFields(
    valueMillis: Long,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier,
    dateLabel: String = "日期",
    timeLabel: String = "时间",
    timeSubtitle: String? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoneySelectionField(
            label = dateLabel,
            value = DateTimeTextFormatter.formatDateOnly(valueMillis),
            modifier = Modifier.clickable(onClick = onDateClick),
        )
        MoneySelectionField(
            label = timeLabel,
            value = DateTimeTextFormatter.formatTimeOnly(valueMillis),
            subtitle = timeSubtitle,
            modifier = Modifier.clickable(onClick = onTimeClick),
        )
    }
}

@Composable
fun MoneyAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "金额",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.displayMedium,
    )
}

@Composable
fun MoneySaveButton(
    onClick: () -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "保存",
    savingLabel: String = "保存中...",
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled && !isSaving,
    ) {
        Text(if (isSaving) savingLabel else label)
    }
}

@Composable
fun <T> MoneyPickerField(
    label: String,
    value: String,
    dialogTitle: String,
    options: List<T>,
    selected: T? = null,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        MoneyChoiceDialog(
            title = dialogTitle,
            options = options,
            selected = selected,
            label = optionLabel,
            onSelect = {
                onSelect(it)
                showDialog = false
            },
            onDismiss = { showDialog = false },
        )
    }

    MoneySelectionField(
        label = label,
        value = value,
        modifier = modifier.clickable { showDialog = true },
    )
}

