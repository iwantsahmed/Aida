package com.rayluxeo.aida.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.model.DateStyle
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import java.io.IOException
import java.time.DayOfWeek
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
    private val dispatchers: DispatcherProvider,
) : SettingsRepository {

    override val settings: Flow<MiraSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toSettings)

    override suspend fun current(): MiraSettings = withContext(dispatchers.io) { settings.first() }

    override suspend fun setLanguageMode(mode: LanguageMode) =
        editPreferences { it[MiraPreferenceKeys.LANGUAGE_MODE] = mode.name }

    override suspend fun setUserName(name: String) =
        editPreferences { it[MiraPreferenceKeys.USER_NAME] = name.trim() }

    override suspend fun setActivationSound(enabled: Boolean) =
        editPreferences { it[MiraPreferenceKeys.ACTIVATION_SOUND] = enabled }

    override suspend fun setHapticFeedback(enabled: Boolean) =
        editPreferences { it[MiraPreferenceKeys.HAPTIC_FEEDBACK] = enabled }

    override suspend fun setOnboardingComplete(complete: Boolean) =
        editPreferences { it[MiraPreferenceKeys.ONBOARDING_COMPLETE] = complete }

    override suspend fun setCloudAiEnabled(enabled: Boolean) =
        editPreferences { it[MiraPreferenceKeys.CLOUD_AI_ENABLED] = enabled }

    override suspend fun setDateStyle(style: DateStyle) =
        editPreferences { it[MiraPreferenceKeys.DATE_STYLE] = style.name }

    override suspend fun setFirstDayOfWeek(day: DayOfWeek) =
        editPreferences { it[MiraPreferenceKeys.FIRST_DAY_OF_WEEK] = day.name }

    override suspend fun setConversationStyle(style: ConversationStyle) =
        editPreferences { it[MiraPreferenceKeys.CONVERSATION_STYLE] = style.name }

    override suspend fun setNotificationCategories(
        tasks: Boolean,
        reminders: Boolean,
        invoices: Boolean,
        subscriptions: Boolean,
    ) = editPreferences {
        it[MiraPreferenceKeys.NOTIFY_TASKS] = tasks
        it[MiraPreferenceKeys.NOTIFY_REMINDERS] = reminders
        it[MiraPreferenceKeys.NOTIFY_INVOICES] = invoices
        it[MiraPreferenceKeys.NOTIFY_SUBSCRIPTIONS] = subscriptions
    }

    override suspend fun setQuietHours(enabled: Boolean, startMinute: Int, endMinute: Int) =
        editPreferences {
            it[MiraPreferenceKeys.QUIET_HOURS_ENABLED] = enabled
            it[MiraPreferenceKeys.QUIET_HOURS_START] = startMinute
            it[MiraPreferenceKeys.QUIET_HOURS_END] = endMinute
        }

    override suspend fun setAppLock(enabled: Boolean, timeoutSeconds: Int, lockWhenBackgrounded: Boolean) =
        editPreferences {
            it[MiraPreferenceKeys.APP_LOCK_ENABLED] = enabled
            it[MiraPreferenceKeys.APP_LOCK_TIMEOUT_SECONDS] = timeoutSeconds
            it[MiraPreferenceKeys.LOCK_WHEN_BACKGROUNDED] = lockWhenBackgrounded
        }

    override suspend fun setAreaProtection(money: Boolean, memory: Boolean, invoices: Boolean, backups: Boolean) =
        editPreferences {
            it[MiraPreferenceKeys.PROTECT_MONEY] = money
            it[MiraPreferenceKeys.PROTECT_MEMORY] = memory
            it[MiraPreferenceKeys.PROTECT_INVOICES] = invoices
            it[MiraPreferenceKeys.PROTECT_BACKUPS] = backups
        }

    override suspend fun setCrossAppAutomationsEnabled(enabled: Boolean) =
        editPreferences { it[MiraPreferenceKeys.CROSS_APP_AUTOMATIONS_ENABLED] = enabled }

    private suspend fun editPreferences(block: (MutablePreferences) -> Unit) {
        withContext(dispatchers.io) { store.edit { preferences -> block(preferences) } }
    }

    private fun toSettings(preferences: Preferences): MiraSettings {
        val defaults = MiraSettings()
        return MiraSettings(
            userName = preferences[MiraPreferenceKeys.USER_NAME] ?: defaults.userName,
            languageMode = preferences[MiraPreferenceKeys.LANGUAGE_MODE]
                ?.let { stored -> LanguageMode.entries.firstOrNull { it.name == stored } }
                ?: defaults.languageMode,
            activationSound = preferences[MiraPreferenceKeys.ACTIVATION_SOUND] ?: defaults.activationSound,
            hapticFeedback = preferences[MiraPreferenceKeys.HAPTIC_FEEDBACK] ?: defaults.hapticFeedback,
            onboardingComplete = preferences[MiraPreferenceKeys.ONBOARDING_COMPLETE] ?: defaults.onboardingComplete,
            geminiEnabled = preferences[MiraPreferenceKeys.GEMINI_SEALED_KEY] != null &&
                (preferences[MiraPreferenceKeys.CLOUD_AI_ENABLED] ?: defaults.cloudAiEnabled),
            cloudAiEnabled = preferences[MiraPreferenceKeys.CLOUD_AI_ENABLED] ?: defaults.cloudAiEnabled,
            dateStyle = preferences[MiraPreferenceKeys.DATE_STYLE]
                ?.let { stored -> DateStyle.entries.firstOrNull { it.name == stored } }
                ?: defaults.dateStyle,
            firstDayOfWeek = preferences[MiraPreferenceKeys.FIRST_DAY_OF_WEEK]
                ?.let { stored -> DayOfWeek.entries.firstOrNull { it.name == stored } }
                ?: defaults.firstDayOfWeek,
            conversationStyle = preferences[MiraPreferenceKeys.CONVERSATION_STYLE]
                ?.let { stored -> ConversationStyle.entries.firstOrNull { it.name == stored } }
                ?: defaults.conversationStyle,
            quietHoursEnabled = preferences[MiraPreferenceKeys.QUIET_HOURS_ENABLED] ?: defaults.quietHoursEnabled,
            quietHoursStartMinute = preferences[MiraPreferenceKeys.QUIET_HOURS_START] ?: defaults.quietHoursStartMinute,
            quietHoursEndMinute = preferences[MiraPreferenceKeys.QUIET_HOURS_END] ?: defaults.quietHoursEndMinute,
            notifyTasks = preferences[MiraPreferenceKeys.NOTIFY_TASKS] ?: defaults.notifyTasks,
            notifyReminders = preferences[MiraPreferenceKeys.NOTIFY_REMINDERS] ?: defaults.notifyReminders,
            notifyInvoices = preferences[MiraPreferenceKeys.NOTIFY_INVOICES] ?: defaults.notifyInvoices,
            notifySubscriptions = preferences[MiraPreferenceKeys.NOTIFY_SUBSCRIPTIONS] ?: defaults.notifySubscriptions,
            appLockEnabled = preferences[MiraPreferenceKeys.APP_LOCK_ENABLED] ?: defaults.appLockEnabled,
            appLockTimeoutSeconds = preferences[MiraPreferenceKeys.APP_LOCK_TIMEOUT_SECONDS] ?: defaults.appLockTimeoutSeconds,
            lockWhenBackgrounded = preferences[MiraPreferenceKeys.LOCK_WHEN_BACKGROUNDED] ?: defaults.lockWhenBackgrounded,
            protectMoney = preferences[MiraPreferenceKeys.PROTECT_MONEY] ?: defaults.protectMoney,
            protectMemory = preferences[MiraPreferenceKeys.PROTECT_MEMORY] ?: defaults.protectMemory,
            protectInvoices = preferences[MiraPreferenceKeys.PROTECT_INVOICES] ?: defaults.protectInvoices,
            protectBackups = preferences[MiraPreferenceKeys.PROTECT_BACKUPS] ?: defaults.protectBackups,
            crossAppAutomationsEnabled = preferences[MiraPreferenceKeys.CROSS_APP_AUTOMATIONS_ENABLED]
                ?: defaults.crossAppAutomationsEnabled,
        )
    }
}
