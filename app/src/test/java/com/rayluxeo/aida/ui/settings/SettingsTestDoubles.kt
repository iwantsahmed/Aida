package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.location.CurrentPlaceFinder
import com.rayluxeo.aida.domain.location.PlaceFix
import com.rayluxeo.aida.domain.model.ConversationTurn
import com.rayluxeo.aida.domain.model.Reminder
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.GeminiCredentialStore
import com.rayluxeo.aida.domain.repository.MiraSettings
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.scheduler.ReminderScheduler
import com.rayluxeo.aida.voice.TtsEngineInfo
import com.rayluxeo.aida.voice.TtsVoiceInfo
import com.rayluxeo.aida.voice.VoicePersonaManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Settings, in memory. [stored] is what the tests assert against. */
class FakeSettingsRepository(initial: MiraSettings = MiraSettings()) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<MiraSettings> = _settings.asStateFlow()

    val stored: MiraSettings get() = _settings.value

    override suspend fun current(): MiraSettings = _settings.value

    override suspend fun setLanguageMode(mode: LanguageMode) {
        _settings.update { it.copy(languageMode = mode) }
    }

    override suspend fun setActivationSound(enabled: Boolean) {
        _settings.update { it.copy(activationSound = enabled) }
    }

    override suspend fun setHapticFeedback(enabled: Boolean) {
        _settings.update { it.copy(hapticFeedback = enabled) }
    }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        _settings.update { it.copy(onboardingComplete = complete) }
    }

}

/**
 * [sealingFails] reproduces the real store's contract: a key that cannot be encrypted is not kept
 * at all, rather than written in the clear.
 */
class FakeGeminiCredentialStore : GeminiCredentialStore {

    private val sealed = MutableStateFlow<String?>(null)
    var sealingFails = false

    val heldKey: String? get() = sealed.value

    override val hasKey: Flow<Boolean> = sealed.map { it != null }

    override suspend fun apiKey(): String? = sealed.value

    override suspend fun setApiKey(key: String?) {
        val cleaned = key?.trim()?.takeIf { it.isNotEmpty() }
        sealed.value = if (sealingFails) null else cleaned
    }
}

class RecordingConversationRepository : ConversationRepository {

    var clears = 0
    var failClear = false

    override fun observeRecent(limit: Int): Flow<List<ConversationTurn>> = flowOf(emptyList())

    override suspend fun append(turn: ConversationTurn): AppResult<ConversationTurn> =
        AppResult.Success(turn)

    override suspend fun clear(): AppResult<Unit> {
        clears++
        return if (failClear) failure(MiraErrorCode.STORAGE, "clear refused") else AppResult.Success(Unit)
    }
}

class FakeReminderScheduler : ReminderScheduler {
    var exactAllowed = true
    override fun canScheduleExact(): Boolean = exactAllowed
    override suspend fun schedule(reminder: Reminder): AppResult<Unit> = AppResult.Success(Unit)
    override suspend fun cancel(reminderId: Long): AppResult<Unit> = AppResult.Success(Unit)
}

/** A device's voice catalogue, keyed by engine package — null meaning the system default. */
class FakeVoicePersonaManager : VoicePersonaManager {

    var installedEngines: List<TtsEngineInfo> = emptyList()
    var catalogue: Map<String?, Map<SpokenLanguage, List<TtsVoiceInfo>>> = emptyMap()
    var previewResult: AppResult<Unit> = AppResult.Success(Unit)

    val previewed = mutableListOf<String>()
    var releases = 0

    override suspend fun engines(): List<TtsEngineInfo> = installedEngines

    override suspend fun voices(enginePackage: String?, language: SpokenLanguage): List<TtsVoiceInfo> =
        catalogue[enginePackage]?.get(language).orEmpty()

    override suspend fun preview(
        enginePackage: String?,
        voiceName: String,
        language: SpokenLanguage,
        rate: Float,
        pitch: Float,
    ): AppResult<Unit> {
        previewed += voiceName
        return previewResult
    }


    override fun release() {
        releases++
    }
}

/**
 * Abu's named places, in memory.
 *
 * [observeAll] sorts by label because the real port promises alphabetical order and the Places
 * screen renders whatever arrives: a fake that returned insertion order would let a list that is
 * shuffled on the device pass here.
 */
class FakeSavedPlaceRepository : SavedPlaceRepository {

    private val rows = MutableStateFlow<List<SavedPlace>>(emptyList())
    private var nextId = 1L

    /** What is actually on disk, so a test can tell a rendered row from a stored one. */
    val stored: List<SavedPlace> get() = rows.value

    override fun observeAll(): Flow<List<SavedPlace>> =
        rows.map { places -> places.sortedBy { it.label } }

    override suspend fun all(): AppResult<List<SavedPlace>> = AppResult.Success(stored)

    override suspend fun byLabelKey(labelKey: String): AppResult<SavedPlace?> =
        AppResult.Success(rows.value.firstOrNull { it.labelKey == labelKey })

    override suspend fun byId(id: Long): AppResult<SavedPlace> =
        rows.value.firstOrNull { it.id == id }?.let { AppResult.Success(it) }
            ?: failure(MiraErrorCode.NOT_FOUND, "No place $id")

    /** Mirrors the real unique index: two places may not answer to the same name. */
    override suspend fun create(place: SavedPlace): AppResult<SavedPlace> {
        if (rows.value.any { it.labelKey == place.labelKey }) {
            return failure(MiraErrorCode.VALIDATION, "${place.labelKey} is taken")
        }
        val saved = place.copy(id = nextId++)
        rows.update { it + saved }
        return AppResult.Success(saved)
    }

    override suspend fun update(place: SavedPlace): AppResult<SavedPlace> {
        rows.update { places -> places.map { if (it.id == place.id) place else it } }
        return AppResult.Success(place)
    }

    override suspend fun delete(id: Long): AppResult<Unit> {
        rows.update { places -> places.filterNot { it.id == id } }
        return AppResult.Success(Unit)
    }
}

/**
 * The platform's answer about where Abu is standing.
 *
 * [asked] is the assertion that matters as much as the coordinate: a name Mira cannot store is no
 * reason to wake the radio, so a refused save must leave this at zero.
 */
class FakeCurrentPlaceFinder : CurrentPlaceFinder {

    var fix: PlaceFix = PlaceFix.Found(
        latitude = DHAKA_LATITUDE,
        longitude = DHAKA_LONGITUDE,
        accuracyMeters = 12f,
    )

    var asked = 0

    override suspend fun here(): PlaceFix {
        asked++
        return fix
    }

    companion object {
        const val DHAKA_LATITUDE = 23.780753
        const val DHAKA_LONGITUDE = 90.407448
    }
}

/** A voice row shaped like the ones a real engine reports. */
fun testVoice(name: String, localeTag: String): TtsVoiceInfo = TtsVoiceInfo(
    name = name,
    localeTag = localeTag,
    displayLabel = name,
    quality = 400,
    latency = 200,
    requiresNetwork = false,
    isInstalled = true,
)
