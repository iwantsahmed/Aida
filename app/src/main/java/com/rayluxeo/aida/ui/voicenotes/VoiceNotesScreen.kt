package com.rayluxeo.aida.ui.voicenotes

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.VoiceNote

@Composable
fun VoiceNotesScreen(
    onNavigateUp: () -> Unit,
    viewModel: VoiceNotesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val voiceNotes by viewModel.voiceNotes.collectAsStateWithLifecycle()
    val words = voiceNotesWording(state.language)

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.title,
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
                    text = words.notice(notice),
                    tone = MiraNoticeTone.INFO,
                    onDismiss = { viewModel.dismissNotice() },
                )
            }

            RecordingCard(
                recordingState = state.recordingState,
                words = words,
                onStart = { viewModel.startRecording() },
                onPause = { viewModel.pauseRecording() },
                onResume = { viewModel.resumeRecording() },
                onStop = { viewModel.stopRecording() },
            )

            if (voiceNotes.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.Mic,
                    message = words.emptyMessage,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    items(voiceNotes, key = { it.id }) { voiceNote ->
                        VoiceNoteItemCard(
                            voiceNote = voiceNote,
                            words = words,
                            isPlaying = state.currentlyPlayingId == voiceNote.id && state.isPlaying,
                            onPlay = { viewModel.playVoiceNote(voiceNote) },
                            onPause = { viewModel.pausePlayback() },
                            onDelete = { viewModel.deleteVoiceNote(voiceNote.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingCard(
    recordingState: RecordingState,
    words: VoiceNotesWording,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MiraTheme.spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
        ) {
            Text(
                text = words.recordingStatus(recordingState),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (recordingState) {
                    RecordingState.IDLE -> {
                        MiraButton(
                            text = words.record,
                            onClick = onStart,
                            leadingIcon = MiraIcons.Mic,
                        )
                    }
                    RecordingState.RECORDING -> {
                        MiraButton(
                            text = words.pause,
                            onClick = onPause,
                            leadingIcon = MiraIcons.Pause,
                            style = MiraButtonStyle.SECONDARY,
                        )
                        MiraButton(
                            text = words.stopAndSave,
                            onClick = onStop,
                            leadingIcon = MiraIcons.Stop,
                        )
                    }
                    RecordingState.PAUSED -> {
                        MiraButton(
                            text = words.resume,
                            onClick = onResume,
                            leadingIcon = MiraIcons.PlayArrow,
                        )
                        MiraButton(
                            text = words.stopAndSave,
                            onClick = onStop,
                            leadingIcon = MiraIcons.Stop,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceNoteItemCard(
    voiceNote: VoiceNote,
    words: VoiceNotesWording,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onDelete: () -> Unit,
) {
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MiraTheme.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voiceNote.transcript?.take(50)?.let { "\"$it…\"" } ?: words.untitledNote,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = "${formatVoiceNoteDuration(voiceNote.duration)} • ${voiceNote.recordedAt.toString().take(10)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                MiraIconButton(
                    icon = if (isPlaying) MiraIcons.Pause else MiraIcons.PlayArrow,
                    contentDescription = if (isPlaying) words.pause else words.play,
                    onClick = if (isPlaying) onPause else onPlay,
                    tint = MaterialTheme.colorScheme.primary,
                )

                MiraIconButton(
                    icon = MiraIcons.Delete,
                    contentDescription = words.deleteNote,
                    onClick = onDelete,
                )
            }
        }
    }
}

/** A recording's length as m:ss — the real measured duration, not a placeholder. */
private fun formatVoiceNoteDuration(duration: java.time.Duration): String {
    val total = duration.seconds.coerceAtLeast(0L)
    return "%d:%02d".format(total / 60, total % 60)
}
