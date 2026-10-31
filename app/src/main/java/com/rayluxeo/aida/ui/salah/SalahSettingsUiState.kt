package com.rayluxeo.aida.ui.salah

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.salah.SalahNotificationSettings
import com.rayluxeo.aida.domain.salah.PrayerParameters
import com.rayluxeo.aida.domain.salah.SalahSettings
import com.rayluxeo.aida.domain.salah.TahajjudSettings

/**
 * Everything the Salah settings screen draws.
 *
 * [settings] is Abu's one Salah configuration, read straight from its repository so the screen is
 * always showing what Mira is actually computing and arming from — there is no Save button and no
 * draft copy of the configuration (the write-through model Settings uses, §4). The one draft the
 * screen keeps is [cityLabelDraft]: a place name is free text, so it is typed into a field before
 * it is committed, and only then does it reach the configuration.
 *
 * [language] rides alongside because the Salah configuration carries none — it is read from the
 * main settings and decides which phrasebook every label comes from.
 */
data class SalahSettingsUiState(
    val loading: Boolean = true,
    val settings: SalahSettings = SalahSettings(),
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    /** The place name being typed — for first-time setup and for renaming an existing place. */
    val cityLabelDraft: String = "",
    /** True while the inline name editor for an already-saved place is open. */
    val editingCityLabel: Boolean = false,
    /** True while a one-shot location fix is being taken. */
    val locatingNow: Boolean = false,
    /** Which selection sheet is open, if any. */
    val activeSheet: SalahSheet? = null,
    val citySearchQuery: String = "",
    val confirmingReset: Boolean = false,
    val resetting: Boolean = false,
    /** A transient failure line, rendered as a dismissible notice. Never a raw exception. */
    val notice: SalahSettingsNotice? = null,
) {
    /** True once Abu has a place to compute from — the gate for showing settings over first setup. */
    val isConfigured: Boolean get() = settings.location != null

    val parameters: PrayerParameters get() = settings.parameters
    val notifications: SalahNotificationSettings get() = settings.notifications
    val tahajjud: TahajjudSettings get() = settings.tahajjud
}

/** The one-choice-at-a-time selectors on the Salah settings screen, each shown in its own sheet. */
enum class SalahSheet { CALCULATION_METHOD, HIGH_LATITUDE_RULE, FOLLOW_UP_DELAY, TAHAJJUD_MODE, CITY_PICKER }

/** What went wrong, rendered per language by the phrasebook — never shown as an exception. */
enum class SalahSettingsNotice {
    /** The phone declined to say where Abu is: a state he fixes in system settings, not a fault. */
    LOCATION_UNAVAILABLE,

    /** A configuration change could not be written. */
    SAVE_FAILED,

    /** The tracking log could not be erased. */
    RESET_FAILED,
}
