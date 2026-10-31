package com.rayluxeo.aida.ui.salah

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraSwitch
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.salah.BuiltInCities
import com.rayluxeo.aida.domain.salah.CalculationMethod
import com.rayluxeo.aida.domain.salah.HighLatitudeRule
import com.rayluxeo.aida.domain.salah.Madhab
import com.rayluxeo.aida.domain.salah.Prayer
import com.rayluxeo.aida.domain.salah.PrayerCity
import com.rayluxeo.aida.domain.salah.PrayerLocation
import com.rayluxeo.aida.domain.salah.TahajjudReminderMode

/**
 * Everything Abu can set for Salah: where he prays from, how the times are worked out, the minute
 * corrections that line Mira up with his mosque, and which notifications, check-ins and Tahajjud
 * reminder he wants. Every control writes straight through the view model and takes effect on the
 * schedule at once — there is no Save button, and nothing here only looks like it did something
 * (§12). A control with nothing behind it yet does not appear.
 *
 * The screen reads whichever language Abu chose in Settings; the Salah configuration carries none of
 * its own, so [SalahSettingsUiState.language] rides alongside it.
 */
@Composable
fun SalahSettingsScreen(
    onBack: () -> Unit,
    viewModel: SalahSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MiraTheme.spacing
    val words = salahSettingsWording(state.language)

    MiraScaffold(
        topBar = { MiraTopBar(title = words.title, onBack = onBack, backContentDescription = words.back) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                MiraLoadingIndicator()
            }
            return@MiraScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(spacing.md))
            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = words.noticeText(notice),
                    tone = MiraNoticeTone.DANGER,
                    dismissContentDescription = words.dismiss,
                    onDismiss = viewModel::dismissNotice,
                    actionText = if (notice == SalahSettingsNotice.LOCATION_UNAVAILABLE) words.openSystemSettings else null,
                    onAction = if (notice == SalahSettingsNotice.LOCATION_UNAVAILABLE) {
                        { context.openLocationSettings() }
                    } else {
                        null
                    },
                )
                Spacer(Modifier.height(spacing.lg))
            }

            // ===================== LOCATION =====================
            MiraSectionHeader(text = words.sectionLocation)
            val location = state.settings.location
            if (location == null) {
                LocationSetupCard(
                    words = words,
                    language = state.language,
                    draft = state.cityLabelDraft,
                    locating = state.locatingNow,
                    onDraftChange = viewModel::onCityLabelDraftChange,
                    onOpenCityPicker = viewModel::openCityPicker,
                    onSelectCity = viewModel::selectCity,
                    onApplyCityDraft = viewModel::applyCityLabelAsLocation,
                    onUseCurrentLocation = viewModel::useCurrentLocation,
                )
            } else {
                SavedLocationBlock(
                    words = words,
                    language = state.language,
                    location = location,
                    editing = state.editingCityLabel,
                    draft = state.cityLabelDraft,
                    locating = state.locatingNow,
                    onOpenCityPicker = viewModel::openCityPicker,
                    onBeginEditName = viewModel::beginEditingName,
                    onDraftChange = viewModel::onCityLabelDraftChange,
                    onSaveName = viewModel::saveCityLabel,
                    onCancelEditName = viewModel::cancelEditingName,
                    onUseCurrentLocation = viewModel::useCurrentLocation,
                )
            }

            // ===================== PRAYER CALCULATION =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionCalculation)
            SelectorRow(
                title = words.calculationMethod,
                supporting = if (!state.isConfigured) words.karachiSuggestion else null,
                value = words.methodShortName(state.parameters.method),
                icon = MiraIcons.AutoAwesome,
            ) { viewModel.openSheet(SalahSheet.CALCULATION_METHOD) }

            Spacer(Modifier.height(spacing.lg))
            LabeledSegment(
                title = words.asrMethod,
                supporting = words.asrMethodDetail,
                options = Madhab.entries.map { words.madhabName(it) },
                selectedIndex = Madhab.entries.indexOf(state.parameters.madhab),
                onSelect = { viewModel.setMadhab(Madhab.entries[it]) },
            )

            if (state.parameters.method == CalculationMethod.CUSTOM) {
                Spacer(Modifier.height(spacing.lg))
                StepperRow(
                    title = words.fajrAngle,
                    supporting = null,
                    value = words.angleLabel(state.parameters.effectiveFajrAngle),
                    icon = MiraIcons.WbSunny,
                    decreaseContentDescription = words.decrease,
                    increaseContentDescription = words.increase,
                    onDecrement = { viewModel.nudgeFajrAngle(-ANGLE_STEP) },
                    onIncrement = { viewModel.nudgeFajrAngle(ANGLE_STEP) },
                )
                MiraDivider()
                StepperRow(
                    title = words.ishaAngle,
                    supporting = null,
                    value = words.angleLabel(state.parameters.effectiveIshaAngle),
                    icon = MiraIcons.Bedtime,
                    decreaseContentDescription = words.decrease,
                    increaseContentDescription = words.increase,
                    onDecrement = { viewModel.nudgeIshaAngle(-ANGLE_STEP) },
                    onIncrement = { viewModel.nudgeIshaAngle(ANGLE_STEP) },
                )
            }

            MiraDivider()
            SelectorRow(
                title = words.highLatitudeRule,
                supporting = words.highLatitudeRuleDetail,
                value = words.highLatitudeRuleShortName(state.parameters.highLatitudeRule),
                icon = MiraIcons.Place,
            ) { viewModel.openSheet(SalahSheet.HIGH_LATITUDE_RULE) }

            // ===================== MANUAL ADJUSTMENTS =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionAdjustments)
            GutterText(words.adjustmentsDetail)
            Spacer(Modifier.height(spacing.xs))
            Prayer.OBLIGATORY.forEachIndexed { index, prayer ->
                if (index > 0) MiraDivider()
                StepperRow(
                    title = words.prayerName(prayer),
                    supporting = null,
                    value = words.minutesLabel(state.parameters.adjustments.minutesFor(prayer)),
                    icon = MiraIcons.Schedule,
                    decreaseContentDescription = words.decrease,
                    increaseContentDescription = words.increase,
                    onDecrement = { viewModel.nudgeAdjustment(prayer, -1) },
                    onIncrement = { viewModel.nudgeAdjustment(prayer, 1) },
                )
            }

            // ===================== NOTIFICATIONS =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionNotifications)
            ToggleRow(
                title = words.prayerNotifications,
                supporting = words.prayerNotificationsDetail,
                icon = MiraIcons.NotificationsActive,
                checked = state.notifications.prayerNotificationsEnabled,
                onCheckedChange = viewModel::setPrayerNotifications,
            )
            if (state.notifications.prayerNotificationsEnabled) {
                Prayer.OBLIGATORY.forEach { prayer ->
                    MiraDivider()
                    ToggleRow(
                        title = words.prayerName(prayer),
                        supporting = null,
                        icon = null,
                        checked = state.notifications.perPrayerNotify[prayer] ?: true,
                        onCheckedChange = { viewModel.setPerPrayerNotify(prayer, it) },
                    )
                }
            }

            // ===================== PRAYER CHECK-INS =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionCheckIns)
            ToggleRow(
                title = words.askAfterEach,
                supporting = words.askAfterEachDetail,
                icon = MiraIcons.Forum,
                checked = state.notifications.checkInEnabled,
                onCheckedChange = viewModel::setCheckInEnabled,
            )
            if (state.notifications.checkInEnabled) {
                MiraDivider()
                SelectorRow(
                    title = words.followUpDelay,
                    supporting = words.followUpDelayDetail,
                    value = words.delayLabel(state.notifications.defaultAskDelayMinutes),
                    icon = MiraIcons.Timer,
                ) { viewModel.openSheet(SalahSheet.FOLLOW_UP_DELAY) }
                Prayer.OBLIGATORY.forEach { prayer ->
                    MiraDivider()
                    ToggleRow(
                        title = words.prayerName(prayer),
                        supporting = null,
                        icon = null,
                        checked = state.notifications.perPrayerCheckIn[prayer] ?: true,
                        onCheckedChange = { viewModel.setPerPrayerCheckIn(prayer, it) },
                    )
                }
            }

            // ===================== TAHAJJUD =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionTahajjud)
            ToggleRow(
                title = words.tahajjudReminder,
                supporting = words.tahajjudReminderDetail,
                icon = MiraIcons.Bedtime,
                checked = state.tahajjud.reminderEnabled,
                onCheckedChange = viewModel::setTahajjudReminder,
            )
            if (state.tahajjud.reminderEnabled) {
                MiraDivider()
                SelectorRow(
                    title = words.tahajjudTime,
                    supporting = null,
                    value = words.tahajjudModeShortName(state.tahajjud.mode),
                    icon = MiraIcons.Schedule,
                ) { viewModel.openSheet(SalahSheet.TAHAJJUD_MODE) }
                if (state.tahajjud.mode == TahajjudReminderMode.CUSTOM) {
                    MiraDivider()
                    StepperRow(
                        title = words.tahajjudCustomMinutes,
                        supporting = null,
                        value = words.delayLabel(state.tahajjud.customMinutesIntoLastThird),
                        icon = MiraIcons.Timer,
                        decreaseContentDescription = words.decrease,
                        increaseContentDescription = words.increase,
                        onDecrement = { viewModel.nudgeTahajjudCustomMinutes(-TAHAJJUD_MINUTE_STEP) },
                        onIncrement = { viewModel.nudgeTahajjudCustomMinutes(TAHAJJUD_MINUTE_STEP) },
                    )
                }
            }
            Spacer(Modifier.height(spacing.xs))
            GutterText(words.tahajjudNote)

            // ===================== TRACKING & STATISTICS =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionTracking)
            GutterText(words.trackingDetail, style = MaterialTheme.typography.bodyMedium)

            // ===================== PRIVACY =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionPrivacy)
            GutterText(words.privacyDetail, style = MaterialTheme.typography.bodyMedium)

            // ===================== RESET =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionReset)
            MiraListRow(
                title = if (state.resetting) words.resetting else words.resetTracking,
                supporting = words.resetTrackingDetail,
                enabled = !state.resetting,
                onClick = viewModel::askReset,
                leading = { Icon(MiraIcons.Delete, null, tint = MiraTheme.colors.danger) },
            )

            Spacer(Modifier.height(spacing.xxl))
        }
    }

    if (state.confirmingReset) {
        MiraDialog(
            title = words.resetTitle,
            onDismissRequest = viewModel::cancelReset,
            confirmText = words.resetConfirm,
            onConfirm = viewModel::confirmReset,
            message = words.resetMessage,
            dismissText = words.resetKeep,
            onDismiss = viewModel::cancelReset,
            destructive = true,
        )
    }

    SalahSelectionSheets(state = state, words = words, viewModel = viewModel)
}

/** The selection sheets, kept out of the long column so the structure above reads as the screen. */
@Composable
private fun SalahSelectionSheets(
    state: SalahSettingsUiState,
    words: SalahSettingsWording,
    viewModel: SalahSettingsViewModel,
) {
    when (state.activeSheet) {
        SalahSheet.CALCULATION_METHOD -> SalahSelectionSheet(
            title = words.chooseMethod,
            options = CalculationMethod.entries.map {
                SalahOption(words.methodName(it), null, it == state.parameters.method)
            },
            onSelect = { viewModel.setMethod(CalculationMethod.entries[it]) },
            onDismiss = viewModel::dismissSheet,
        )
        SalahSheet.HIGH_LATITUDE_RULE -> SalahSelectionSheet(
            title = words.chooseHighLatitude,
            options = HighLatitudeRule.entries.map {
                SalahOption(words.highLatitudeRuleName(it), null, it == state.parameters.highLatitudeRule)
            },
            onSelect = { viewModel.setHighLatitudeRule(HighLatitudeRule.entries[it]) },
            onDismiss = viewModel::dismissSheet,
        )
        SalahSheet.FOLLOW_UP_DELAY -> SalahSelectionSheet(
            title = words.chooseDelay,
            options = SALAH_FOLLOW_UP_DELAYS.map {
                SalahOption(words.delayLabel(it), null, it == state.notifications.defaultAskDelayMinutes)
            },
            onSelect = { viewModel.setFollowUpDelay(SALAH_FOLLOW_UP_DELAYS[it]) },
            onDismiss = viewModel::dismissSheet,
        )
        SalahSheet.TAHAJJUD_MODE -> SalahSelectionSheet(
            title = words.chooseTahajjudTime,
            options = TahajjudReminderMode.entries.map {
                SalahOption(words.tahajjudModeName(it), null, it == state.tahajjud.mode)
            },
            onSelect = { viewModel.setTahajjudMode(TahajjudReminderMode.entries[it]) },
            onDismiss = viewModel::dismissSheet,
        )
        SalahSheet.CITY_PICKER -> CityPickerSheet(
            words = words,
            language = state.language,
            searchQuery = state.citySearchQuery,
            currentLocation = state.settings.location,
            onQueryChange = viewModel::onCitySearchQueryChange,
            onSelect = viewModel::selectCity,
            onDismiss = viewModel::dismissSheet,
        )
        null -> Unit
    }
}

// --------------------------------------------------------------- location pieces

/** First-run setup: choose city or name the place. No GPS or location tracking required. */
@Composable
private fun LocationSetupCard(
    words: SalahSettingsWording,
    language: SpokenLanguage,
    draft: String,
    locating: Boolean,
    onDraftChange: (String) -> Unit,
    onOpenCityPicker: () -> Unit,
    onSelectCity: (PrayerCity) -> Unit,
    onApplyCityDraft: () -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val isBengali = language == SpokenLanguage.BENGALI

    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(MiraIcons.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = words.locationSetupTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = words.noGpsRequiredBadge,
            style = MaterialTheme.typography.labelMedium,
            color = MiraTheme.colors.accentDim,
        )
        Spacer(Modifier.height(spacing.xs))
        Text(
            text = words.locationSetupBody,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.md))

        // Quick popular cities
        Text(
            text = words.popularLocations,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xs))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            listOf("dhaka", "chattogram", "sylhet", "rajshahi", "khulna", "makkah").forEach { id ->
                val city = BuiltInCities.findById(id) ?: return@forEach
                MiraButton(
                    text = if (isBengali) city.bengaliName else city.englishName,
                    onClick = { onSelectCity(city) },
                    style = if (id == "dhaka") MiraButtonStyle.PRIMARY else MiraButtonStyle.SECONDARY,
                )
            }
        }

        Spacer(Modifier.height(spacing.md))
        MiraButton(
            text = words.selectCity,
            onClick = onOpenCityPicker,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = MiraIcons.Place,
        )

        Spacer(Modifier.height(spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiraTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                label = words.cityLabelField,
                placeholder = words.cityLabelPlaceholder,
                leadingIcon = MiraIcons.Search,
                enabled = !locating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            if (draft.isNotBlank()) {
                MiraButton(
                    text = words.save,
                    onClick = onApplyCityDraft,
                    style = MiraButtonStyle.SECONDARY,
                )
            }
        }

        Spacer(Modifier.height(spacing.md))
        MiraButton(
            text = if (locating) words.locating else words.useCurrentLocation,
            onClick = onUseCurrentLocation,
            modifier = Modifier.fillMaxWidth(),
            enabled = !locating,
            style = MiraButtonStyle.SECONDARY,
            leadingIcon = MiraIcons.Refresh,
        )
    }
    Spacer(Modifier.height(spacing.xs))
    GutterText(words.mosqueNote)
}

/** The saved place: its name and coordinates, a way to pick a city, rename it, or re-take a GPS fix. */
@Composable
private fun SavedLocationBlock(
    words: SalahSettingsWording,
    language: SpokenLanguage,
    location: PrayerLocation,
    editing: Boolean,
    draft: String,
    locating: Boolean,
    onOpenCityPicker: () -> Unit,
    onBeginEditName: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSaveName: () -> Unit,
    onCancelEditName: () -> Unit,
    onUseCurrentLocation: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraListRow(
        title = location.cityLabel.ifBlank { words.unnamedPlace },
        supporting = "${words.coordinates(location.latitude, location.longitude)} • ${location.zoneId.id}",
        leading = { Icon(MiraIcons.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )

    if (editing) {
        Column(Modifier.fillMaxWidth().padding(top = spacing.sm)) {
            MiraTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = words.cityLabelField,
                placeholder = words.cityLabelPlaceholder,
                leadingIcon = MiraIcons.Place,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(spacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                MiraButton(
                    text = words.save,
                    onClick = onSaveName,
                    modifier = Modifier.weight(1f),
                )
                MiraButton(
                    text = words.cancel,
                    onClick = onCancelEditName,
                    modifier = Modifier.weight(1f),
                    style = MiraButtonStyle.SECONDARY,
                )
            }
        }
    } else {
        MiraDivider()
        MiraListRow(
            title = words.changeCity,
            onClick = onOpenCityPicker,
            leading = { Icon(MiraIcons.Place, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        MiraDivider()
        MiraListRow(
            title = words.editName,
            onClick = onBeginEditName,
            leading = { Icon(MiraIcons.Edit, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
        MiraDivider()
        MiraListRow(
            title = if (locating) words.locating else words.useCurrentLocation,
            enabled = !locating,
            onClick = onUseCurrentLocation,
            leading = { Icon(MiraIcons.Refresh, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        )
    }
    Spacer(Modifier.height(spacing.xs))
    GutterText(words.mosqueNote)
}

/** Searchable sheet to pick from all 64 Bangladesh districts and world cities without location tracking. */
@Composable
private fun CityPickerSheet(
    words: SalahSettingsWording,
    language: SpokenLanguage,
    searchQuery: String,
    currentLocation: PrayerLocation?,
    onQueryChange: (String) -> Unit,
    onSelect: (PrayerCity) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val isBengali = language == SpokenLanguage.BENGALI
    val cities = remember(searchQuery) { BuiltInCities.find(searchQuery) }

    MiraBottomSheet(
        onDismissRequest = onDismiss,
        title = words.selectCity,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.md),
        ) {
            MiraTextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = words.searchCityPlaceholder,
                leadingIcon = MiraIcons.Search,
            )
            Spacer(Modifier.height(spacing.sm))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
        ) {
            items(cities, key = { it.id }) { city ->
                val isSelected = currentLocation?.let {
                    it.cityLabel.equals(city.englishName, ignoreCase = true) ||
                        it.cityLabel == city.bengaliName ||
                        (kotlin.math.abs(it.latitude - city.latitude) < 0.05 && kotlin.math.abs(it.longitude - city.longitude) < 0.05)
                } ?: false

                MiraListRow(
                    title = if (isBengali) "${city.bengaliName} (${city.englishName})" else city.englishName,
                    supporting = "${city.regionOrCountry} • ${city.latitude}°, ${city.longitude}°",
                    onClick = { onSelect(city) },
                    trailing = {
                        if (isSelected) {
                            Icon(
                                imageVector = MiraIcons.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
                MiraDivider()
            }
        }
    }
}

// --------------------------------------------------------------- row helpers

/** One selectable option in a Salah selection sheet. */
private data class SalahOption(val label: String, val supporting: String?, val selected: Boolean)

@Composable
private fun SalahSelectionSheet(
    title: String,
    options: List<SalahOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MiraBottomSheet(onDismissRequest = onDismiss, title = title) {
        options.forEachIndexed { index, option ->
            if (index > 0) MiraDivider()
            MiraListRow(
                title = option.label,
                supporting = option.supporting,
                onClick = { onSelect(index) },
                trailing = {
                    if (option.selected) {
                        Icon(MiraIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    }
}

/** A segmented control with its own label and footnote above it — the Settings screen's pattern. */
@Composable
private fun LabeledSegment(
    title: String,
    supporting: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val spacing = MiraTheme.spacing
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(spacing.xs))
    }
    MiraSegmentedControl(options = options, selectedIndex = selectedIndex, onSelect = onSelect)
    Spacer(Modifier.height(spacing.xs))
    GutterText(supporting)
}

/** A row that opens a selection sheet: its glyph, its label, and the current choice at the end. */
@Composable
private fun SelectorRow(
    title: String,
    supporting: String?,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    MiraListRow(
        title = title,
        supporting = supporting,
        onClick = onClick,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingText = value,
        trailing = {
            Icon(MiraIcons.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        },
    )
}

/** A row whose trailing control is a switch, with an optional leading glyph for a top-level toggle. */
@Composable
private fun ToggleRow(
    title: String,
    supporting: String?,
    icon: ImageVector?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    MiraListRow(
        title = title,
        supporting = supporting,
        onClick = { onCheckedChange(!checked) },
        leading = leadingGlyph(icon),
        trailing = { MiraSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

/** The leading slot for a row that may or may not carry a glyph — null means none, so no space. */
private fun leadingGlyph(icon: ImageVector?): (@Composable () -> Unit)? =
    if (icon == null) {
        null
    } else {
        { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

/** A row with a −/+ stepper at the end, for a minute correction, an angle, or a window length. */
@Composable
private fun StepperRow(
    title: String,
    supporting: String?,
    value: String,
    icon: ImageVector?,
    decreaseContentDescription: String,
    increaseContentDescription: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraListRow(
        title = title,
        supporting = supporting,
        leading = leadingGlyph(icon),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiraIconButton(
                    icon = MiraIcons.Remove,
                    contentDescription = decreaseContentDescription,
                    onClick = onDecrement,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value,
                    modifier = Modifier.widthIn(min = spacing.xxl),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                MiraIconButton(
                    icon = MiraIcons.Add,
                    contentDescription = increaseContentDescription,
                    onClick = onIncrement,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

/** Body copy or a footnote set at the gutter, aligned under the row titles. */
@Composable
private fun GutterText(
    text: String,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val ANGLE_STEP = 0.5
private const val TAHAJJUD_MINUTE_STEP = 5

/** Opens the system screen where Abu can turn location on, falling back to Mira's app details. */
private fun Context.openLocationSettings() {
    try {
        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    } catch (missing: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }
}
