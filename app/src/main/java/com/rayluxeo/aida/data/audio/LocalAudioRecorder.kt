package com.rayluxeo.aida.data.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.os.Build
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalAudioRecorder @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var isPaused = false

    fun startRecording(fileNamePrefix: String): AppResult<File> {
        return try {
            stopRecording()
            val outputDir = File(context.filesDir, "audio_recordings").apply { mkdirs() }
            val outputFile = File(outputDir, "${fileNamePrefix}_${System.currentTimeMillis()}.m4a")

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            currentFile = outputFile
            isPaused = false
            outputFile.asSuccess()
        } catch (e: Exception) {
            failure(MiraErrorCode.STORAGE, "Failed to start audio recording: ${e.message}")
        }
    }

    fun pauseRecording(): AppResult<Unit> {
        return try {
            if (mediaRecorder != null && !isPaused) {
                mediaRecorder?.pause()
                isPaused = true
            }
            Unit.asSuccess()
        } catch (e: Exception) {
            failure(MiraErrorCode.STORAGE, "Failed to pause recording: ${e.message}")
        }
    }

    fun resumeRecording(): AppResult<Unit> {
        return try {
            if (mediaRecorder != null && isPaused) {
                mediaRecorder?.resume()
                isPaused = false
            }
            Unit.asSuccess()
        } catch (e: Exception) {
            failure(MiraErrorCode.STORAGE, "Failed to resume recording: ${e.message}")
        }
    }

    /**
     * The true length of a recorded file, read back from its own metadata. Returns zero rather
     * than throwing if the file cannot be read, so a save never fails on the duration alone.
     */
    fun durationOf(file: File): Duration = try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val ms = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            Duration.ofMillis(ms.coerceAtLeast(0L))
        } finally {
            retriever.release()
        }
    } catch (_: Exception) {
        Duration.ZERO
    }

    fun stopRecording(): AppResult<File?> {
        val file = currentFile
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isPaused = false
            currentFile = null
            file.asSuccess()
        } catch (e: Exception) {
            mediaRecorder?.release()
            mediaRecorder = null
            currentFile = null
            file.asSuccess()
        }
    }
}
