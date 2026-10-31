package com.rayluxeo.aida.ui.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rayluxeo.aida.core.design.MiraMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraAvatar
import com.rayluxeo.aida.core.design.component.MiraAvatarState
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * How far through Abu is, as one line rather than as twelve.
 *
 * It was a row of twelve separate dashes, and twelve marks is a ladder to be counted rather than
 * a position to be felt — at step two, ten dark gaps read as work outstanding. One continuous
 * track with a champagne fill says the same thing without asking to be read, and it is the shape
 * the eye already knows from a progress line.
 *
 * The fill is a **spring**, because it moves (`MiraMotion`): stepping forward twice quickly
 * carries the first movement's velocity into the second instead of restarting it.
 *
 * The bar still carries the whole position as a single spoken description — "Step 4 of 12", or
 * "ধাপ ৪ / ১২" — because that is the actual information, where the geometry is not.
 */
@Composable
internal fun OnboardingProgress(
    step: OnboardingStep,
    wording: OnboardingWording,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val position = wording.stepPosition(step.ordinal + 1, OnboardingStep.count)
    val reached by animateFloatAsState(
        targetValue = (step.ordinal + 1).toFloat() / OnboardingStep.count,
        animationSpec = MiraMotion.move(),
        label = "OnboardingProgress",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = position }
            .height(spacing.hairline * TRACK_HAIRLINES)
            .clip(CircleShape)
            .background(MiraTheme.colors.hairlineStrong),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                // Coerced because a taut spring may pass its target by a hair, and a fraction
                // over one is not a width.
                .fillMaxWidth(reached.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/**
 * The shape of every pane: an optional mark, one large title, a lead paragraph, a quieter detail,
 * and then whatever that step needs. Keeping the frame identical across twelve screens is what
 * makes the flow feel like one object rather than twelve.
 *
 * The two paragraphs are deliberately **not** set the same. They were, and a pane then read as one
 * undifferentiated grey block: the lead is the sentence Abu has to take in, so it carries the
 * page's own text colour, and the detail sits below it at body size in the muted tone. The
 * intervals follow from that — the two paragraphs are related and sit close, and the gap before
 * whatever the step actually asks for is the large one.
 *
 * [mark] is a slot rather than a flag so the pane never learns what Mira's mark is; the welcome
 * step passes the avatar, and every other step passes nothing.
 */
@Composable
internal fun OnboardingPane(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    mark: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    var order = 0
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(Modifier.height(spacing.xxl))
        if (mark != null) {
            OnboardingReveal(order++) { mark() }
            Spacer(Modifier.height(spacing.lg))
        }
        OnboardingReveal(order++) {
            Text(
                text = title,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(spacing.lg))
        OnboardingReveal(order++) {
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        if (detail != null) {
            Spacer(Modifier.height(spacing.sm))
            OnboardingReveal(order++) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (content != null) {
            Spacer(Modifier.height(spacing.xl))
            OnboardingReveal(order++) { content() }
        }
    }
}

/**
 * Each line of a pane arrives in turn: it fades up on a curve and rises into place on a spring,
 * a beat behind the one above it. The stagger is what makes a pane feel spoken rather than
 * printed — the words land one after another, the way Mira would say them. Keyed to its own
 * appearance, so the `Crossfade` between steps re-runs it for every pane.
 *
 * Fade is a curve and the rise is a spring, as §11 asks: the opacity settles, the position springs.
 *
 * The holder is a [Column], not a [Box]: a step's content slot may emit more than one child — the
 * language pane stacks a segmented control over its footnote, the privacy pane a card over its own —
 * and a Box would lay those on top of one another. A Column stacks them as the author wrote them,
 * and a single child renders identically either way.
 */
@Composable
private fun OnboardingReveal(order: Int, content: @Composable () -> Unit) {
    val fade = remember { Animatable(0f) }
    val rise = remember { Animatable(1f) }
    val risePx = with(LocalDensity.current) { REVEAL_RISE.toPx() }
    LaunchedEffect(Unit) {
        delay(order * REVEAL_STAGGER_MS)
        launch { fade.animateTo(1f, MiraMotion.fade(MiraMotion.STANDARD_MS)) }
        launch { rise.animateTo(0f, MiraMotion.settle()) }
    }
    Column(
        modifier = Modifier.graphicsLayer {
            alpha = fade.value
            translationY = rise.value * risePx
        },
    ) {
        content()
    }
}

private val REVEAL_RISE = 14.dp
private const val REVEAL_STAGGER_MS = 70L

/**
 * Short statements that have to be read rather than skimmed, set apart from the paragraphs.
 *
 * The bullets are the dim champagne rather than the accent itself: three of them beside the
 * pane's one filled button made four champagne things on a screen, and champagne used in four
 * places is not an accent any more (AGENTS.md §10). Dimmed, they read as texture holding the
 * list together, which is all a bullet is for.
 */
@Composable
internal fun OnboardingPoints(points: List<String>, modifier: Modifier = Modifier) {
    val spacing = MiraTheme.spacing
    MiraCard(modifier = modifier.fillMaxWidth()) {
        points.forEachIndexed { index, point ->
            if (index > 0) Spacer(Modifier.height(spacing.md))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .padding(top = spacing.xs)
                        .size(spacing.xxs)
                        .clip(CircleShape)
                        .background(MiraTheme.colors.accentDim),
                )
                Spacer(Modifier.width(spacing.sm))
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * The anchored actions. A pane carries at most two: the way forward, and the one alternative that
 * step actually offers — granting something, or opening the system screen that can.
 *
 * Only a bordered alternative takes the full width. A label-only one does not, because a
 * full-width champagne label directly beneath a full-width champagne fill reads as a second
 * button of equal weight, which is the opposite of what "Skip for now" is.
 */
@Composable
internal fun OnboardingActions(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    alternativeText: String? = null,
    alternativeStyle: MiraButtonStyle = MiraButtonStyle.TEXT,
    onAlternative: (() -> Unit)? = null,
) {
    val spacing = MiraTheme.spacing
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MiraButton(
            text = primaryText,
            onClick = onPrimary,
            modifier = Modifier.fillMaxWidth(),
            enabled = primaryEnabled,
        )
        if (alternativeText != null && onAlternative != null) {
            Spacer(Modifier.height(spacing.sm))
            MiraButton(
                text = alternativeText,
                onClick = onAlternative,
                modifier = if (alternativeStyle == MiraButtonStyle.TEXT) {
                    Modifier
                } else {
                    Modifier.fillMaxWidth()
                },
                style = alternativeStyle,
            )
        }
    }
}

/** Two hairlines: thin enough to read as a rule, thick enough to hold a rounded end. */
private const val TRACK_HAIRLINES = 2

@Preview
@Composable
private fun OnboardingPanePreview() {
    MiraPreviewSurface {
        val wording = EnglishOnboardingWording
        OnboardingProgress(step = OnboardingStep.MICROPHONE, wording = wording)
        OnboardingPane(
            title = wording.privacyTitle,
            body = wording.privacyBody,
            detail = wording.privacyFootnote,
            mark = { MiraAvatar(state = MiraAvatarState.IDLE, amplitude = 0f) },
        ) {
            OnboardingPoints(points = wording.privacyPoints)
        }
        OnboardingActions(
            primaryText = wording.continueOn,
            onPrimary = {},
            alternativeText = wording.skipForNow,
            onAlternative = {},
        )
    }
}
