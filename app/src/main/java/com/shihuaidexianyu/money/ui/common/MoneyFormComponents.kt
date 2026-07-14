package com.shihuaidexianyu.money.ui.common

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.R

enum class MoneyDateTimePickerField {
    DATE,
    TIME,
}

@Composable
fun MoneyFormPage(
    title: String,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    listState: LazyListState? = null,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = MoneyDimens.bottomNavContentPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: LazyListScope.() -> Unit,
) {
    val defaultListState = rememberLazyListState()
    val resolvedListState = listState ?: defaultListState

    Column(modifier = modifier) {
        MoneyPageTitle(
            title = title,
            leading = onBack?.let { { MoneyBackButton(onClick = it) } },
            trailing = trailing,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 4.dp),
        )
        LazyColumn(
            state = resolvedListState,
            contentPadding = contentPadding,
            verticalArrangement = verticalArrangement,
        ) {
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
    confirmLabel: String? = null,
    dismissLabel: String? = null,
) {
    val resolvedConfirmLabel = confirmLabel ?: stringResource(R.string.action_confirm)
    val resolvedDismissLabel = dismissLabel ?: stringResource(R.string.action_cancel)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(resolvedConfirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(resolvedDismissLabel) }
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
    confirmLabel: String? = null,
    dismissLabel: String? = null,
) {
    val resolvedConfirmLabel = confirmLabel ?: stringResource(R.string.action_confirm)
    val resolvedDismissLabel = dismissLabel ?: stringResource(R.string.action_cancel)
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
            TextButton(onClick = onConfirm) { Text(resolvedConfirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(resolvedDismissLabel) }
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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    supportingText: String? = null,
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
        keyboardOptions = keyboardOptions,
        isError = isError,
        supportingText = supportingText?.let { { Text(it) } },
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

@Composable
fun <T> MoneyChoiceDialog(
    title: String,
    options: List<T>,
    selected: T? = null,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String? = null,
) {
    val resolvedDismissLabel = dismissLabel ?: stringResource(R.string.action_close)
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
            TextButton(onClick = onDismiss) { Text(resolvedDismissLabel) }
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
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
    dateLabel: String? = null,
    timeLabel: String? = null,
    timeSubtitle: String? = null,
    errorText: String? = null,
) {
    val resolvedDateLabel = dateLabel ?: stringResource(R.string.field_date)
    val resolvedTimeLabel = timeLabel ?: stringResource(R.string.field_time)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MoneySelectionField(
            label = resolvedDateLabel,
            value = DateTimeTextFormatter.formatDateOnly(valueMillis),
            modifier = Modifier.clickable(onClick = onDateClick),
            isError = errorText != null,
        )
        errorText?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        MoneySelectionField(
            label = resolvedTimeLabel,
            value = DateTimeTextFormatter.formatTimeOnly(valueMillis),
            subtitle = timeSubtitle,
            modifier = Modifier.clickable(onClick = onTimeClick),
            isError = errorText != null,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MoneyAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    allowSigned: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
) {
    val resolvedLabel = label ?: stringResource(R.string.field_amount)
    var showKeypad by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(12.dp)

    if (showKeypad) {
        MoneyAmountKeypadSheet(
            value = value,
            label = resolvedLabel,
            allowSigned = allowSigned,
            onValueChange = onValueChange,
            onDismiss = { showKeypad = false },
        )
    }

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(resolvedLabel) },
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {},
            singleLine = true,
            readOnly = true,
            textStyle = MaterialTheme.typography.displayMedium,
            isError = isError,
            supportingText = supportingText?.let { { Text(it) } },
            shape = shape,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            ),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    role = Role.Button,
                    onClick = {
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        showKeypad = true
                    },
                )
                .semantics {
                    contentDescription = resolvedLabel
                    stateDescription = value.ifBlank { "0" }
                    if (isError && supportingText != null) {
                        error(supportingText)
                    }
                },
        )
    }
}

@Composable
fun MoneySaveButton(
    onClick: () -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
) {
    val resolvedLabel = label ?: stringResource(R.string.action_save)
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        enabled = enabled && !isSaving,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Text(
            text = resolvedLabel,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun <T> MoneyPickerField(
    label: String,
    value: String,
    dialogTitle: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    selected: T? = null,
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
