package com.rayluxeo.aida.ui.activity

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ActivityCategory

/**
 * One line of the Activity screen, already worded for the row that draws it.
 *
 * The time is resolved once, in the view model, because it needs the injected clock (§4.4); the
 * screen only lays the strings out. [summary] is the entry's own text, shown as it was recorded.
 */
data class ActivityLogItem(
    val id: Long,
    val summary: String,
    /** The category name and the "when", joined — e.g. "Tasks · 5 minutes ago". */
    val supporting: String,
)

/**
 * Everything the Activity screen draws from: the entries, which category chip is on ([null] is
 * "All"), the language the labels are in, and whether the first list has arrived yet.
 */
data class ActivityLogUiState(
    val entries: List<ActivityLogItem> = emptyList(),
    val selectedCategory: ActivityCategory? = null,
    val language: SpokenLanguage = SpokenLanguage.ENGLISH,
    val isLoading: Boolean = true,
)
