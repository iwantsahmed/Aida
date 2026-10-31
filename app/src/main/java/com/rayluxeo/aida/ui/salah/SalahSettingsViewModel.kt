package com.rayluxeo.aida.ui.salah

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.salah.BuiltInCities
import com.rayluxeo.aida.domain.salah.CalculationMethod
import com.rayluxeo.aida.domain.salah.HighLatitudeRule
import com.rayluxeo.aida.domain.salah.Madhab
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerAdjustments
import com.rayluxeo.aida.domain.salah.PrayerCity
import com.rayluxeo.aida.domain.salah.PrayerLocation
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.SalahSettingsRepository
import com.rayluxeo.aida.domain.salah.TahajjudReminderMode
import com.rayluxeo.aida.domain.usecase.ResetSalahTrackingUseCase
import com.rayluxeo.aida.domain.usecase.SaveCurrentLocationUseCase
import com.rayluxeo.aida.domain.usecase.SetPrayerCityUseCase
import com.rayluxeo.aida.domain.usecase.UpdateSalahSettingsUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The Salah settings screen's state holder. Every control writes straight through: a change builds
 * the next [SalahSettings] whole and hands it to [UpdateSalahSettingsUseCase], which saves it and
 * re-arms the prayer alarms in one step — so a change to the method, a minute correction or a
 * notification switch all take effect on the schedule immediately, never on the next reboot (§4,
 * §5: anything that re-arms alarms goes through a use case, never the repository alone).
 *
 * The screen never keeps its own copy of the configuration. It reads back the repository's [Flow],
 * so what Abu sees is always what Mira is computing from — the write-through model Settings uses. A
 * failure to save or to locate is said plainly as a [SalahSettingsNotice], never swallowed and never
 * shown as an exception.
 */
@HiltViewModel
class SalahSettingsViewModel @Inject constructor(
    private val salahSettings: SalahSettingsRepository,
    private val settings: SettingsRepository,
    private val updateSalahSettings: UpdateSalahSettingsUseCase,
    private val saveCurrentLocation: SaveCurrentLocationUseCase,
    private val resetSalahTracking: ResetSalahTrackingUseCase,
    private val setPrayerCity: SetPrayerCityUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SalahSettingsUiState())
    val state: StateFlow<SalahSettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(salahSettings.settings, settings.settings) { salah, mira ->
                salah to mira.languageMode.displayLanguage()
            }.collect { (salah, language) ->
                _state.update { it.copy(loading = false, settings = salah, language = language) }
            }
        }
    }

    // ----------------------------------------------------------------- LOCATION

    fun onCityLabelDraftChange(value: String) {
        _state.update { it.copy(cityLabelDraft = value) }
    }

    fun onCitySearchQueryChange(query: String) {
        _state.update { it.copy(citySearchQuery = query) }
    }

    fun openCityPicker() {
        _state.update { it.copy(activeSheet = SalahSheet.CITY_PICKER, citySearchQuery = "") }
    }

    /** Sets the location immediately from a known city without needing GPS or location tracking. */
    fun selectCity(city: PrayerCity) {
        _state.update { it.copy(activeSheet = null, citySearchQuery = "", notice = null) }
        viewModelScope.launch {
            val useBengali = _state.value.language == SpokenLanguage.BENGALI
            if (setPrayerCity(city, useBengali) is AppResult.Failure) {
                _state.update { it.copy(notice = SalahSettingsNotice.SAVE_FAILED) }
            }
        }
    }

    /** If user typed a custom city name in the input box, applies it. */
    fun applyCityLabelAsLocation() {
        val label = _state.value.cityLabelDraft.trim()
        if (label.isBlank()) return
        val matchedCity = BuiltInCities.findByName(label)
        if (matchedCity != null) {
            selectCity(matchedCity)
        } else {
            val location = PrayerLocation(
                latitude = BuiltInCities.DEFAULT.latitude,
                longitude = BuiltInCities.DEFAULT.longitude,
                cityLabel = label,
                zoneId = ZoneId.systemDefault(),
            )
            update { it.copy(location = location) }
        }
    }

    /** Open the inline name editor for an already-saved place, seeded with its current name. */
    fun beginEditingName() {
        _state.update { it.copy(editingCityLabel = true, cityLabelDraft = it.settings.location?.cityLabel.orEmpty()) }
    }

    fun cancelEditingName() {
        _state.update { it.copy(editingCityLabel = false) }
    }

    /** Rename the saved place. Only the label changes, so this is a plain write-through. */
    fun saveCityLabel() {
        val location = _state.value.settings.location ?: return
        val label = _state.value.cityLabelDraft.trim()
        _state.update { it.copy(editingCityLabel = false) }
        update { it.copy(location = location.copy(cityLabel = label)) }
    }

    /**
     * Takes one location fix and saves it under the name Abu typed. The fix and the re-arm happen in
     * the use case; a missing grant or a disabled radio comes back as a failure the screen turns into
     * the location notice rather than a claim that a place was saved.
     */
    fun useCurrentLocation() {
        if (_state.value.locatingNow) return
        val label = _state.value.cityLabelDraft.trim()
            .ifEmpty { _state.value.settings.location?.cityLabel.orEmpty() }
        _state.update { it.copy(locatingNow = true, notice = null) }
        viewModelScope.launch {
            val notice = when (saveCurrentLocation(label)) {
                is AppResult.Success -> null
                is AppResult.Failure -> SalahSettingsNotice.LOCATION_UNAVAILABLE
            }
            _state.update { it.copy(locatingNow = false, editingCityLabel = false, notice = notice) }
        }
    }

    // ------------------------------------------------------- PRAYER CALCULATION

    fun setMethod(method: CalculationMethod) {
        _state.update { it.copy(activeSheet = null) }
        update { it.copy(parameters = it.parameters.copy(method = method)) }
    }

    fun setMadhab(madhab: Madhab) =
        update { it.copy(parameters = it.parameters.copy(madhab = madhab)) }

    fun setHighLatitudeRule(rule: HighLatitudeRule) {
        _state.update { it.copy(activeSheet = null) }
        update { it.copy(parameters = it.parameters.copy(highLatitudeRule = rule)) }
    }

    /** Only reachable while the method is CUSTOM; steps the Fajr depression angle by half a degree. */
    fun nudgeFajrAngle(deltaDegrees: Double) = update {
        val next = roundToHalf(it.parameters.effectiveFajrAngle + deltaDegrees).coerceIn(ANGLE_MIN, ANGLE_MAX)
        it.copy(parameters = it.parameters.copy(customFajrAngle = next))
    }

    fun nudgeIshaAngle(deltaDegrees: Double) = update {
        val next = roundToHalf(it.parameters.effectiveIshaAngle + deltaDegrees).coerceIn(ANGLE_MIN, ANGLE_MAX)
        it.copy(parameters = it.parameters.copy(customIshaAngle = next))
    }

    // ------------------------------------------------------- MANUAL ADJUSTMENTS

    /** Shift one obligatory prayer's time by [deltaMinutes], clamped to a sane window. */
    fun nudgeAdjustment(prayer: Prayer, deltaMinutes: Int) = update {
        val a = it.parameters.adjustments
        fun clamp(current: Int) = (current + deltaMinutes).coerceIn(ADJUSTMENT_MIN, ADJUSTMENT_MAX)
        val next = when (prayer) {
            Prayer.FAJR -> a.copy(fajr = clamp(a.fajr))
            Prayer.DHUHR -> a.copy(dhuhr = clamp(a.dhuhr))
            Prayer.ASR -> a.copy(asr = clamp(a.asr))
            Prayer.MAGHRIB -> a.copy(maghrib = clamp(a.maghrib))
            Prayer.ISHA -> a.copy(isha = clamp(a.isha))
            Prayer.TAHAJJUD -> a // Tahajjud is not one of the five and carries no minute correction.
        }
        it.copy(parameters = it.parameters.copy(adjustments = next))
    }

    // ----------------------------------------------------------- NOTIFICATIONS

    fun setPrayerNotifications(enabled: Boolean) =
        update { it.copy(notifications = it.notifications.copy(prayerNotificationsEnabled = enabled)) }

    fun setPerPrayerNotify(prayer: Prayer, enabled: Boolean) = update {
        it.copy(notifications = it.notifications.copy(perPrayerNotify = it.notifications.perPrayerNotify + (prayer to enabled)))
    }

    // ------------------------------------------------------------ CHECK-INS

    fun setCheckInEnabled(enabled: Boolean) =
        update { it.copy(notifications = it.notifications.copy(checkInEnabled = enabled)) }

    fun setPerPrayerCheckIn(prayer: Prayer, enabled: Boolean) = update {
        it.copy(notifications = it.notifications.copy(perPrayerCheckIn = it.notifications.perPrayerCheckIn + (prayer to enabled)))
    }

    fun setFollowUpDelay(minutes: Int) {
        _state.update { it.copy(activeSheet = null) }
        update { it.copy(notifications = it.notifications.copy(defaultAskDelayMinutes = minutes)) }
    }

    // ---------------------------------------------------------------- TAHAJJUD

    fun setTahajjudReminder(enabled: Boolean) =
        update { it.copy(tahajjud = it.tahajjud.copy(reminderEnabled = enabled)) }

    fun setTahajjudMode(mode: TahajjudReminderMode) {
        _state.update { it.copy(activeSheet = null) }
        update { it.copy(tahajjud = it.tahajjud.copy(mode = mode)) }
    }

    fun nudgeTahajjudCustomMinutes(deltaMinutes: Int) = update {
        val next = (it.tahajjud.customMinutesIntoLastThird + deltaMinutes).coerceIn(0, TAHAJJUD_CUSTOM_MAX)
        it.copy(tahajjud = it.tahajjud.copy(customMinutesIntoLastThird = next))
    }

    // ------------------------------------------------------------------- SHEETS

    fun openSheet(sheet: SalahSheet) {
        _state.update { it.copy(activeSheet = sheet) }
    }

    fun dismissSheet() {
        _state.update { it.copy(activeSheet = null) }
    }

    // -------------------------------------------------------------------- RESET

    fun askReset() {
        _state.update { it.copy(confirmingReset = true) }
    }

    fun cancelReset() {
        _state.update { it.copy(confirmingReset = false) }
    }

    /** By the time this runs Abu has confirmed losing his whole prayer log; his settings are kept. */
    fun confirmReset() {
        _state.update { it.copy(confirmingReset = false, resetting = true, notice = null) }
        viewModelScope.launch {
            val failed = resetSalahTracking() is AppResult.Failure
            _state.update {
                it.copy(resetting = false, notice = if (failed) SalahSettingsNotice.RESET_FAILED else null)
            }
        }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /**
     * The single write path for a configuration change: build the next value from what is on screen,
     * save-and-re-arm through the use case, and raise a notice if the write did not land. The screen
     * is redrawn by the repository's flow, not from here, so a failed write never looks like a change.
     */
    private fun update(transform: (SalahSettings) -> SalahSettings) {
        val next = transform(_state.value.settings)
        viewModelScope.launch {
            if (updateSalahSettings(next) is AppResult.Failure) {
                _state.update { it.copy(notice = SalahSettingsNotice.SAVE_FAILED) }
            }
        }
    }

    private fun roundToHalf(value: Double): Double = (value * 2).roundToInt() / 2.0

    private companion object {
        const val ANGLE_MIN = 10.0
        const val ANGLE_MAX = 21.0
        const val ADJUSTMENT_MIN = -30
        const val ADJUSTMENT_MAX = 30
        const val TAHAJJUD_CUSTOM_MAX = 180
    }
}

/** Shared with the screen: the follow-up-delay choices, in minutes. */
internal val SALAH_FOLLOW_UP_DELAYS = listOf(10, 15, 20, 30, 45, 60)

/** Shared with the screen: PrayerAdjustments read generically, so a per-prayer row needs no `when`. */
internal fun PrayerAdjustments.minutesFor(prayer: Prayer): Int = when (prayer) {
    Prayer.FAJR -> fajr
    Prayer.DHUHR -> dhuhr
    Prayer.ASR -> asr
    Prayer.MAGHRIB -> maghrib
    Prayer.ISHA -> isha
    Prayer.TAHAJJUD -> 0
}
