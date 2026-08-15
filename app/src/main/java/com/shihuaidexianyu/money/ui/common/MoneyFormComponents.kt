package com.shihuaidexianyu.money.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shihuaidexianyu.money.util.DateTimeTextFormatter
import com.shihuaidexianyu.money.R

enum class MoneyDateTimePickerField {
    DATE,
    TIME,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyFormPage(
    title: String,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState? = null,
    onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    listState: LazyListState? = null,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = MoneyDimens.bottomNavContentPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(14.dp),
    content: LazyListScope.() -> Unit,
) {
    val defaultListState = rememberLazyListState()
    val resolvedListState = listState ?: defaultListState
    val appBarScrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(appBarScrollBehavior.nestedScrollConnection),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        MoneyBackButton(onClick = onBack)
                    }
                },
                actions = { trailing?.invoke() },
                scrollBehavior = appBarScrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            LazyColumn(
                state = resolvedListState,
                modifier = Modifier.weight(1f),
                contentPadding = contentPadding,
                verticalArrangement = verticalArrangement,
            ) {
                content()
            }
        }
        snackbarHostState?.let { hostState ->
            SnackbarHost(
                hostState = hostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
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
    destructive: Boolean = false,
    showDismissAction: Boolean = true,
) {
    val resolvedConfirmLabel = confirmLabel ?: stringResource(R.string.action_confirm)
    val resolvedDismissLabel = dismissLabel ?: stringResource(R.string.action_cancel)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = resolvedConfirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
            }
        },
        dismissButton = if (showDismissAction) {
            { TextButton(onClick = onDismiss) { Text(resolvedDismissLabel) } }
        } else {
            null
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
    val pickerInitialSelection = initialSelectedDateMillis?.let(
        DateTimeTextFormatter::toDatePickerMillis,
    )
    val pickerState = androidx.compose.material3.rememberDatePickerState(
        initialSelectedDateMillis = pickerInitialSelection,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        pickerState.selectedDateMillis?.let(
                            DateTimeTextFormatter::fromDatePickerMillis,
                        ),
                    )
                },
            ) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(
            state = pickerState,
            title = {
                Text(
                    text = stringResource(R.string.date_picker_title),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
                )
            },
            headline = {
                Text(
                    text = pickerState.selectedDateMillis?.let {
                        DateTimeTextFormatter.formatDateOnly(it, java.time.ZoneOffset.UTC)
                    } ?: stringResource(R.string.date_picker_no_selection),
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyTimePickerDialogHost(
    initialTimeMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val zoneDateTime = java.time.Instant.ofEpochMilli(initialTimeMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    val pickerState = androidx.compose.material3.rememberTimePickerState(
        initialHour = zoneDateTime.hour,
        initialMinute = zoneDateTime.minute,
        is24Hour = true,
    )
    androidx.compose.material3.TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = { Text(stringResource(R.string.field_time)) },
    ) {
        androidx.compose.material3.TimePicker(state = pickerState)
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
            onClick = onDateClick,
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
            onClick = onTimeClick,
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
    enabled: Boolean = true,
    autoOpenKeypad: Boolean = false,
) {
    val resolvedLabel = label ?: stringResource(R.string.field_amount)
    var showKeypad by remember { mutableStateOf(autoOpenKeypad) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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
        )
        Surface(
            onClick = {
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                showKeypad = true
            },
            modifier = Modifier
                .matchParentSize()
                .semantics {
                    contentDescription = resolvedLabel
                    stateDescription = value.ifBlank { "0" }
                    if (isError && supportingText != null) {
                        error(supportingText)
                    }
                },
            enabled = enabled,
            color = Color.Transparent,
            shape = MaterialTheme.shapes.extraSmall,
            content = {},
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
    val haptics = LocalHapticFeedback.current
    Button(
        onClick = {
            // A firmer confirm tick on the commit action of every form.
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp),
        enabled = enabled && !isSaving,
        shape = MaterialTheme.shapes.large,
    ) {
        if (isSaving) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(
                text = resolvedLabel,
                style = MaterialTheme.typography.titleMedium,
            )
        }
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
        modifier = modifier,
        onClick = { showDialog = true },
    )
}
