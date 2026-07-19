package com.shihuaidexianyu.money.ui.common

import androidx.compose.runtime.Composable
import com.shihuaidexianyu.money.util.DateTimeTextFormatter

/**
 * Hosts the date OR time picker dialog based on [field]. Centralizes the 30-line `when` block that
 * was previously copy-pasted across form screens. The caller keeps the [field] state (so it can be
 * reset on save/dismiss); this composable just renders the right dialog and forwards the picked
 * timestamp back via [onPick].
 *
 * [MoneyDateTimePickerField.DATE]: shows [MoneyDatePickerDialogHost], and on confirm calls
 * [DateTimeTextFormatter.replaceDate] to merge the new date with the existing time.
 *
 * [MoneyDateTimePickerField.TIME]: shows [MoneyTimePickerDialogHost], and on confirm calls
 * [DateTimeTextFormatter.replaceTime] to merge the new time with the existing date.
 *
 * Both branches call [onDismiss] when the user cancels.
 */
@Composable
fun MoneyDateTimePickerHost(
    field: MoneyDateTimePickerField?,
    currentMillis: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    field ?: return
    when (field) {
        MoneyDateTimePickerField.DATE -> {
            MoneyDatePickerDialogHost(
                initialSelectedDateMillis = currentMillis,
                onDismiss = onDismiss,
                onConfirm = { selectedDate ->
                    selectedDate?.let {
                        onPick(
                            DateTimeTextFormatter.replaceDate(
                                baseTimeMillis = currentMillis,
                                selectedDateMillis = it,
                            ),
                        )
                    }
                    onDismiss()
                },
            )
        }

        MoneyDateTimePickerField.TIME -> {
            MoneyTimePickerDialogHost(
                initialTimeMillis = currentMillis,
                onDismiss = onDismiss,
                onConfirm = { hour, minute ->
                    onPick(
                        DateTimeTextFormatter.replaceTime(
                            baseTimeMillis = currentMillis,
                            hour = hour,
                            minute = minute,
                        ),
                    )
                    onDismiss()
                },
            )
        }
    }
}
