package com.rayluxeo.aida.ui.voicenotes

import com.rayluxeo.aida.core.common.SpokenLanguage

/**
 * Every word the Voice Notes screen puts in front of Abu, in one language.
 *
 * The screen records and plays back; the measured length of a clip stays a bare m:ss, but every
 * label around it — the recording status, the transport buttons, the empty list and every notice
 * the view model raises — comes from here, so a Bengali screen is not an English strip sitting
 * over a Bengali list.
 *
 * The notices are worded here rather than in [VoiceNotesViewModel] for the same reason Mira's
 * spoken replies are: the view model concludes what happened as a [VoiceNotesNotice] value, and
 * the language it is said in is decided at the glass, where the current language is known.
 */
internal interface VoiceNotesWording {

    val title: String

    /** Shown when nothing has been recorded yet. A calm statement, not a prompt with a flourish. */
    val emptyMessage: String

    /** The line above the transport, saying what the recorder is doing right now. */
    fun recordingStatus(state: RecordingState): String

    val record: String

    val pause: String

    val resume: String

    val stopAndSave: String

    val play: String

    /** TalkBack's name for the delete control on a saved note. */
    val deleteNote: String

    /** A saved clip that carries no transcript to show in its place. */
    val untitledNote: String

    /** What the view model concluded, rendered in Abu's language. */
    fun notice(notice: VoiceNotesNotice): String
}

internal fun voiceNotesWording(language: SpokenLanguage): VoiceNotesWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliVoiceNotesWording
    SpokenLanguage.ENGLISH -> EnglishVoiceNotesWording
}

/** Voice Notes in English. Short labels, plain sentences, nothing exclaimed. */
internal object EnglishVoiceNotesWording : VoiceNotesWording {

    override val title: String = "Voice Notes"

    override val emptyMessage: String = "No voice notes recorded yet. Tap Record above to start."

    override fun recordingStatus(state: RecordingState): String = when (state) {
        RecordingState.IDLE -> "Ready to record a voice note"
        RecordingState.RECORDING -> "Recording audio…"
        RecordingState.PAUSED -> "Recording paused"
    }

    override val record: String = "Record"

    override val pause: String = "Pause"

    override val resume: String = "Resume"

    override val stopAndSave: String = "Stop & Save"

    override val play: String = "Play"

    override val deleteNote: String = "Delete voice note"

    override val untitledNote: String = "Recorded audio note"

    override fun notice(notice: VoiceNotesNotice): String = when (notice) {
        VoiceNotesNotice.RECORDING_STARTED -> "Recording started"
        VoiceNotesNotice.COULD_NOT_START -> "Couldn't start recording"
        VoiceNotesNotice.COULD_NOT_SAVE_RECORDING -> "Couldn't save the recording"
        VoiceNotesNotice.SAVED -> "Voice note saved"
        VoiceNotesNotice.COULD_NOT_SAVE -> "Couldn't save the voice note"
        VoiceNotesNotice.NO_RECORDING_TO_PLAY -> "This note has no recording to play"
        VoiceNotesNotice.RECORDING_MISSING -> "The recording is missing"
        VoiceNotesNotice.COULD_NOT_PLAY -> "Couldn't play the recording"
        VoiceNotesNotice.DELETED -> "Voice note deleted"
    }
}

/**
 * Voice Notes in Bengali. Bengali script throughout, calm and plain.
 *
 * "ভয়েস নোট" and "অডিও" are written in Bengali script rather than translated, because that is how
 * Abu says them. No digit crosses into this file — the only number the screen shows is the clip's
 * m:ss, which the screen formats itself.
 */
internal object BengaliVoiceNotesWording : VoiceNotesWording {

    override val title: String = "ভয়েস নোট"

    override val emptyMessage: String = "এখনও কোনো ভয়েস নোট রেকর্ড করা হয়নি। শুরু করতে উপরে রেকর্ড চাপুন।"

    override fun recordingStatus(state: RecordingState): String = when (state) {
        RecordingState.IDLE -> "ভয়েস নোট রেকর্ড করতে প্রস্তুত"
        RecordingState.RECORDING -> "অডিও রেকর্ড করা হচ্ছে…"
        RecordingState.PAUSED -> "রেকর্ডিং থামানো আছে"
    }

    override val record: String = "রেকর্ড"

    override val pause: String = "বিরতি"

    override val resume: String = "আবার শুরু"

    override val stopAndSave: String = "থামিয়ে সেভ করুন"

    override val play: String = "চালান"

    override val deleteNote: String = "ভয়েস নোট মুছুন"

    override val untitledNote: String = "রেকর্ড করা অডিও নোট"

    override fun notice(notice: VoiceNotesNotice): String = when (notice) {
        VoiceNotesNotice.RECORDING_STARTED -> "রেকর্ডিং শুরু হয়েছে"
        VoiceNotesNotice.COULD_NOT_START -> "রেকর্ডিং শুরু করা যায়নি"
        VoiceNotesNotice.COULD_NOT_SAVE_RECORDING -> "রেকর্ডিং সেভ করা যায়নি"
        VoiceNotesNotice.SAVED -> "ভয়েস নোট সেভ হয়েছে"
        VoiceNotesNotice.COULD_NOT_SAVE -> "ভয়েস নোট সেভ করা যায়নি"
        VoiceNotesNotice.NO_RECORDING_TO_PLAY -> "এই নোটে চালানোর মতো কোনো রেকর্ডিং নেই"
        VoiceNotesNotice.RECORDING_MISSING -> "রেকর্ডিংটি পাওয়া যাচ্ছে না"
        VoiceNotesNotice.COULD_NOT_PLAY -> "রেকর্ডিং চালানো যায়নি"
        VoiceNotesNotice.DELETED -> "ভয়েস নোট মুছে ফেলা হয়েছে"
    }
}
