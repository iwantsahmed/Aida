package com.rayluxeo.aida.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private const val SETTINGS_FILE_NAME = "mira_settings"

/**
 * The single DataStore instance for this process.
 *
 * DataStore permits exactly one active instance per file and throws if a second is opened. A Hilt
 * `@Singleton` is only unique per component, which is enough in production but not under
 * instrumentation, where every test class builds a fresh component and the second one to touch
 * settings died with "There are multiple DataStores active for the same file". This delegate is
 * keyed on the process, so there is genuinely only ever one.
 */
private val Context.miraSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_FILE_NAME,
    // A corrupted file resets to defaults rather than throwing on every read: settings are
    // reconstructible, and Mira refusing to start because a preferences file was truncated
    // would be the worse failure.
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
)

/** Owns the one Preferences DataStore in the app; everything else injects the [store] it exposes. */
@Singleton
class MiraPreferences @Inject constructor(@ApplicationContext context: Context) {

    val store: DataStore<Preferences> = context.miraSettingsStore
}

/**
 * Preference keys are strings on disk forever; renaming one silently forgets Abu's choice, so
 * they live together where that cost is visible.
 *
 * The voice keys below are older than the port that now reads them. When the voice configuration
 * moved out of `MiraSettings` into `VoicePreferences` the Kotlin names changed — `englishVoiceName`
 * became `englishVoiceId`, `spokenResponses` became `spokenResponsesEnabled` — and every one of
 * these strings deliberately did not, because Abu had already chosen a voice and a rate on this
 * phone. A rename here is not a refactor; it is his setting quietly reverting to the default.
 */
internal object MiraPreferenceKeys {
    val USER_NAME = stringPreferencesKey("user_name")
    val LANGUAGE_MODE = stringPreferencesKey("language_mode")
    val ACTIVATION_SOUND = booleanPreferencesKey("activation_sound")
    val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
    val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
    val QUIET_HOURS_START = intPreferencesKey("quiet_hours_start_minute")
    val QUIET_HOURS_END = intPreferencesKey("quiet_hours_end_minute")
    val APP_LOCK_ENABLED = booleanPreferencesKey("app_lock_enabled")
    val APP_LOCK_TIMEOUT_SECONDS = intPreferencesKey("app_lock_timeout_seconds")
    val LOCK_WHEN_BACKGROUNDED = booleanPreferencesKey("lock_when_backgrounded")
    val PROTECT_MONEY = booleanPreferencesKey("protect_money")
    val PROTECT_MEMORY = booleanPreferencesKey("protect_memory")
    val PROTECT_INVOICES = booleanPreferencesKey("protect_invoices")
    val PROTECT_BACKUPS = booleanPreferencesKey("protect_backups")

    /** The cross-app automation tier's master switch. Off by default; the on-disk name is permanent. */
    val CROSS_APP_AUTOMATIONS_ENABLED = booleanPreferencesKey("cross_app_automations_enabled")

    /**
     * A soft kill-switch for the cloud. Separate from the key's presence so Abu can keep a saved
     * Gemini key and still turn every cloud call off; "Gemini is enabled" is the AND of the two.
     */
    val CLOUD_AI_ENABLED = booleanPreferencesKey("cloud_ai_enabled")

    /** How dates are written app-wide, and which day a week starts on (stores the enum name). */
    val DATE_STYLE = stringPreferencesKey("date_style")
    val FIRST_DAY_OF_WEEK = stringPreferencesKey("first_day_of_week")

    /** How much Mira says around an answer (stores the [com.rayluxeo.aida.domain.model.ConversationStyle] name). */
    val CONVERSATION_STYLE = stringPreferencesKey("conversation_style")

    /**
     * Which kinds of notification Mira may raise. Each gates the single reminder-posting path by
     * the reminder's kind; all default on, so turning one off is always Abu's own choice.
     */
    val NOTIFY_TASKS = booleanPreferencesKey("notify_tasks")
    val NOTIFY_REMINDERS = booleanPreferencesKey("notify_reminders")
    val NOTIFY_INVOICES = booleanPreferencesKey("notify_invoices")
    val NOTIFY_SUBSCRIPTIONS = booleanPreferencesKey("notify_subscriptions")

    // How Mira sounds. Read and written by VoicePreferenceRepositoryImpl.
    val SPOKEN_RESPONSES = booleanPreferencesKey("spoken_responses")
    val VOICE_ENGINE = stringPreferencesKey("voice_engine")
    val VOICE_ENGLISH_NAME = stringPreferencesKey("voice_english_name")
    val VOICE_BENGALI_NAME = stringPreferencesKey("voice_bengali_name")
    val SPEECH_RATE = floatPreferencesKey("voice_speech_rate")
    val VOICE_PITCH = floatPreferencesKey("voice_pitch")
    val VOICE_SETUP_COMPLETE = booleanPreferencesKey("voice_setup_complete")

    /**
     * The two scheduled briefs. Both default off, so the first notification Mira ever sends
     * unasked is one Abu switched on himself.
     *
     * A time is stored as minutes since midnight rather than as ISO-8601 text. An ISO string
     * invites an offset or a zone onto the end of it, and a brief is a wall-clock appointment:
     * "eight in the morning" must stay eight in the morning when Abu flies to Dubai, not
     * follow the zone that was current when he chose it. The zone comes from `MiraClock` at
     * the moment the alarm is armed.
     */
    val MORNING_BRIEF_ENABLED = booleanPreferencesKey("morning_brief_enabled")
    val MORNING_BRIEF_MINUTES = intPreferencesKey("morning_brief_minutes")
    val EVENING_BRIEF_ENABLED = booleanPreferencesKey("evening_brief_enabled")
    val EVENING_BRIEF_MINUTES = intPreferencesKey("evening_brief_minutes")

    /** Ciphertext, never the key itself. Its mere presence is what "Gemini is enabled" means. */
    val GEMINI_SEALED_KEY = stringPreferencesKey("gemini_api_key_sealed")
}
