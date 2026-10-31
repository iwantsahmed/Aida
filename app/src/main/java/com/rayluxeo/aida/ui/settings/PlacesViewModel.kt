package com.rayluxeo.aida.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.repository.SavedPlaceRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.domain.usecase.DeletePlaceUseCase
import com.rayluxeo.aida.domain.usecase.PlaceSaveOutcome
import com.rayluxeo.aida.domain.usecase.RenamePlaceUseCase
import com.rayluxeo.aida.domain.usecase.SavePlaceHereUseCase
import com.rayluxeo.aida.ui.tasks.displayLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Abu's named places: the directory that makes "remind me when I get to the office" mean
 * something.
 *
 * Mira has no geocoder and no map, so a place becomes known by being stood in. Everything on this
 * screen is therefore optional in the strongest sense: the rest of Mira works with the location
 * permission refused, the radio off and this screen never opened, and nothing here nags, blocks
 * or presents itself as setup she needs.
 *
 * Whether the platform will answer at all is the screen's business rather than this class's — a
 * permission check needs a Context, and a view model holding one could not be tested without a
 * device. What arrives here is the answer: either a label to save, or the [PlaceWatchRefusal]
 * that explains why no fix was possible.
 */
@HiltViewModel
class PlacesViewModel @Inject constructor(
    places: SavedPlaceRepository,
    private val savePlaceHere: SavePlaceHereUseCase,
    private val renamePlace: RenamePlaceUseCase,
    private val deletePlace: DeletePlaceUseCase,
    settings: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    init {
        // A read path, observed rather than fetched once: the language toggle is two screens away
        // and a list that went on speaking English after Abu chose বাংলা would be the defect this
        // wording exists to fix.
        viewModelScope.launch {
            settings.settings.collect { preferences ->
                _state.update { it.copy(language = preferences.languageMode.displayLanguage()) }
            }
        }
        // The list is the repository's, not a copy kept in step by hand: every write below goes
        // through a use case and comes back out here, so a row can never show what was asked for
        // rather than what was stored.
        viewModelScope.launch {
            places.observeAll().collect { saved ->
                _state.update { it.copy(loading = false, places = saved) }
            }
        }
    }

    /**
     * Opens the naming prompt.
     *
     * Declines while a fix is already being taken: reopening the prompt mid-fix would leave Abu
     * naming a second place that the first answer is about to land on.
     */
    fun beginSaving() {
        if (_state.value.saving) return
        _state.update { it.copy(prompt = PlacePrompt.Naming, refusal = null, problem = null) }
    }

    fun beginRenaming(place: SavedPlace) {
        _state.update {
            it.copy(
                prompt = PlacePrompt.Renaming(place.id),
                draftLabel = place.label,
                refusal = null,
                problem = null,
            )
        }
    }

    fun beginForgetting(place: SavedPlace) {
        _state.update {
            it.copy(
                prompt = PlacePrompt.Forgetting(place.id, place.label),
                refusal = null,
                problem = null,
            )
        }
    }

    fun onDraftChange(label: String) {
        _state.update { it.copy(draftLabel = label) }
    }

    fun dismissPrompt() {
        _state.update { it.copy(prompt = null) }
    }

    fun dismissNotice() {
        _state.update { it.copy(refusal = null, problem = null) }
    }

    /**
     * Names the spot Abu is standing on and asks the platform where that is.
     *
     * The name is checked again here rather than trusted from the moment the button was pressed,
     * because the save is actually triggered from the permission-result callback: the dialog's
     * own state is two round trips old by the time this runs.
     *
     * A refusal is not a failure. [PlaceSaveOutcome.NoFix] leaves the list untouched and says why
     * — claiming a place landed when it did not is the one mistake here Abu has no way to notice.
     */
    fun confirmSave() {
        if (_state.value.saving) return
        val label = _state.value.draftLabel.trim()
        if (label.isEmpty()) {
            _state.update { it.copy(prompt = null, problem = PlacesProblem.NAME_BLANK) }
            return
        }
        _state.update { it.copy(prompt = null, saving = true, refusal = null, problem = null) }
        viewModelScope.launch {
            when (val result = savePlaceHere(label)) {
                is AppResult.Failure -> _state.update {
                    it.copy(
                        saving = false,
                        problem = result.error.code.named(PlacesProblem.COULD_NOT_SAVE),
                    )
                }

                is AppResult.Success -> when (val outcome = result.value) {
                    // Cleared only here: the name has been used, so the next place starts blank.
                    is PlaceSaveOutcome.Saved ->
                        _state.update { it.copy(saving = false, draftLabel = "") }

                    is PlaceSaveOutcome.NoFix ->
                        _state.update { it.copy(saving = false, refusal = outcome.reason) }
                }
            }
        }
    }

    /**
     * Renames the place the prompt is about.
     *
     * A name that was actually attempted is spent whether or not it took, because reopening the
     * prompt starts the draft again from the name the place currently has — which after a refused
     * rename is still the old one.
     */
    fun confirmRename() {
        val prompt = _state.value.prompt as? PlacePrompt.Renaming ?: return
        val label = _state.value.draftLabel.trim()
        if (label.isEmpty()) {
            _state.update { it.copy(prompt = null, problem = PlacesProblem.NAME_BLANK) }
            return
        }
        _state.update { it.copy(prompt = null, refusal = null, problem = null) }
        viewModelScope.launch {
            val result = renamePlace(prompt.id, label)
            _state.update {
                it.copy(
                    draftLabel = "",
                    problem = (result as? AppResult.Failure)
                        ?.error?.code?.named(PlacesProblem.COULD_NOT_RENAME),
                )
            }
        }
    }

    /**
     * Forgets the place the prompt is about.
     *
     * A `VALIDATION` here cannot be a name clash — nothing is being named — so it is reported as
     * the plain failure it is rather than run through [named].
     */
    fun confirmForget() {
        val prompt = _state.value.prompt as? PlacePrompt.Forgetting ?: return
        _state.update { it.copy(prompt = null, refusal = null, problem = null) }
        viewModelScope.launch {
            val result = deletePlace(prompt.id)
            if (result is AppResult.Failure) {
                _state.update { it.copy(problem = PlacesProblem.COULD_NOT_FORGET) }
            }
        }
    }

    /**
     * What the screen found out before the radio was ever reached.
     *
     * Reuses [PlaceWatchRefusal] rather than a second list of reasons: a permission Abu has not
     * granted reads the same and leads to the same settings screen whether the platform said so
     * or the permission dialog did.
     */
    fun reportRefusal(reason: PlaceWatchRefusal) {
        _state.update { it.copy(prompt = null, saving = false, refusal = reason) }
    }

    /**
     * A `VALIDATION` on a write that carries a name means the name is taken.
     *
     * The other two things the use cases validate — a coordinate off the globe and a radius
     * outside the bounds — are Mira's own constants on this screen and cannot come from anything
     * Abu typed, so there is nothing else this code can be reporting about.
     */
    private fun MiraErrorCode.named(fallback: PlacesProblem): PlacesProblem =
        if (this == MiraErrorCode.VALIDATION) PlacesProblem.NAME_TAKEN else fallback
}
