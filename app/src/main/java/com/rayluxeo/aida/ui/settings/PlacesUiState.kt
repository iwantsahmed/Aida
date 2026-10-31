package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal

/**
 * The one question Mira is asking right now, and null when the screen is only a list.
 *
 * Modelled as state rather than held in the composable, because each of the three outlives a
 * recomposition and two of them survive a trip out to Android's own permission dialog.
 */
sealed interface PlacePrompt {

    /** "Save where I am now" was pressed, and the place has still to be named. */
    data object Naming : PlacePrompt

    /** An existing place is being renamed. The draft starts at the name it already has. */
    data class Renaming(val id: Long) : PlacePrompt

    /** Destructive, so it is asked before it is done. Carries the label the question names. */
    data class Forgetting(val id: Long, val label: String) : PlacePrompt
}

/**
 * Something Mira could not do, as a value rather than as a sentence.
 *
 * The wording lives in [PlacesWording] and the reason lives here, so a test can assert on what
 * Mira concluded without pinning the prose, and so every line the screen can show is one that
 * some problem actually produces.
 *
 * Deliberately not the same thing as a [PlaceWatchRefusal]: a refusal is the phone declining to
 * say where Abu is, which he settles on a settings screen, and one of these is Mira failing at
 * something she was asked to do.
 */
enum class PlacesProblem {
    NAME_BLANK,

    /** Another place already answers to the name, so the one he typed cannot be kept. */
    NAME_TAKEN,
    COULD_NOT_SAVE,
    COULD_NOT_RENAME,
    COULD_NOT_FORGET,
}

/**
 * What pressing the button on a refusal actually does.
 *
 * Kept apart from the label in [PlacesWording] because the two answer different questions: the
 * wording says what the control is called, this says what it does. They were the same thing for
 * as long as every refusal led to an Android settings screen, and [PlaceWatchRefusal.NO_FIX_YET]
 * is the one that does not — nothing on any settings screen changes the fact that the sky was not
 * visible, and sending Abu there would be a control that cannot help.
 */
enum class PlaceRefusalRemedy {

    /** Android's own screen settles it. */
    SETTINGS,

    /** Nothing to change: asking the platform again is the whole answer. */
    RETRY,
}

/**
 * The way out of a refusal, or null when there is none.
 *
 * Null for exactly the two Abu cannot act on, which is the same pair [PlacesWording.refusalAction]
 * has no words for — a phone with no location provider and a register Mira has filled. The two
 * agreeing is what stops a notice ever drawing a button with nothing behind it, and it is
 * asserted rather than assumed.
 */
fun PlaceWatchRefusal.remedy(): PlaceRefusalRemedy? = when (this) {
    PlaceWatchRefusal.NO_FIX_YET -> PlaceRefusalRemedy.RETRY

    PlaceWatchRefusal.PERMISSION_MISSING,
    PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING,
    PlaceWatchRefusal.LOCATION_DISABLED,
    PlaceWatchRefusal.BATTERY_RESTRICTED,
    -> PlaceRefusalRemedy.SETTINGS

    PlaceWatchRefusal.NO_PROVIDER, PlaceWatchRefusal.LIMIT_REACHED -> null
}

/**
 * Everything the Places screen draws.
 *
 * [refusal] and [problem] are kept apart on purpose. Both render as one dismissible line, but a
 * refusal carries a way through to the Android screen that settles it and a problem never does,
 * and collapsing them into one string would mean the screen guessing which of those it was
 * looking at.
 *
 * [draftLabel] deliberately survives a refused save. Abu typed a name, Mira could not read where
 * he was, and making him type it again once he has granted the permission would be a second
 * punishment for the phone's answer. It is cleared once a place is actually saved.
 */
data class PlacesUiState(
    val loading: Boolean = true,
    /**
     * Which of Abu's two languages the screen speaks, from his stored setting — carried for the
     * same reason the assistant screen carries it, so the wording is chosen once rather than
     * guessed per composable.
     */
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val places: List<SavedPlace> = emptyList(),
    val prompt: PlacePrompt? = null,
    val draftLabel: String = "",
    /** True while the radio is being asked. A fix can take seconds, and the button says so. */
    val saving: Boolean = false,
    val refusal: PlaceWatchRefusal? = null,
    val problem: PlacesProblem? = null,
) {

    /** An empty list is a normal state here, not a fault — but only once it is known to be one. */
    val isEmpty: Boolean get() = !loading && places.isEmpty()

    /** Nothing is said above the list until there is something true to say. */
    val hasNotice: Boolean get() = refusal != null || problem != null
}
