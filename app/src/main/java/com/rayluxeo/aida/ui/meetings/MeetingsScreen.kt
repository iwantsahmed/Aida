package com.rayluxeo.aida.ui.meetings

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.ui.tasks.TaskDatePickerDialog
import com.rayluxeo.aida.ui.tasks.TaskTimePickerDialog
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.Meeting

@Composable
fun MeetingsScreen(
    onNavigateUp: () -> Unit,
    viewModel: MeetingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val meetings by viewModel.meetings.collectAsStateWithLifecycle()
    val words = meetingsWording(state.language)

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.screenTitle,
                onBack = onNavigateUp,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
        ) {
            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = notice,
                    tone = MiraNoticeTone.INFO,
                    onDismiss = { viewModel.dismissNotice() },
                )
            }

            MiraButton(
                text = words.newMeeting,
                onClick = { viewModel.openNewMeetingSheet() },
                leadingIcon = MiraIcons.Add,
                modifier = Modifier.fillMaxWidth(),
            )

            if (meetings.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.CalendarMonth,
                    message = words.emptyMessage,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    items(meetings, key = { it.id }) { meeting ->
                        MeetingItemCard(meeting = meeting, words = words)
                    }
                }
            }
        }

        if (state.isAddSheetOpen) {
            AddMeetingBottomSheet(
                words = words,
                defaultStart = state.defaultStart,
                onDismiss = { viewModel.closeAddSheet() },
                onSave = { title, location, clientId, projectId, startsAt ->
                    viewModel.createMeeting(title, location, clientId, projectId, startsAt)
                },
            )
        }
    }
}

@Composable
private fun MeetingItemCard(meeting: Meeting, words: MeetingsWording) {
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MiraTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
        ) {
            Text(
                text = meeting.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = words.startsAt(meeting.startsAt.toString().take(16).replace("T", " ")),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!meeting.location.isNullOrBlank()) {
                MiraChip(
                    text = words.location(meeting.location),
                    selected = false,
                )
            }
        }
    }
}

@Composable
private fun AddMeetingBottomSheet(
    words: MeetingsWording,
    defaultStart: LocalDateTime,
    onDismiss: () -> Unit,
    onSave: (String, String?, Long?, Long?, LocalDateTime) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var date by remember(defaultStart) { mutableStateOf(defaultStart.toLocalDate()) }
    var time by remember(defaultStart) { mutableStateOf(defaultStart.toLocalTime()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val whenText = remember(date, time) {
        date.atTime(time).format(MEETING_WHEN_FORMAT)
    }

    MiraBottomSheet(
        onDismissRequest = onDismiss,
        title = words.newMeeting,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            MiraTextField(
                value = title,
                onValueChange = { title = it },
                label = words.meetingTitleLabel,
                placeholder = words.meetingTitlePlaceholder,
            )

            MiraTextField(
                value = location,
                onValueChange = { location = it },
                label = words.locationLabel,
            )

            MiraListRow(
                title = words.meetingWhen,
                trailingText = whenText,
                onClick = { showDatePicker = true },
                leading = {
                    Icon(
                        imageVector = MiraIcons.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xs))

            MiraButton(
                text = words.saveMeeting,
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, location.takeIf { it.isNotBlank() }, null, null, date.atTime(time))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showDatePicker) {
        TaskDatePickerDialog(
            initialDate = date,
            setText = words.setLabel,
            cancelText = words.cancelLabel,
            // Pick the day, then step straight to the time — the two halves of "when" in one flow.
            onPick = { picked ->
                date = picked
                showDatePicker = false
                showTimePicker = true
            },
            onDismiss = { showDatePicker = false },
        )
    }
    if (showTimePicker) {
        TaskTimePickerDialog(
            title = words.pickTime,
            initialTime = time,
            setText = words.setLabel,
            cancelText = words.cancelLabel,
            onPick = { picked ->
                time = picked
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

private val MEETING_WHEN_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a")
