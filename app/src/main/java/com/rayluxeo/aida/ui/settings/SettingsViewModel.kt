package com.rayluxeo.aida.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.BuildConfig
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.brief.BriefKind
import com.rayluxeo.aida.domain.brief.BriefSchedule
import com.rayluxeo.aida.domain.brief.BriefScheduleRepository
import com.rayluxeo.aida.domain.brief.BriefScheduler
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.model.DateStyle
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.ota.AppUpdate
import com.rayluxeo.aida.domain.repository.CloudAiStatusProvider
import com.rayluxeo.aida.domain.repository.CloudQuotaStatus
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import com.rayluxeo.aida.domain.repository.VoicePreferenceRepository
import com.rayluxeo.aida.domain.repository.VoicePreferences
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.domain.usecase.CheckAppUpdateUseCase
import com.rayluxeo.aida.domain.usecase.DownloadAppUpdateUseCase
import com.rayluxeo.aida.domain.usecase.EraseAllDataUseCase
import com.rayluxeo.aida.ui.tasks.TaskTimeFormatter
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.util.Currency
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Every control on the Settings screen writes straight through to storage — there is no Save
 * button and no draft copy of the settings, so what Abu sees is always what Mira is actually
 * running on.
 *
 * The Gemini key is the one exception: it is typed into a draft, sealed by the credential store,
 * and then dropped from memory. It is never read back into the screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val voice: VoicePreferenceRepository,
    private val credentials: GeminiCredentialStore,
    private val conversation: ConversationRepository,
    private val scheduler: ReminderScheduler,
    private val briefs: BriefScheduleRepository,
    private val briefScheduler: BriefScheduler,
    private val userProfile: UserProfileRepository,
    private val cloudAiStatus: CloudAiStatusProvider,
    private val eraseAllData: EraseAllDataUseCase,
    private val checkAppUpdate: CheckAppUpdateUseCase,
    private val downloadAppUpdate: DownloadAppUpdateUseCase,
    private val clock: MiraClock,
    private val times: TaskTimeFormatter,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            versionName = BuildConfig.VERSION_NAME,
            versionCode = BuildConfig.VERSION_CODE,
            canScheduleExact = scheduler.canScheduleExact(),
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settings.settings,
                voice.preferences,
                credentials.hasKey,
                briefs.schedule,
                cloudAiStatus.quota,
            ) { current, voicePrefs, hasKey, schedule, quota ->
                Loaded(current, voicePrefs, hasKey, schedule, quota)
            }.combine(userProfile.observe()) { loaded, profile ->
                loaded to profile
            }.collect { (loaded, profile) ->
                val language = loaded.settings.languageMode.displayLanguage()
                _state.update {
                    it.copy(
                        loading = false,
                        settings = loaded.settings,
                        voice = loaded.voice,
                        hasGeminiKey = loaded.hasGeminiKey,
                        cloudQuota = loaded.quota,
                        currency = profile?.defaultCurrency ?: it.currency,
                        quietStartLabel = times.timeLabel(minuteOfDay(loaded.settings.quietHoursStartMinute), language),
                        quietEndLabel = times.timeLabel(minuteOfDay(loaded.settings.quietHoursEndMinute), language),
                    ).withBriefSchedule(
                        schedule = loaded.briefSchedule,
                        language = language,
                    )
                }
            }
        }
    }

    /**
     * Abu can grant exact alarms from a system screen and come straight back, so the answer is
     * re-read whenever the screen resumes rather than cached from construction.
     */
    fun refreshExactAlarmPermission() {
        _state.update { it.copy(canScheduleExact = scheduler.canScheduleExact()) }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch { settings.setLanguageMode(mode) }
    }

    fun setSpokenResponses(enabled: Boolean) {
        viewModelScope.launch { voice.setSpokenResponsesEnabled(enabled) }
    }

    fun setActivationSound(enabled: Boolean) {
        viewModelScope.launch { settings.setActivationSound(enabled) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { settings.setHapticFeedback(enabled) }
    }

    fun setConversationStyle(style: ConversationStyle) {
        viewModelScope.launch { settings.setConversationStyle(style) }
    }

    // --- GENERAL: date style, currency, first day of week (each picked in a sheet) ---

    fun openSheet(sheet: SettingsSheet) {
        _state.update { it.copy(activeSheet = sheet) }
    }

    fun dismissSheet() {
        _state.update { it.copy(activeSheet = null) }
    }

    fun setDateStyle(style: DateStyle) {
        _state.update { it.copy(activeSheet = null) }
        viewModelScope.launch { settings.setDateStyle(style) }
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        _state.update { it.copy(activeSheet = null) }
        viewModelScope.launch { settings.setFirstDayOfWeek(day) }
    }

    fun setUserName(name: String) {
        val trimmed = name.trim()
        viewModelScope.launch {
            settings.setUserName(trimmed)
            val existing = userProfile.current().getOrNull()
            val profile = existing?.copy(displayName = trimmed)
                ?: UserProfile(
                    displayName = trimmed,
                    defaultCurrency = Money.BDT,
                    createdAt = clock.now(),
                    updatedAt = clock.now(),
                )
            userProfile.save(profile)
        }
    }

    /**
     * Currency lives on Abu's profile, the single source the invoice and the brief already read. A
     * phone that has no profile yet gets a minimal one so the choice is not silently dropped; the
     * rest of the profile is filled in where business details are edited.
     */
    fun setCurrency(currency: Currency) {
        _state.update { it.copy(activeSheet = null) }
        viewModelScope.launch {
            val existing = userProfile.current().getOrNull()
            val profile = existing?.copy(defaultCurrency = currency)
                ?: UserProfile(
                    displayName = "",
                    defaultCurrency = currency,
                    createdAt = clock.now(),
                    updatedAt = clock.now(),
                )
            userProfile.save(profile)
        }
    }

    // --- NOTIFICATIONS: per-category switches + quiet hours ---

    fun setNotifyTasks(enabled: Boolean) = updateNotifications { it.copy(tasks = enabled) }
    fun setNotifyReminders(enabled: Boolean) = updateNotifications { it.copy(reminders = enabled) }
    fun setNotifyInvoices(enabled: Boolean) = updateNotifications { it.copy(invoices = enabled) }
    fun setNotifySubscriptions(enabled: Boolean) = updateNotifications { it.copy(subscriptions = enabled) }

    private fun updateNotifications(change: (NotifyFlags) -> NotifyFlags) {
        val s = _state.value.settings
        val next = change(NotifyFlags(s.notifyTasks, s.notifyReminders, s.notifyInvoices, s.notifySubscriptions))
        viewModelScope.launch {
            settings.setNotificationCategories(next.tasks, next.reminders, next.invoices, next.subscriptions)
        }
    }

    fun setQuietHoursEnabled(enabled: Boolean) {
        val s = _state.value.settings
        viewModelScope.launch {
            settings.setQuietHours(enabled, s.quietHoursStartMinute, s.quietHoursEndMinute)
        }
    }

    fun editQuietHours(bound: QuietBound) {
        _state.update { it.copy(quietEditor = bound) }
    }

    fun dismissQuietEditor() {
        _state.update { it.copy(quietEditor = null) }
    }

    fun setQuietHoursBound(bound: QuietBound, time: LocalTime) {
        _state.update { it.copy(quietEditor = null) }
        val s = _state.value.settings
        val minute = time.hour * 60 + time.minute
        viewModelScope.launch {
            settings.setQuietHours(
                enabled = s.quietHoursEnabled,
                startMinute = if (bound == QuietBound.START) minute else s.quietHoursStartMinute,
                endMinute = if (bound == QuietBound.END) minute else s.quietHoursEndMinute,
            )
        }
    }

    // --- AI: the key-preserving soft disable ---

    /** True means cloud is allowed; false keeps the key but refuses every cloud call (§3). */
    fun setCloudAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setCloudAiEnabled(enabled) }
    }

    // --- PRIVACY & SECURITY: app lock, auto-lock, protected areas ---

    fun setBiometricLock(enabled: Boolean) {
        val s = _state.value.settings
        viewModelScope.launch {
            settings.setAppLock(enabled, s.appLockTimeoutSeconds, s.lockWhenBackgrounded)
        }
    }

    fun setAutoLockTimeout(seconds: Int) {
        _state.update { it.copy(activeSheet = null) }
        val s = _state.value.settings
        viewModelScope.launch {
            settings.setAppLock(s.appLockEnabled, seconds, s.lockWhenBackgrounded)
        }
    }

    fun setLockWhenBackgrounded(enabled: Boolean) {
        val s = _state.value.settings
        viewModelScope.launch {
            settings.setAppLock(s.appLockEnabled, s.appLockTimeoutSeconds, enabled)
        }
    }

    fun setProtectMoney(enabled: Boolean) = updateProtection { it.copy(money = enabled) }
    fun setProtectMemory(enabled: Boolean) = updateProtection { it.copy(memory = enabled) }
    fun setProtectInvoices(enabled: Boolean) = updateProtection { it.copy(invoices = enabled) }
    fun setProtectBackups(enabled: Boolean) = updateProtection { it.copy(backups = enabled) }

    private fun updateProtection(change: (ProtectFlags) -> ProtectFlags) {
        val s = _state.value.settings
        val next = change(ProtectFlags(s.protectMoney, s.protectMemory, s.protectInvoices, s.protectBackups))
        viewModelScope.launch {
            settings.setAreaProtection(next.money, next.memory, next.invoices, next.backups)
        }
    }

    /** The cross-app tier's master opt-in. A plain settings write, no rule (§4 toggle exception). */
    fun setCrossAppAutomations(enabled: Boolean) {
        viewModelScope.launch { settings.setCrossAppAutomationsEnabled(enabled) }
    }

    // --- DATA: erase everything, behind a confirmation ---

    fun askEraseData() {
        _state.update { it.copy(confirmingEraseData = true) }
    }

    fun cancelEraseData() {
        _state.update { it.copy(confirmingEraseData = false) }
    }

    /** By the time this runs Abu has confirmed losing everything on this phone (§4). */
    fun confirmEraseData() {
        _state.update { it.copy(confirmingEraseData = false, erasingData = true, error = null) }
        viewModelScope.launch {
            val failed = eraseAllData() is AppResult.Failure
            _state.update {
                it.copy(
                    erasingData = false,
                    error = if (failed) it.words().eraseDataFailed else null,
                )
            }
        }
    }

    /**
     * Turning a brief on or off writes it through and re-arms in the same breath, so the alarms
     * Android is holding always match what the screen says.
     */
    fun setBriefEnabled(kind: BriefKind, enabled: Boolean) {
        viewModelScope.launch {
            when (kind) {
                BriefKind.MORNING -> briefs.setMorningEnabled(enabled)
                BriefKind.EVENING -> briefs.setEveningEnabled(enabled)
            }
            armBriefs()
        }
    }

    fun editBriefTime(kind: BriefKind) {
        _state.update { it.copy(editingBriefTime = kind) }
    }

    fun dismissBriefTimeEditor() {
        _state.update { it.copy(editingBriefTime = null) }
    }

    /** A new time takes effect now. Waiting for a reboot to honour it would be a defect. */
    fun setBriefTime(kind: BriefKind, time: LocalTime) {
        _state.update { it.copy(editingBriefTime = null) }
        viewModelScope.launch {
            when (kind) {
                BriefKind.MORNING -> briefs.setMorningAt(time)
                BriefKind.EVENING -> briefs.setEveningAt(time)
            }
            armBriefs()
        }
    }

    fun openGeminiEditor() {
        _state.update { it.copy(geminiEditorOpen = true, geminiKeyDraft = "", error = null) }
    }

    fun dismissGeminiEditor() {
        _state.update { it.copy(geminiEditorOpen = false, geminiKeyDraft = "") }
    }

    fun onGeminiKeyDraftChange(value: String) {
        _state.update { it.copy(geminiKeyDraft = value) }
    }

    /**
     * A key that cannot be sealed is not stored at all, so the store is asked afterwards whether
     * it actually holds one rather than the write being assumed to have worked.
     */
    fun saveGeminiKey() {
        val key = _state.value.geminiKeyDraft.trim()
        if (key.isEmpty() || _state.value.savingGeminiKey) return
        _state.update { it.copy(savingGeminiKey = true, error = null) }
        viewModelScope.launch {
            credentials.setApiKey(key)
            val stored = credentials.hasKey.first()
            _state.update {
                it.copy(
                    savingGeminiKey = false,
                    geminiEditorOpen = !stored,
                    geminiKeyDraft = "",
                    error = if (stored) null else it.words().geminiKeyRefused,
                )
            }
        }
    }

    fun removeGeminiKey() {
        viewModelScope.launch {
            credentials.setApiKey(null)
            _state.update { it.copy(geminiEditorOpen = false, geminiKeyDraft = "", error = null) }
        }
    }

    fun askClearHistory() {
        _state.update { it.copy(confirmingClearHistory = true) }
    }

    fun cancelClearHistory() {
        _state.update { it.copy(confirmingClearHistory = false) }
    }

    fun confirmClearHistory() {
        _state.update { it.copy(confirmingClearHistory = false, error = null) }
        viewModelScope.launch {
            if (conversation.clear() is AppResult.Failure) {
                _state.update { it.copy(error = it.words().conversationNotCleared) }
            }
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    /**
     * Armed from what storage now holds rather than from what the screen last drew, so a brief is
     * never scheduled for a time the write did not keep.
     *
     * A refusal is said out loud and the setting is left as Abu set it: flipping the switch back
     * under him would leave him unable to tell a failed alarm from a change he never made.
     */
    private suspend fun armBriefs() {
        if (briefScheduler.apply(briefs.current()) is AppResult.Failure) {
            _state.update {
                it.copy(
                    error = briefSettingsWording(it.settings.languageMode.displayLanguage())
                        .scheduleRefused,
                )
            }
        }
    }

    /** A minute-of-day, 0..1439, as a wall-clock [LocalTime] for formatting. */
    private fun minuteOfDay(minute: Int): LocalTime = LocalTime.of(minute / 60, minute % 60)

    /** Whatever Mira has to say here is said in the language the screen is currently drawn in. */
    private fun SettingsUiState.words(): SettingsWording =
        settingsWording(settings.languageMode.displayLanguage())

    private fun SettingsUiState.withBriefSchedule(
        schedule: BriefSchedule,
        language: SpokenLanguage,
    ): SettingsUiState = copy(
        briefSchedule = schedule,
        morningTimeLabel = times.timeLabel(schedule.morningAt, language),
        eveningTimeLabel = times.timeLabel(schedule.eveningAt, language),
        morningNextLabel = nextLabel(schedule.morningEnabled, schedule.morningAt, language),
        eveningNextLabel = nextLabel(schedule.eveningEnabled, schedule.eveningAt, language),
    )

    /** Null while the brief is off: there is no next one, and saying one would be a lie. */
    private fun nextLabel(enabled: Boolean, at: LocalTime, language: SpokenLanguage): String? {
        if (!enabled) return null
        return briefSettingsWording(language).nextOccurrence(times.momentLabel(nextOccurrence(at), language))
    }

    // --- OTA UPDATE (GITHUB RELEASES) ---

    fun checkForUpdates() {
        if (_state.value.otaState is OtaUpdateState.Checking || _state.value.otaState is OtaUpdateState.Downloading) return
        _state.update { it.copy(otaState = OtaUpdateState.Checking) }
        viewModelScope.launch {
            val result = checkAppUpdate(
                currentVersionCode = BuildConfig.VERSION_CODE.toLong(),
                currentVersionName = BuildConfig.VERSION_NAME,
            )
            when (result) {
                is AppResult.Success -> {
                    val update = result.value
                    if (update.hasUpdate) {
                        _state.update { it.copy(otaState = OtaUpdateState.Available(update)) }
                    } else {
                        _state.update { it.copy(otaState = OtaUpdateState.UpToDate(update.latestVersionName)) }
                    }
                }
                is AppResult.Failure -> {
                    val msg = result.error.detail ?: "আপডেট তথ্য লোড করা যায়নি"
                    _state.update { it.copy(otaState = OtaUpdateState.Error(msg)) }
                }
            }
        }
    }

    fun downloadAndInstallUpdate(update: AppUpdate, context: Context) {
        if (_state.value.otaState is OtaUpdateState.Downloading) return
        _state.update { it.copy(otaState = OtaUpdateState.Downloading(0f, update)) }

        viewModelScope.launch {
            val result = downloadAppUpdate(
                downloadUrl = update.apkDownloadUrl,
                targetFileName = update.apkFileName.ifBlank { "Aida-${update.latestVersionName}.apk" },
                onProgress = { progress ->
                    _state.update { current ->
                        if (current.otaState is OtaUpdateState.Downloading) {
                            current.copy(otaState = OtaUpdateState.Downloading(progress, update))
                        } else {
                            current
                        }
                    }
                },
            )
            when (result) {
                is AppResult.Success -> {
                    val file = result.value
                    _state.update { it.copy(otaState = OtaUpdateState.ReadyToInstall(file, update)) }
                    installDownloadedApk(file, context)
                }
                is AppResult.Failure -> {
                    val msg = result.error.detail ?: "ডাউনলোড সম্পন্ন করা যায়নি"
                    _state.update { it.copy(otaState = OtaUpdateState.Error(msg)) }
                }
            }
        }
    }

    fun installDownloadedApk(file: File, context: Context) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _state.update { it.copy(otaState = OtaUpdateState.Error("ইনস্টলার চালু করতে ব্যর্থ হয়েছে: ${e.message}")) }
        }
    }

    fun dismissOta() {
        _state.update { it.copy(otaState = OtaUpdateState.Idle) }
    }

    /**
     * The first [at] strictly after now, read from the injected clock rather than `Instant.now()`
     * so the line Abu reads is one a test can assert.
     *
     * Deliberately the same rule the scheduler arms by — strictly after, and compared as instants
     * so a clock change moves both the same way. A label that disagreed with the alarm by a day
     * would be worse than no label at all. The rule is stated twice because presentation may not
     * reach into `data`; if it earns a third home it belongs in `domain/brief`.
     */
    private fun nextOccurrence(at: LocalTime): Instant {
        val from = clock.now()
        val today = from.atZone(clock.zone()).toLocalDate()
        val todaysOccurrence = today.atTime(at).atZone(clock.zone()).toInstant()
        return if (todaysOccurrence.isAfter(from)) {
            todaysOccurrence
        } else {
            today.plusDays(1).atTime(at).atZone(clock.zone()).toInstant()
        }
    }
}

/** The stores the screen reads, arriving together so one emission redraws it once. */
private data class Loaded(
    val settings: MiraSettings,
    val voice: VoicePreferences,
    val hasGeminiKey: Boolean,
    val briefSchedule: BriefSchedule,
    val quota: CloudQuotaStatus,
)

/** The four notification switches, carried together so one is flipped without disturbing the rest. */
private data class NotifyFlags(
    val tasks: Boolean,
    val reminders: Boolean,
    val invoices: Boolean,
    val subscriptions: Boolean,
)

/** The four protected areas, flipped one at a time against the current set. */
private data class ProtectFlags(
    val money: Boolean,
    val memory: Boolean,
    val invoices: Boolean,
    val backups: Boolean,
)
