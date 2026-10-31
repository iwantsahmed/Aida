package com.rayluxeo.aida.ui.onboarding

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.repository.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Settings in memory.
 *
 * [stored] is what the tests assert against, and [completionWrites] exists for one reason: the
 * finish step must reach storage exactly once however many times the button is pressed.
 */
class FakeSettingsRepository(initial: MiraSettings = MiraSettings()) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<MiraSettings> = _settings.asStateFlow()

    val stored: MiraSettings get() = _settings.value

    var completionWrites = 0
        private set

    override suspend fun current(): MiraSettings = _settings.value

    override suspend fun setLanguageMode(mode: LanguageMode) {
        _settings.update { it.copy(languageMode = mode) }
    }

    override suspend fun setUserName(name: String) {
        _settings.update { it.copy(userName = name) }
    }

    override suspend fun setActivationSound(enabled: Boolean) {
        _settings.update { it.copy(activationSound = enabled) }
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        _settings.update { it.copy(hapticFeedback = enabled) }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        completionWrites++
        _settings.update { it.copy(onboardingComplete = complete) }
    }
}

class FakeUserProfileRepository(initial: UserProfile? = null) : UserProfileRepository {
    private val _profile = MutableStateFlow(initial)
    override fun observe(): Flow<UserProfile?> = _profile.asStateFlow()
    override suspend fun current(): AppResult<UserProfile?> = AppResult.Success(_profile.value)
    override suspend fun save(profile: UserProfile): AppResult<UserProfile> {
        _profile.value = profile
        return AppResult.Success(profile)
    }
}
