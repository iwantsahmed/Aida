package com.rayluxeo.aida.ui.tasks

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Picking a day, once, for the whole Tasks area.
 *
 * The composer sheet, the detail form and the recurrence end date all ask Abu the same question.
 * They asked it in two copies of the same composable, which had already begun to drift, and the
 * UTC rule below was written out twice — the kind of duplication that stays correct only until
 * one of the two is edited. It takes its labels as words rather than a phrasebook, so the tasks
 * screen and the detail screen can each hand it their own.
 *
 * The Material picker reports its selection as midnight UTC of the chosen day, so the value is
 * both written and read in UTC. Converting it through Abu's zone would move the date by a day for
 * anyone east of Greenwich, which is everyone in Dhaka.
 *
 * Material's own dialog rather than [com.rayluxeo.aida.core.design.component.MiraDialog]: the
 * month grid is taller than that dialog's content slot, which does not scroll, so the confirm
 * action would be pushed off the bottom of the screen.
 */
@Composable
internal fun TaskDatePickerDialog(
    initialDate: LocalDate,
    setText: String,
    cancelText: String,
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
                text = setText,
                onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
            )
        },
        modifier = modifier,
        dismissButton = {
            MiraButton(
                style = MiraButtonStyle.TEXT,
                text = cancelText,
                onClick = onDismiss,
                accent = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        DatePicker(state = pickerState)
    }
}
