package com.rayluxeo.aida.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraTopBar

/**
 * What Abu sees when he opens an area Mira does not have yet.
 *
 * AGENTS.md §12 forbids a screen that is reachable but empty, and this is the one governed
 * exception to it. A route that led nowhere, or a hub row that refused the tap, would tell him
 * less than a screen that names the area, says plainly it is not built, and lists what it will
 * do when it is. The single action on it is real: it hands the request to Mira, who answers for
 * herself whether she can do it today.
 *
 * The screen is mostly empty by its nature, so the emptiness has to look decided — which means
 * the content has to span the space rather than sit in a third of it. It reads as three blocks:
 * what the area is, set at the display cut so that something on the screen is larger than the top
 * bar; what it will do, on the one lit surface; and what Abu can do instead, held at the bottom
 * edge where the one action on a screen belongs. The air that is left falls between the card and
 * the action, which is a gap, rather than under everything, which is a tail.
 *
 * The action sits outside the scroll on purpose: it is the only thing here that does anything, and
 * a long capability list or a large font scale must not be able to push it off the screen.
 *
 * Every block begins on the screen's one left edge. The top bar's title does not — with a back
 * arrow it starts 40dp further in — and that is a [MiraTopBar] question, not this screen's.
 *
 * Nothing here says "soon" and nothing gives a date. An area stops reaching this screen the
 * moment its `isBuilt` flips in [MiraArea]; when the last one flips, delete this file.
 */
@Composable
fun PlannedAreaScreen(
    area: MiraArea,
    language: SpokenLanguage,
    onAskMira: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val words = shellWording(language)
    MiraScaffold(
        modifier = modifier,
        topBar = {
            MiraTopBar(
                title = words.areaTitle(area),
                onBack = onBack,
                backContentDescription = words.back,
            )
        },
    ) { padding ->
        PlannedAreaBody(
            area = area,
            words = words,
            onAskMira = onAskMira,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

/**
 * The body spans the screen: what the area is at the top, what Abu can do at the bottom, and the
 * space between them held by the two of them rather than left hanging under the last line.
 *
 * There is no hero glyph. It repeated the mark Abu had just tapped on the hub, said nothing the
 * title above it did not, and at `accentDim` on obsidian it was dimmer than the muted sentence
 * beneath it — a second champagne element that read as smudged rather than restrained. The one
 * accent on this screen is the action.
 */
@Composable
private fun PlannedAreaBody(
    area: MiraArea,
    words: ShellWording,
    onAskMira: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(spacing.xl))
            // The screen's one large thing. Set above the type scale's display floor, so it takes
            // the narrower display cut and lands well clear of the top bar's 17sp title — a
            // headline two points bigger than the chrome is not a headline.
            Text(
                text = words.areaSummary(area),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(spacing.sm))
            Text(
                text = words.notBuiltYet,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(spacing.xl))
            // A card rather than loose text under a heading: three lines floating on obsidian read
            // as the page not having finished loading, where one lit surface reads as a thing that
            // was put there.
            MiraCard(modifier = Modifier.fillMaxWidth()) {
                MiraSectionHeader(text = words.whatItWillDo)
                words.areaCapabilities(area).forEachIndexed { index, capability ->
                    if (index > 0) Spacer(Modifier.height(spacing.sm))
                    CapabilityLine(text = capability)
                }
            }
            // Inside the scroll, so that a list long enough to scroll still ends with air under it
            // rather than against the action below.
            Spacer(Modifier.height(spacing.lg))
        }

        // No leading glyph. The waveform is the mark Mira has outgrown, and a button whose label
        // already names her does not need one at all.
        MiraButton(
            text = words.askMira,
            onClick = onAskMira,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = words.askMiraHint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.lg))
    }
}

/**
 * A drawn dot rather than a bullet glyph: the marker has to sit on the first line of wrapped
 * text, and a text bullet drifts with the font the device happens to use for Bengali.
 *
 * Muted, not champagne. The accent on this screen belongs to the one action at the bottom, and
 * three lit dots beside it would have split it three ways.
 */
@Composable
private fun CapabilityLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                // Nudged down to sit on the optical centre of the first line rather than its top.
                .padding(top = spacing.xs)
                .size(spacing.xxs)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant),
        )
        Spacer(Modifier.width(spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

// A bounded canvas rather than MiraPreviewSurface: the body now holds its action against the
// bottom edge, and a preview that wraps its content has no bottom edge to hold it against.
@Preview(widthDp = 400, heightDp = 880)
@Composable
private fun PlannedAreaScreenPreview() {
    MiraTheme {
        PlannedAreaScreen(
            area = MiraArea.CALENDAR,
            language = SpokenLanguage.ENGLISH,
            onAskMira = {},
            onBack = {},
        )
    }
}
