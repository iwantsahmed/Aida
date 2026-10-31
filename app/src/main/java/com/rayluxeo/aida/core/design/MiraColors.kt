package com.rayluxeo.aida.core.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Mira's fixed palette. The app is AMOLED-dark only — there is no light variant.
 *
 * The accent is a single, restrained **ice-blue**: near-monochrome by design, because premium on
 * a black screen comes from cool neutrals and precise hierarchy, not from colour spread across
 * every surface. The accent earns one moment per screen — an active state, a primary action, the
 * voice mark — and everything else is platinum text, cool greys and hairlines on obsidian.
 */
object MiraPalette {
    val Obsidian = Color(0xFF080808)
    val Carbon = Color(0xFF101013)
    val Graphite = Color(0xFF191A1E)
    val Accent = Color(0xFFA6C8E4)
    val Platinum = Color(0xFFF1F3F7)
    val Muted = Color(0xFF878B93)

    /** Derived tones. Kept here so no screen invents its own colour. */
    val AccentDim = Color(0xFF5E748C)
    val AccentWash = Color(0x1FA6C8E4)
    val Hairline = Color(0x12F1F3F7)
    val HairlineStrong = Color(0x24F1F3F7)
    val Scrim = Color(0xCC000000)
    val Danger = Color(0xFFCC6A5C)
    val Positive = Color(0xFF7FA37A)

    /**
     * The aurora hues — the one sanctioned departure from the ice-blue rule above.
     *
     * Abu asked on 16 October 2026 for a Gemini-style animated backdrop (AGENTS.md §10), which
     * reverses this file's own "no purple" stance for exactly one surface: [MiraAmbientBackground].
     * These are its colours and nothing else's — they are NEVER a UI accent, a text colour or a
     * fill. An active state, a button or the voice mark is still ice-blue; the purple lives only in
     * the breathing light behind the content. Kept here, not invented in the component, so §11 holds.
     */
    val AuroraMagenta = Color(0xFFE05CC9)
    val AuroraViolet = Color(0xFF9B5DE5)
    val AuroraIndigo = Color(0xFF5B3F9E)
}

@Immutable
data class MiraExtendedColors(
    val hairline: Color,
    val hairlineStrong: Color,
    val accentWash: Color,
    val accentDim: Color,
    val danger: Color,
    val positive: Color,
    val scrim: Color,
    // The aurora hues, read only by MiraAmbientBackground. See MiraPalette for why they exist.
    val auroraMagenta: Color,
    val auroraViolet: Color,
    val auroraIndigo: Color,
)

internal val MiraExtendedDark = MiraExtendedColors(
    hairline = MiraPalette.Hairline,
    hairlineStrong = MiraPalette.HairlineStrong,
    accentWash = MiraPalette.AccentWash,
    accentDim = MiraPalette.AccentDim,
    danger = MiraPalette.Danger,
    positive = MiraPalette.Positive,
    scrim = MiraPalette.Scrim,
    auroraMagenta = MiraPalette.AuroraMagenta,
    auroraViolet = MiraPalette.AuroraViolet,
    auroraIndigo = MiraPalette.AuroraIndigo,
)

internal val MiraColorScheme: ColorScheme = darkColorScheme(
    primary = MiraPalette.Accent,
    onPrimary = MiraPalette.Obsidian,
    primaryContainer = MiraPalette.AccentDim,
    onPrimaryContainer = MiraPalette.Platinum,
    secondary = MiraPalette.Platinum,
    onSecondary = MiraPalette.Obsidian,
    secondaryContainer = MiraPalette.Graphite,
    onSecondaryContainer = MiraPalette.Platinum,
    tertiary = MiraPalette.Accent,
    onTertiary = MiraPalette.Obsidian,
    background = MiraPalette.Obsidian,
    onBackground = MiraPalette.Platinum,
    surface = MiraPalette.Carbon,
    onSurface = MiraPalette.Platinum,
    surfaceVariant = MiraPalette.Graphite,
    onSurfaceVariant = MiraPalette.Muted,
    surfaceContainerLowest = MiraPalette.Obsidian,
    surfaceContainerLow = MiraPalette.Carbon,
    surfaceContainer = MiraPalette.Carbon,
    surfaceContainerHigh = MiraPalette.Graphite,
    surfaceContainerHighest = MiraPalette.Graphite,
    outline = MiraPalette.HairlineStrong,
    outlineVariant = MiraPalette.Hairline,
    error = MiraPalette.Danger,
    onError = MiraPalette.Obsidian,
    scrim = MiraPalette.Scrim,
)
