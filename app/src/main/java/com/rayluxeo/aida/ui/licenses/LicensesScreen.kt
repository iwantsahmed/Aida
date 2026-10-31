package com.rayluxeo.aida.ui.licenses

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraTopBar

/**
 * One acknowledged dependency: its own proper name and the name of the licence it ships under,
 * both read the same in either language. [viewable] marks the single row that opens a bundled
 * licence text in full — the Noto Serif Bengali font, whose OFL text ships beside it in the APK.
 */
private data class LicenseEntry(
    val name: String,
    val license: String,
    val viewable: Boolean = false,
)

/** A labelled group of entries, shown under one [MiraSectionHeader]. */
private data class LicenseGroup(
    val section: LicenseSection,
    val entries: List<LicenseEntry>,
)

/**
 * The real set recorded in `docs/ZERO_COST_AUDIT.md`: every component is free and open-source, and
 * each licence name is quoted as the licence itself states it. Nothing here is fabricated, and a
 * new dependency is added to this list in the same change that records it in the audit.
 */
private val LICENSE_GROUPS = listOf(
    LicenseGroup(
        section = LicenseSection.FRAMEWORK,
        entries = listOf(
            LicenseEntry("Jetpack Compose", "Apache License 2.0"),
            LicenseEntry("Material 3", "Apache License 2.0"),
            LicenseEntry("AndroidX Core", "Apache License 2.0"),
            LicenseEntry("Lifecycle", "Apache License 2.0"),
            LicenseEntry("Navigation Compose", "Apache License 2.0"),
            LicenseEntry("Room", "Apache License 2.0"),
            LicenseEntry("DataStore", "Apache License 2.0"),
            LicenseEntry("WorkManager", "Apache License 2.0"),
            LicenseEntry("Hilt (Dagger)", "Apache License 2.0"),
            LicenseEntry("ML Kit Text Recognition", "Apache License 2.0"),
        ),
    ),
    LicenseGroup(
        section = LicenseSection.LANGUAGE,
        entries = listOf(
            LicenseEntry("Kotlin", "Apache License 2.0"),
            LicenseEntry("Kotlin Coroutines", "Apache License 2.0"),
            LicenseEntry("kotlinx.serialization", "Apache License 2.0"),
            LicenseEntry("Core library desugaring", "GPLv2 with Classpath Exception"),
        ),
    ),
    LicenseGroup(
        section = LicenseSection.TYPE_AND_ICONS,
        entries = listOf(
            LicenseEntry("Manrope", "SIL Open Font License 1.1"),
            LicenseEntry("Noto Serif Bengali", "SIL Open Font License 1.1", viewable = true),
            LicenseEntry("Solar icons", "CC0 / Public Domain"),
        ),
    ),
)

/**
 * The acknowledgements screen in the About section: the open-source work Mira is built on and the
 * licences that keep it free. Every entry is a real dependency from `docs/ZERO_COST_AUDIT.md`.
 *
 * Only the Noto Serif Bengali row does more than name a licence: it opens the bundled OFL text,
 * the one licence file shipped in the APK, in a sheet.
 */
@Composable
fun LicensesScreen(
    onBack: () -> Unit,
    viewModel: LicensesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val words = licensesWording(state.language)
    val spacing = MiraTheme.spacing

    var showFontLicense by remember { mutableStateOf(false) }

    MiraScaffold(
        topBar = { MiraTopBar(title = words.screenTitle, onBack = onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Spacer(Modifier.height(spacing.md))
            Text(
                text = words.intro,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.sectionGap))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LICENSE_GROUPS.forEach { group ->
                    item(key = "header_${group.section}") {
                        MiraSectionHeader(text = words.sectionLabel(group.section))
                    }
                    itemsIndexed(group.entries, key = { _, entry -> entry.name }) { index, entry ->
                        LicenseRow(
                            entry = entry,
                            words = words,
                            onViewLicense = {
                                viewModel.loadFontLicense()
                                showFontLicense = true
                            },
                        )
                        if (index < group.entries.lastIndex) {
                            MiraDivider()
                        }
                    }
                    item(key = "gap_${group.section}") {
                        Spacer(Modifier.height(spacing.sectionGap))
                    }
                }
                item(key = "bottom_breathing_room") {
                    Spacer(Modifier.height(spacing.xl))
                }
            }
        }
    }

    if (showFontLicense) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        MiraBottomSheet(
            onDismissRequest = { showFontLicense = false },
            sheetState = sheetState,
            title = words.fontSheetTitle,
        ) {
            val text = state.fontLicense
            if (text == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xl),
                    contentAlignment = Alignment.Center,
                ) {
                    MiraLoadingIndicator()
                }
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(LICENSE_SHEET_HEIGHT_FRACTION)
                        .verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

/**
 * One dependency row: the component on the left, its licence beneath. Only a [LicenseEntry.viewable]
 * row is tappable — it carries the "view licence" affordance and opens the bundled text; the rest
 * simply state a fact and so have no handler (§12).
 */
@Composable
private fun LicenseRow(
    entry: LicenseEntry,
    words: LicensesWording,
    onViewLicense: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraListRow(
        title = entry.name,
        supporting = entry.license,
        onClick = if (entry.viewable) onViewLicense else null,
        trailing = if (entry.viewable) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = words.viewLicense,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(spacing.xs))
                    Icon(
                        imageVector = MiraIcons.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
    )
}

/** The licence text is long, so the sheet claims most of the screen and the text scrolls inside it. */
private const val LICENSE_SHEET_HEIGHT_FRACTION = 0.8f
