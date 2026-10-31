package com.rayluxeo.aida.data.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAudioPlayer @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var mediaPlayer: MediaPlayer? = null
    private var currentFilePath: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    /**
     * Plays a file stored relative to Mira's own files directory — the form voice notes and scans
     * keep, since an absolute path does not survive a restore. Resolves it against `filesDir` here,
     * where the [Context] lives, so callers never have to.
     */
    fun playRelative(relativePath: String): AppResult<Unit> =
        play(File(context.filesDir, relativePath).absolutePath)

    fun play(filePath: String): AppResult<Unit> {
        return try {
            if (currentFilePath == filePath && mediaPlayer != null) {
                mediaPlayer?.start()
                _isPlaying.value = true
                return Unit.asSuccess()
            }

            stop()
            val file = File(filePath)
            if (!file.exists()) {
                return failure(MiraErrorCode.NOT_FOUND, "Audio file not found at $filePath")
            }

            val player = MediaPlayer().apply {
                setDataSource(context, Uri.fromFile(file))
                prepare()
                setOnCompletionListener {
                    _isPlaying.value = false
                    _currentPositionMs.value = 0L
                }
                start()
            }

            mediaPlayer = player
            currentFilePath = filePath
            _isPlaying.value = true
            _durationMs.value = player.duration.toLong()
            Unit.asSuccess()
        } catch (e: Exception) {
            failure(MiraErrorCode.STORAGE, "Failed to play audio: ${e.message}")
        }
    }

    fun pause() {
        try {
            mediaPlayer?.pause()
            _isPlaying.value = false
        } catch (_: Exception) {}
    }

    fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.seekTo(positionMs.toInt())
            _currentPositionMs.value = positionMs
        } catch (_: Exception) {}
    }

    fun stop() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) stop()
                release()
            }
            mediaPlayer = null
            currentFilePath = null
            _isPlaying.value = false
            _currentPositionMs.value = 0L
        } catch (_: Exception) {}
    }
}
