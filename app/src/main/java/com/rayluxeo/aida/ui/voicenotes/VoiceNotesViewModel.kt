package com.rayluxeo.aida.ui.voicenotes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.data.audio.LocalAudioPlayer
import com.rayluxeo.aida.data.audio.LocalAudioRecorder
import com.rayluxeo.aida.domain.model.FileRecord
import com.rayluxeo.aida.domain.model.FileSource
import com.rayluxeo.aida.domain.model.VoiceNote
import com.rayluxeo.aida.domain.repository.FileRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.VoiceNoteRepository
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RecordingState { IDLE, RECORDING, PAUSED }

/**
 * Something the screen has to tell Abu, as a value rather than a sentence.
 *
 * The reason lives here with the recorder that raises it; the wording lives in [VoiceNotesWording]
 * and is chosen by language at the glass. That keeps the view model free of English prose and lets
 * a test assert what Mira concluded without pinning the words it concluded it in.
 */
enum class VoiceNotesNotice {
    RECORDING_STARTED,
    COULD_NOT_START,
    COULD_NOT_SAVE_RECORDING,
    SAVED,
    COULD_NOT_SAVE,
    NO_RECORDING_TO_PLAY,
    RECORDING_MISSING,
    COULD_NOT_PLAY,
    DELETED,
}

data class VoiceNotesUiState(
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val recordingState: RecordingState = RecordingState.IDLE,
    val currentlyPlayingId: Long? = null,
    val isPlaying: Boolean = false,
    val notice: VoiceNotesNotice? = null,
)

@HiltViewModel
class VoiceNotesViewModel @Inject constructor(
    private val repository: VoiceNoteRepository,
    private val fileRepository: FileRepository,
    private val audioRecorder: LocalAudioRecorder,
    private val audioPlayer: LocalAudioPlayer,
    private val clock: MiraClock,
    settings: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceNotesUiState())
    val uiState: StateFlow<VoiceNotesUiState> = _uiState.asStateFlow()

    val voiceNotes: StateFlow<List<VoiceNote>> = repository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // The play/pause state follows the player itself, so a note that finishes on its own resets
        // the row rather than leaving the button stuck on "pause".
        viewModelScope.launch {
            audioPlayer.isPlaying.collect { playing ->
                _uiState.update {
                    it.copy(
                        isPlaying = playing,
                        currentlyPlayingId = if (playing) it.currentlyPlayingId else null,
                    )
                }
            }
        }
        // Observed, not read once: the language toggle is several screens away, and a screen that
        // went on naming its controls in English after Abu chose বাংলা is the defect this exists
        // to fix.
        viewModelScope.launch {
            settings.settings.collect { preferences ->
                _uiState.update { it.copy(language = preferences.languageMode.displayLanguage()) }
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            val result = audioRecorder.startRecording("voicenote")
            if (result is AppResult.Success) {
                _uiState.update {
                    it.copy(
                        recordingState = RecordingState.RECORDING,
                        notice = VoiceNotesNotice.RECORDING_STARTED,
                    )
                }
            } else if (result is AppResult.Failure) {
                _uiState.update { it.copy(notice = VoiceNotesNotice.COULD_NOT_START) }
            }
        }
    }

    fun pauseRecording() {
        audioRecorder.pauseRecording()
        _uiState.update { it.copy(recordingState = RecordingState.PAUSED) }
    }

    fun resumeRecording() {
        audioRecorder.resumeRecording()
        _uiState.update { it.copy(recordingState = RecordingState.RECORDING) }
    }

    /**
     * Saves what was actually recorded: the audio file is registered so it can be played back, and
     * the note keeps the file's own measured length — not a placeholder. If nothing was recorded,
     * nothing is claimed saved.
     */
    fun stopRecording(transcript: String? = null) {
        viewModelScope.launch {
            val file = (audioRecorder.stopRecording() as? AppResult.Success)?.value
            _uiState.update { it.copy(recordingState = RecordingState.IDLE) }
            if (file == null || !file.exists() || file.length() == 0L) {
                _uiState.update { it.copy(notice = VoiceNotesNotice.COULD_NOT_SAVE_RECORDING) }
                return@launch
            }
            val now = clock.now()
            val registered = fileRepository.register(
                FileRecord(
                    displayName = file.name,
                    relativePath = "audio_recordings/${file.name}",
                    mimeType = "audio/mp4",
                    sizeBytes = file.length(),
                    source = FileSource.RECORDING,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            val audioFileId = (registered as? AppResult.Success)?.value?.id
            val saved = repository.record(
                VoiceNote(
                    audioFileId = audioFileId,
                    transcript = transcript,
                    duration = audioRecorder.durationOf(file),
                    recordedAt = now,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _uiState.update {
                it.copy(
                    notice = if (saved is AppResult.Success) {
                        VoiceNotesNotice.SAVED
                    } else {
                        VoiceNotesNotice.COULD_NOT_SAVE
                    },
                )
            }
        }
    }

    fun playVoiceNote(voiceNote: VoiceNote) {
        val audioFileId = voiceNote.audioFileId
        if (audioFileId == null) {
            _uiState.update { it.copy(notice = VoiceNotesNotice.NO_RECORDING_TO_PLAY) }
            return
        }
        viewModelScope.launch {
            val record = (fileRepository.byId(audioFileId) as? AppResult.Success)?.value
            if (record == null) {
                _uiState.update { it.copy(notice = VoiceNotesNotice.RECORDING_MISSING) }
                return@launch
            }
            when (audioPlayer.playRelative(record.relativePath)) {
                is AppResult.Success -> _uiState.update { it.copy(currentlyPlayingId = voiceNote.id) }
                is AppResult.Failure -> _uiState.update { it.copy(notice = VoiceNotesNotice.COULD_NOT_PLAY) }
            }
        }
    }

    fun pausePlayback() {
        audioPlayer.pause()
    }

    fun deleteVoiceNote(id: Long) {
        viewModelScope.launch {
            repository.delete(id)
            _uiState.update { it.copy(notice = VoiceNotesNotice.DELETED) }
        }
    }

    fun dismissNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    override fun onCleared() {
        // The player is process-wide; without this it keeps playing after the screen is gone.
        audioPlayer.stop()
    }
}
