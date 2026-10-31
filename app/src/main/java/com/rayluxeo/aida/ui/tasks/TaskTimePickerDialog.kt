package com.rayluxeo.aida.ui.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rayluxeo.aida.core.design.component.MiraDialog
import java.time.LocalTime

/**
 * Picking a time of day, once, for the whole Tasks area.
 *
 * The counterpart to [TaskDatePickerDialog], and here for the same reason: the composer sheet and
 * the detail form had a copy each of this clock face and of the note explaining it. Its labels
 * arrive as words so either screen's phrasebook can supply them.
 *
 * A [MiraDialog] rather than Material's `AlertDialog`: on an AMOLED screen the Material container
 * arrives with its own grey tonal surface, its own radius and its own type scale, and the clock
 * face then sits on a lighter card than everything behind it.
 */
@Composable
internal fun TaskTimePickerDialog(
    title: String,
    initialTime: LocalTime,
    setText: String,
    cancelText: String,
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
        title = title,
        onDismissRequest = onDismiss,
        confirmText = setText,
        onConfirm = {
            onPick(LocalTime.of(pickerState.hour, pickerState.minute))
            onDismiss()
        },
        modifier = modifier,
        dismissText = cancelText,
        content = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
    )
}
