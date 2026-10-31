package com.rayluxeo.aida.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The scale, set to Apple's proportions.
 *
 * iOS builds its hierarchy from a **bold large title** and a **17pt regular body**, with titles at
 * semibold — confidence comes from weight and size, not from ornament. So the display styles here
 * are bold and tight (large text is read as a shape, and negative tracking is what makes it look
 * set), the body sits at 17sp, and section labels are the small upper-cased greys iOS groups its
 * lists with.
 *
 * Line height stays a little generous because Bengali — which falls through to Noto Serif Bengali — carries
 * matras above the line and descenders below, and a leading tuned for Latin alone would crowd it.
 * [LineHeightStyle] trims the first and last line so that air never becomes dead space in a card.
 */
private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

internal fun miraTypography(family: FontFamily): Typography = Typography(
    // Large title and its steps down — bold, tight. The greeting, a screen title, a big figure.
    displayLarge = TextStyle(
        fontFamily = family,
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
        lineHeightStyle = Trim,
    ),
    displayMedium = TextStyle(
        fontFamily = family,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = Trim,
    ),
    displaySmall = TextStyle(
        fontFamily = family,
        fontSize = 26.sp,
        lineHeight = 33.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
        lineHeightStyle = Trim,
    ),
    headlineLarge = TextStyle(
        fontFamily = family,
        fontSize = 23.sp,
        lineHeight = 29.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
        lineHeightStyle = Trim,
    ),
    headlineMedium = TextStyle(
        fontFamily = family,
        fontSize = 21.sp,
        lineHeight = 27.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = Trim,
    ),
    headlineSmall = TextStyle(
        fontFamily = family,
        fontSize = 19.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = Trim,
    ),

    // Titles — a row's lead line, a card heading. Semibold, the iOS emphasis weight.
    titleLarge = TextStyle(
        fontFamily = family,
        fontSize = 17.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeightStyle = Trim,
    ),
    titleMedium = TextStyle(
        fontFamily = family,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = Trim,
    ),
    titleSmall = TextStyle(
        fontFamily = family,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeightStyle = Trim,
    ),

    // Body — 17pt regular, the iOS reading size, with its steps down.
    bodyLarge = TextStyle(
        fontFamily = family,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = Trim,
    ),
    bodyMedium = TextStyle(
        fontFamily = family,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = Trim,
    ),
    bodySmall = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
        lineHeightStyle = Trim,
    ),

    // Labels — button text and the small upper-cased greys that head grouped sections.
    labelLarge = TextStyle(
        fontFamily = family,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
        lineHeightStyle = Trim,
    ),
    labelMedium = TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.2.sp,
        lineHeightStyle = Trim,
    ),
    labelSmall = TextStyle(
        fontFamily = family,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.6.sp,
        lineHeightStyle = Trim,
    ),
)
