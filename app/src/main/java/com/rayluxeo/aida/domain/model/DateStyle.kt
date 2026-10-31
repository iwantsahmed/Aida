package com.rayluxeo.aida.domain.model

/**
 * How Abu wants a calendar date written, everywhere Mira prints one.
 *
 * The enum carries only the choice, never a format pattern or any wording — rendering a date in
 * Abu's language belongs to the presentation layer (§4). The default is day-before-month, which is
 * how a date is read aloud in both Bangla and the English Abu speaks.
 */
enum class DateStyle {
    /** 2026-10-10 — unambiguous, sortable. */
    ISO,

    /** 10 Oct 2026. */
    DAY_MONTH_YEAR,

    /** Oct 10, 2026. */
    MONTH_DAY_YEAR,

    /** 10/10/2026. */
    NUMERIC_DAY_MONTH,

    /** 10/10/2026, month first. */
    NUMERIC_MONTH_DAY,
}
