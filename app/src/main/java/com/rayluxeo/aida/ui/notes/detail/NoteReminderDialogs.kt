package com.rayluxeo.aida.ui.notes.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * The day and then the time a reminder about this note should arrive.
 *
 * Two steps rather than one because they are two decisions, and because Material has no combined
 * picker. The day is asked first, so the time picker opens knowing which day it is setting.
 */
@Composable
internal fun NoteReminderDialogs(
    initialDate: LocalDate,
    initialTime: LocalTime,
    words: NoteDetailWording,
    onPick: (LocalDate, LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var chosenDate by remember { mutableStateOf<LocalDate?>(null) }
    val date = chosenDate

    if (date == null) {
        ReminderDateDialog(
            initialDate = initialDate,
            words = words,
            onPick = { picked -> chosenDate = picked },
            onDismiss = onDismiss,
            modifier = modifier,
        )
    } else {
        ReminderTimeDialog(
            initialTime = initialTime,
            words = words,
            onPick = { time -> onPick(date, time) },
            onDismiss = onDismiss,
            modifier = modifier,
        )
    }
}

/**
 * The Material picker reports its selection as midnight UTC of the chosen day, so the value is
 * both written and read in UTC. Converting it through Abu's zone would move the date by a day for
 * anyone east of Greenwich, which is everyone in Dhaka.
 *
 * This one keeps Material's own dialog: [DatePicker] brings its own header above a month grid,
 * which is taller than `MiraDialog`'s content slot can hold on a phone.
 */
@Composable
private fun ReminderDateDialog(
    initialDate: LocalDate,
    words: NoteDetailWording,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            MiraButton(
                style = MiraButtonStyle.TEXT,
                text = words.set,
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis == null) {
                        onDismiss()
                    } else {
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
            )
        },
        modifier = modifier,
        dismissButton = {
            MiraButton(
                style = MiraButtonStyle.TEXT,
                text = words.cancel,
                onClick = onDismiss,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        DatePicker(state = pickerState)
    }
}

/**
 * A [MiraDialog] rather than Material's `AlertDialog`: on an AMOLED screen the Material container
 * arrives with its own grey tonal surface, and the clock face then sits on a lighter card than
 * everything behind it.
 */
@Composable
private fun ReminderTimeDialog(
    initialTime: LocalTime,
    words: NoteDetailWording,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false,
    )
    MiraDialog(
        title = words.reminderTimeTitle,
        onDismissRequest = onDismiss,
        confirmText = words.set,
        onConfirm = { onPick(LocalTime.of(pickerState.hour, pickerState.minute)) },
        modifier = modifier,
        dismissText = words.cancel,
        onDismiss = onDismiss,
        content = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
    )
}
