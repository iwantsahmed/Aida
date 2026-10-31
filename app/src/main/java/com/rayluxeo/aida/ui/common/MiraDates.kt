package com.rayluxeo.aida.ui.common

import androidx.compose.runtime.staticCompositionLocalOf
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.DateStyle
import com.rayluxeo.aida.ui.response.bengaliNumerals
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One place that turns a [LocalDate] into the string Abu reads, in the style he picked in Settings
 * and the language Mira is speaking. Dates used to be formatted with a hard-coded pattern at every
 * call site; routing them all through here is what makes the Date Format setting actually change
 * what he sees across the app.
 *
 * Bengali uses its own month names (from the Bengali locale) and its own digits (via
 * [bengaliNumerals], applied so the digits are Bengali whatever the JVM's locale data returns).
 */
fun formatDate(date: LocalDate, style: DateStyle, language: SpokenLanguage): String {
    val locale = if (language == SpokenLanguage.BENGALI) BENGALI_LOCALE else Locale.ENGLISH
    val text = date.format(DateTimeFormatter.ofPattern(style.pattern(), locale))
    return if (language == SpokenLanguage.BENGALI) bengaliNumerals(text) else text
}

private fun DateStyle.pattern(): String = when (this) {
    DateStyle.ISO -> "yyyy-MM-dd"
    DateStyle.DAY_MONTH_YEAR -> "d MMM yyyy"
    DateStyle.MONTH_DAY_YEAR -> "MMM d, yyyy"
    DateStyle.NUMERIC_DAY_MONTH -> "dd/MM/yyyy"
    DateStyle.NUMERIC_MONTH_DAY -> "MM/dd/yyyy"
}

private val BENGALI_LOCALE: Locale = Locale.forLanguageTag("bn")

/**
 * The date style in force, provided once at the app root from Abu's setting so any screen can
 * format a date without its own view model having to carry the preference. Defaults to the same
 * value [com.rayluxeo.aida.domain.repository.MiraSettings] defaults to, for previews and the first
 * frame before settings load.
 */
val LocalDateStyle = staticCompositionLocalOf { DateStyle.DAY_MONTH_YEAR }
