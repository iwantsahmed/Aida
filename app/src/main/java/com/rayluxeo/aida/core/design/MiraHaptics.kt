package com.rayluxeo.aida.core.design

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView

/**
 * One decision about how Mira feels under the thumb, so a tap in Tasks and a tap in Vault are
 * the same small event and no screen invents its own buzz.
 *
 * The host view is asked first because it carries the device's own tuned effects — on Abu's
 * S24 Ultra those are noticeably crisper than the generic vibration. Compose's [HapticFeedback]
 * is the fallback for hosts that decline the request, which is what previews and tests do.
 */
@Stable
class MiraHaptics internal constructor(
    private val fallback: HapticFeedback,
    private val view: View,
    private val enabled: Boolean,
) {
    /** A light tick: a row opening, a button going down, a segment moving. */
    fun tap() = perform(HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackType.ContextClick)

    /** Something Abu asked for has landed — a task created, a reminder set. */
    fun confirm() = perform(HapticFeedbackConstants.CONFIRM, HapticFeedbackType.Confirm)

    /** Mira could not do it. Deliberately distinct from [confirm] so the hand knows before the eye. */
    fun error() = perform(HapticFeedbackConstants.REJECT, HapticFeedbackType.Reject)

    private fun perform(constant: Int, type: HapticFeedbackType) {
        if (!enabled || !view.isHapticFeedbackEnabled) return
        if (!view.performHapticFeedback(constant)) fallback.performHapticFeedback(type)
    }
}

/**
 * Abu's haptic setting, provided once at the app root. It lives as a composition local so the
 * switch in Settings reaches all nine call sites without every component taking a flag it would
 * only pass along — and so that turning it off genuinely stops the buzzing.
 */
val LocalHapticsEnabled = compositionLocalOf { true }

/** Ties [MiraHaptics] to the current composition's view. Cheap enough to call per component. */
@Composable
fun rememberMiraHaptics(): MiraHaptics {
    val fallback = LocalHapticFeedback.current
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(fallback, view, enabled) { MiraHaptics(fallback, view, enabled) }
}
