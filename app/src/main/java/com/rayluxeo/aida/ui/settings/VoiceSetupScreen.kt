package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraLoadingIndicator
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraSwitch
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.repository.VoicePreferences

/**
 * Where Abu decides how Mira sounds, by listening rather than by reading labels.
 *
 * Tapping a voice plays it and keeps it, in one gesture. Every row shows only what the engine
 * actually reported — locale, quality, latency, whether the voice needs the network. Nothing is
 * inferred from a voice's name, least of all a gender the Android API does not expose.
 */
@Composable
fun VoiceSetupScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: VoiceSetupViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MiraTheme.spacing
    val wording = voiceSetupWording(state.language)

    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = wording.title,
                subtitle = wording.subtitle,
                onBack = onBack,
                backContentDescription = wording.back,
            )
        },
    ) { padding ->
        when {
            state.loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MiraListSkeleton()
            }

            state.noEngines -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MiraEmptyState(
                    icon = MiraIcons.RecordVoiceOver,
                    message = wording.noEngines,
                    actionText = wording.openTextToSpeechSettings,
                    onAction = {
                        context.openTextToSpeechSettings(wording, viewModel::reportProblem)
                    },
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState()),
            ) {
                // One opening interval, held whether or not a notice is showing, so the first
                // line does not shift up the screen the moment one is dismissed.
                Spacer(Modifier.height(spacing.md))
                val notice = state.notice
                if (notice != null) {
                    MiraInlineNotice(
                        text = notice,
                        tone = MiraNoticeTone.DANGER,
                        dismissContentDescription = wording.dismiss,
                        onDismiss = viewModel::dismissNotice,
                    )
                    Spacer(Modifier.height(spacing.lg))
                }

                Text(
                    text = wording.intro,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(spacing.sectionGap))
                EngineSection(
                    state = state,
                    wording = wording,
                    onSelectEngine = viewModel::selectEngine,
                )

                Spacer(Modifier.height(spacing.sectionGap))
                VoiceSection(
                    heading = wording.englishVoices,
                    language = SpokenLanguage.ENGLISH,
                    state = state,
                    wording = wording,
                    missing = state.englishMissing,
                    missingMessage = wording.englishMissing,
                    onChoose = viewModel::chooseVoice,
                    onOpenSystemSettings = {
                        context.openTextToSpeechSettings(wording, viewModel::reportProblem)
                    },
                )

                Spacer(Modifier.height(spacing.sectionGap))
                VoiceSection(
                    heading = wording.bengaliVoices,
                    language = SpokenLanguage.BENGALI,
                    state = state,
                    wording = wording,
                    missing = state.bengaliMissing,
                    missingMessage = wording.bengaliMissing,
                    onChoose = viewModel::chooseVoice,
                    onOpenSystemSettings = {
                        context.openTextToSpeechSettings(wording, viewModel::reportProblem)
                    },
                )

                Spacer(Modifier.height(spacing.sectionGap))
                MiraSectionHeader(text = wording.speedAndPitch)
                SliderRow(
                    label = wording.speechRate,
                    value = state.preferences.speechRate,
                    range = VoicePreferences.RATE_RANGE,
                    wording = wording,
                    onChange = viewModel::onSpeechRateChange,
                    onCommit = viewModel::commitSpeechRate,
                )
                Spacer(Modifier.height(spacing.md))
                SliderRow(
                    label = wording.pitch,
                    value = state.preferences.pitch,
                    range = VoicePreferences.PITCH_RANGE,
                    wording = wording,
                    onChange = viewModel::onPitchChange,
                    onCommit = viewModel::commitPitch,
                )
                // Wider than the gap between the two sliders: those two belong together, and
                // these buttons are what you do once they are set.
                Spacer(Modifier.height(spacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    MiraButton(
                        style = MiraButtonStyle.SECONDARY,
                        text = wording.hearEnglish,
                        onClick = { viewModel.playSample(SpokenLanguage.ENGLISH) },
                        modifier = Modifier.weight(1f),
                        enabled = state.canPlaySample(SpokenLanguage.ENGLISH),
                    )
                    MiraButton(
                        style = MiraButtonStyle.SECONDARY,
                        text = wording.hearBengali,
                        onClick = { viewModel.playSample(SpokenLanguage.BENGALI) },
                        modifier = Modifier.weight(1f),
                        enabled = state.canPlaySample(SpokenLanguage.BENGALI),
                    )
                }

                Spacer(Modifier.height(spacing.sectionGap))
                MiraSectionHeader(text = wording.spokenReplies)
                MiraListRow(
                    title = wording.speakHerAnswers,
                    supporting = wording.speakHerAnswersDetail,
                    onClick = {
                        viewModel.setSpokenResponsesEnabled(
                            !state.preferences.spokenResponsesEnabled,
                        )
                    },
                    leading = {
                        // Muted whatever the setting is: the switch beside it already answers
                        // that question, and a glyph answering it twice only spends the accent.
                        Icon(
                            imageVector = MiraIcons.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailing = {
                        MiraSwitch(
                            checked = state.preferences.spokenResponsesEnabled,
                            onCheckedChange = viewModel::setSpokenResponsesEnabled,
                        )
                    },
                )
                Spacer(Modifier.height(spacing.xxl))
            }
        }
    }
}

/**
 * The engine list, with the system default as a row of its own.
 *
 * Following Android's own choice is a decision Abu can make on purpose, so it is offered as a
 * selectable option rather than as the state he is left in when he has not pinned anything.
 */
@Composable
private fun EngineSection(
    state: VoiceSetupUiState,
    wording: VoiceSetupWording,
    onSelectEngine: (String?) -> Unit,
) {
    MiraSectionHeader(text = wording.engineSection)
    MiraListRow(
        title = wording.systemDefault,
        supporting = wording.systemDefaultDetail,
        onClick = { onSelectEngine(null) },
        leading = {
            SelectionMark(
                selected = state.preferences.engine == null,
                selectedTint = MiraTheme.colors.accentDim,
            )
        },
    )
    // Between rows only. A rule under the last one is a line with nothing beneath it to
    // separate, and it closed the section with a box edge the section does not have.
    state.engines.forEach { engine ->
        MiraDivider()
        MiraListRow(
            title = engine.label,
            supporting = engine.packageName,
            onClick = { onSelectEngine(engine.packageName) },
            leading = {
                SelectionMark(
                    selected = engine.packageName == state.preferences.engine,
                    selectedTint = MiraTheme.colors.accentDim,
                )
            },
        )
    }
}

/**
 * One language's voices.
 *
 * The row is a single target on purpose: tapping it plays the voice and keeps it. A separate
 * "play" button beside a separate "select" button would make Abu choose a voice he has not heard,
 * which is the one thing this screen exists to prevent. The trailing glyph reports which voice is
 * speaking; it is not a second control.
 */
@Composable
private fun VoiceSection(
    heading: String,
    language: SpokenLanguage,
    state: VoiceSetupUiState,
    wording: VoiceSetupWording,
    missing: Boolean,
    missingMessage: String,
    onChoose: (String, SpokenLanguage) -> Unit,
    onOpenSystemSettings: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val chosen = state.preferences.voiceIdFor(language)
    MiraSectionHeader(text = heading)
    when {
        state.voicesLoading -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.md),
            horizontalArrangement = Arrangement.Center,
        ) {
            MiraLoadingIndicator()
        }

        missing -> MiraCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = missingMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md))
            MiraButton(
                style = MiraButtonStyle.SECONDARY,
                text = wording.openTextToSpeechSettings,
                onClick = onOpenSystemSettings,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> state.voicesFor(language).forEachIndexed { index, voice ->
            if (index > 0) MiraDivider()
            MiraListRow(
                title = voice.displayLabel,
                supporting = wording.attributes(voice),
                onClick = { onChoose(voice.name, language) },
                leading = { SelectionMark(selected = voice.name == chosen) },
                trailing = {
                    if (state.previewing == voice.name) {
                        MiraLoadingIndicator()
                    } else {
                        Icon(
                            imageVector = MiraIcons.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

/**
 * Which row of a group is the chosen one.
 *
 * [selectedTint] exists because three of these are on screen at once — an engine, an English voice
 * and a Bengali voice — and three champagne marks beside a champagne switch track is the accent in
 * four places, which is the accent nowhere (AGENTS.md §10). The two voices are what this screen is
 * for and keep it; the engine is the container that choice is made inside, so it is dimmed.
 */
@Composable
private fun SelectionMark(
    selected: Boolean,
    selectedTint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        imageVector = if (selected) {
            MiraIcons.RadioButtonChecked
        } else {
            MiraIcons.RadioButtonUnchecked
        },
        contentDescription = null,
        tint = if (selected) selectedTint else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    wording: VoiceSetupWording,
    onChange: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // The number is the answer and the word beside it is only the question, so the two
            // are separated by size and weight rather than by a colour spent on the accent.
            Text(
                text = wording.multiplier(value),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        MiraSettingSlider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            onValueChangeFinished = onCommit,
        )
    }
}

/**
 * Material's slider in Mira's colours, in one place: speech rate and pitch share it, so the two
 * cannot drift apart and a third slider added later cannot arrive with Material's own purple.
 *
 * Neither part of it is the accent. The track is the dim champagne so it still reads as filled,
 * and the thumb is ivory: a slider here is a handle, not an answer — the number above it, set at
 * title weight, is the answer — and two champagne thumbs beside three selection marks and a
 * switch put champagne in six places on one screenful, which is champagne in none (AGENTS.md §10).
 *
 * This wants to be `MiraSlider` in `core/design/component`. It cannot move from here: the design
 * system is owned elsewhere, and a private copy in a screen file is exactly the failure §11 names.
 */
@Composable
private fun MiraSettingSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.onBackground,
            activeTrackColor = MiraTheme.colors.accentDim,
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    )
}

/**
 * The TTS settings screen has no public action constant, so the documented Settings action is
 * tried first and the accessibility screen — which always exists and lists text-to-speech — is
 * the fallback.
 */
private fun Context.openTextToSpeechSettings(
    wording: VoiceSetupWording,
    onFailure: (String) -> Unit,
) {
    val candidates = listOf(
        Intent(TTS_SETTINGS_ACTION),
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
    )
    for (intent in candidates) {
        try {
            startActivity(intent)
            return
        } catch (missing: ActivityNotFoundException) {
            continue
        }
    }
    onFailure(wording.noTextToSpeechSettingsScreen)
}

private const val TTS_SETTINGS_ACTION = "com.android.settings.TTS_SETTINGS"
