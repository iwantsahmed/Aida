package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.model.DateStyle
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow

/**
 * Everything Abu can set that is not about how Mira sounds.
 *
 * [geminiEnabled] is derived, not stored on its own: it is true only when a sealed key exists AND
 * [cloudAiEnabled] is on, so Abu can keep his key and still refuse every cloud call with one
 * switch. The on-device engine answers everything either way (§3).
 */
data class MiraSettings(
    val userName: String = "",
    val languageMode: LanguageMode = LanguageMode.AUTO,
    val activationSound: Boolean = true,
    val hapticFeedback: Boolean = true,
    val onboardingComplete: Boolean = false,
    val geminiEnabled: Boolean = false,
    val cloudAiEnabled: Boolean = true,
    val dateStyle: DateStyle = DateStyle.DAY_MONTH_YEAR,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val conversationStyle: ConversationStyle = ConversationStyle.BALANCED,
    val quietHoursEnabled: Boolean = false,
    val quietHoursStartMinute: Int = 22 * 60,
    val quietHoursEndMinute: Int = 7 * 60,
    val notifyTasks: Boolean = true,
    val notifyReminders: Boolean = true,
    val notifyInvoices: Boolean = true,
    val notifySubscriptions: Boolean = true,
    val appLockEnabled: Boolean = false,
    val appLockTimeoutSeconds: Int = 0,
    val lockWhenBackgrounded: Boolean = true,
    val protectMoney: Boolean = false,
    val protectMemory: Boolean = false,
    val protectInvoices: Boolean = false,
    val protectBackups: Boolean = false,
    /**
     * The master switch for the cross-app automation tier (the accessibility service and the
     * notification listener). OFF by default and never turned on except by Abu on its own settings
     * row — the whole tier is opt-in, and Mira is fully functional without it.
     */
    val crossAppAutomationsEnabled: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<MiraSettings>
    suspend fun current(): MiraSettings
    suspend fun setLanguageMode(mode: LanguageMode)
    suspend fun setUserName(name: String) {}
    suspend fun setActivationSound(enabled: Boolean)
    suspend fun setHapticFeedback(enabled: Boolean)
    suspend fun setOnboardingComplete(complete: Boolean)
    suspend fun setCloudAiEnabled(enabled: Boolean) {}
    suspend fun setDateStyle(style: DateStyle) {}
    suspend fun setFirstDayOfWeek(day: DayOfWeek) {}
    suspend fun setConversationStyle(style: ConversationStyle) {}
    suspend fun setQuietHours(enabled: Boolean, startMinute: Int = 22 * 60, endMinute: Int = 7 * 60) {}
    suspend fun setNotificationCategories(
        tasks: Boolean,
        reminders: Boolean,
        invoices: Boolean,
        subscriptions: Boolean,
    ) {}
    suspend fun setAppLock(enabled: Boolean, timeoutSeconds: Int = 0, lockWhenBackgrounded: Boolean = true) {}
    suspend fun setAreaProtection(money: Boolean, memory: Boolean, invoices: Boolean, backups: Boolean) {}
    suspend fun setCrossAppAutomationsEnabled(enabled: Boolean) {}
}

/**
 * The Gemini key is held apart from ordinary settings because it is encrypted with an
 * Android Keystore key and must never be logged, exported or backed up.
 */
interface GeminiCredentialStore {
    val hasKey: Flow<Boolean>
    suspend fun apiKey(): String?
    suspend fun setApiKey(key: String?)
}
