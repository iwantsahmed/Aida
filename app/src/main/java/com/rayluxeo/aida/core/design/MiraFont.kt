package com.rayluxeo.aida.core.design

import android.content.Context
import android.graphics.fonts.Font as PlatformFont
import android.graphics.fonts.FontFamily as PlatformFontFamily
import android.graphics.fonts.FontStyle
import android.graphics.Typeface as PlatformTypeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import com.rayluxeo.aida.R

/**
 * Mira's voice — **Manrope** for Latin, **Noto Serif Bengali** for Bangla — with REAL per-glyph
 * fallback between the two bundled faces.
 *
 * This is the load-bearing detail. Compose's `FontFamily` does **not** fall through from one bundled
 * font to another for a missing glyph: it picks a single font per weight and lets the *system* face
 * cover whatever that font lacks. So a Bengali face merely listed after Manrope in a `FontFamily`
 * never actually drew — Bangla came out in the device's own sans. The fix is to hand Compose a
 * single `android.graphics.Typeface` whose fallback is set **explicitly**, with
 * `Typeface.CustomFallbackBuilder` (API 29; Mira's minSdk is 31): Manrope carries every Latin glyph
 * at every weight, and Noto Serif Bengali is the fallback the Typeface itself reaches for on a
 * Bengali codepoint. Bangla is therefore always the serif, Latin always Manrope — even inside one
 * Banglish word — and never the system face.
 *
 * Everything is bundled; nothing is fetched at runtime, so the app looks identical on a plane
 * (AGENTS.md §3). Noto Serif Bengali's coverage of every Bengali codepoint Mira prints — the digits
 * ০–৯ and the taka sign ৳ among them — was verified against its `cmap` before it landed (§10).
 */
@Composable
fun rememberMiraFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember(context) { miraFontFamily(context) }
}

private fun miraFontFamily(context: Context): FontFamily =
    runCatching { FontFamily(Typeface(buildMiraTypeface(context))) }
        // If the platform ever refuses to assemble the fallback typeface, Latin still renders from
        // Manrope and Bengali falls to the system face — degraded, never a crash or tofu.
        .getOrElse { manropeOnlyFallback() }

private fun buildMiraTypeface(context: Context): PlatformTypeface {
    val manrope = PlatformFontFamily.Builder(manrope(context, R.font.manrope_regular, 400))
        .addFont(manrope(context, R.font.manrope_medium, 500))
        .addFont(manrope(context, R.font.manrope_semibold, 600))
        .addFont(manrope(context, R.font.manrope_bold, 700))
        .addFont(manrope(context, R.font.manrope_extrabold, 800))
        .build()
    val bengali = PlatformFontFamily.Builder(
        PlatformFont.Builder(context.resources, R.font.noto_serif_bengali).build(),
    ).build()
    return PlatformTypeface.CustomFallbackBuilder(manrope)
        .addCustomFallback(bengali)
        .setStyle(FontStyle(FontStyle.FONT_WEIGHT_NORMAL, FontStyle.FONT_SLANT_UPRIGHT))
        .build()
}

private fun manrope(context: Context, resId: Int, weight: Int): PlatformFont =
    PlatformFont.Builder(context.resources, resId).setWeight(weight).build()

/** Latin-only safety net; used only if the platform cannot build the fallback typeface. */
private fun manropeOnlyFallback(): FontFamily = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)
