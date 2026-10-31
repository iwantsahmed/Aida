package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.domain.brief.BriefKind
import com.rayluxeo.aida.domain.brief.BriefSchedule
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.ota.AppUpdate
import com.rayluxeo.aida.domain.repository.CloudQuotaStatus
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.VoicePreferences
import java.io.File
import java.util.Currency

/**
 * Everything the Settings screen draws.
 *
 * [hasGeminiKey] is kept beside [settings] rather than read from it because the key lives in its
 * own encrypted store: the screen may know that a key exists, and may never see it.
 *
 * [voice] is a second store for the same reason in reverse: how Mira sounds is edited on the Voice
 * Setup screen and read on every spoken reply, so it lives in its own port. Settings shows only
 * the spoken-responses switch and a summary of what has been chosen.
 *
 * The brief labels are worded rather than raw times because wording a moment needs the clock and
 * the chosen language, and a composable has neither: the view model already holds both.
 */
data class SettingsUiState(
    val loading: Boolean = true,
    val settings: MiraSettings = MiraSettings(),
    val voice: VoicePreferences = VoicePreferences.DEFAULT,
    val hasGeminiKey: Boolean = false,
    val canScheduleExact: Boolean = true,
    val briefSchedule: BriefSchedule = BriefSchedule.DEFAULT,
    /** The hour each brief is set for, as Abu reads it: "8 am", "সকাল ৮টা". */
    val morningTimeLabel: String = "",
    val eveningTimeLabel: String = "",
    /** When the next one actually lands, or null while that brief is off. */
    val morningNextLabel: String? = null,
    val eveningNextLabel: String? = null,
    /** Which brief's time picker is open, and null when neither is. */
    val editingBriefTime: BriefKind? = null,
    val geminiEditorOpen: Boolean = false,
    val geminiKeyDraft: String = "",
    val savingGeminiKey: Boolean = false,
    val confirmingClearHistory: Boolean = false,
    /** Abu's default currency, read from his profile — the fallback for a new invoice or quote. */
    val currency: Currency = Money.BDT,
    /** The live free-tier state of the cloud engine. */
    val cloudQuota: CloudQuotaStatus = CloudQuotaStatus.AVAILABLE,
    /** Which selection sheet is open, if any (date style, currency, first day, auto-lock). */
    val activeSheet: SettingsSheet? = null,
    /** Which end of the quiet-hours window is being picked, if either. */
    val quietEditor: QuietBound? = null,
    /** The quiet-hours bounds as Abu reads them: "10 pm", "সন্ধ্যা ৭টা". */
    val quietStartLabel: String = "",
    val quietEndLabel: String = "",
    val confirmingEraseData: Boolean = false,
    val erasingData: Boolean = false,
    val error: String? = null,
    val versionName: String = "",
    val versionCode: Int = 0,
    val otaState: OtaUpdateState = OtaUpdateState.Idle,
) {
    val canSaveGeminiKey: Boolean get() = geminiKeyDraft.isNotBlank() && !savingGeminiKey
}

/** State of Over-The-Air GitHub Release update. */
sealed interface OtaUpdateState {
    data object Idle : OtaUpdateState
    data object Checking : OtaUpdateState
    data class UpToDate(val version: String) : OtaUpdateState
    data class Available(val update: AppUpdate) : OtaUpdateState
    data class Downloading(val progress: Float, val update: AppUpdate) : OtaUpdateState
    data class ReadyToInstall(val file: File, val update: AppUpdate) : OtaUpdateState
    data class Error(val message: String) : OtaUpdateState
}

/** The one-choice-at-a-time selectors on the Settings screen, each shown in its own bottom sheet. */
enum class SettingsSheet { DATE_STYLE, CURRENCY, FIRST_DAY, AUTO_LOCK }

/** Which bound of the quiet-hours window a time picker is editing. */
enum class QuietBound { START, END }
