package com.rayluxeo.aida.ui.alarms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.MiraSpacing
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface

/**
 * The two lines the Alarms and Timers screens put above and below their content.
 *
 * Both live here rather than at the foot of one of the two screen files, because both screens
 * show both: a timer and an alarm are withheld by the same permission and report themselves in
 * the same voice, and a shared piece hiding inside one caller is how two copies start.
 */

/**
 * What a list has to leave free at its foot for [AlarmsNoticeBar].
 *
 * The bar settles over the content rather than pushing it, so the room is reserved rather than
 * occupied: the bar's own height — a 48dp dismiss target with one step of air above and below it
 * — plus the gap it floats at and one interval of clearance, so the last alarm is never read
 * through a confirmation. It is worked out here, beside the bar it measures, because the two
 * screens that show the bar had each guessed at this separately and both guessed 16dp short.
 */
internal val alarmsNoticeInset: Dp
    @Composable get() = with(MiraTheme.spacing) { xs + xxl + xs + noticeFloat + sm }

/** How far above the bottom edge the bar settles. Read by the bar and by the inset above it. */
private val MiraSpacing.noticeFloat: Dp get() = md

/**
 * The transient line about what just happened.
 *
 * It fades rather than appears. A notice that snaps into existence at the foot of a screen is
 * read as something going wrong even when it says an alarm was saved; a fade is the difference
 * between Mira mentioning something and Mira interrupting.
 *
 * The gap it floats at is applied here rather than by the caller, so that the height the bar
 * occupies and the height [alarmsNoticeInset] reserves cannot be changed apart from each other.
 */
@Composable
internal fun AlarmsNoticeBar(
    notice: AlarmsNotice?,
    words: AlarmsWording,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The line has to outlive its own dismissal: `notice` is already null while the fade out is
    // still running, and text that blanks before it leaves reads as a glitch rather than a fade.
    // Written from an effect rather than during composition, so nothing is read in the same pass
    // it was set in.
    var showing by remember { mutableStateOf(notice) }
    LaunchedEffect(notice) {
        if (notice != null) showing = notice
    }

    AnimatedVisibility(
        visible = notice != null,
        modifier = modifier.padding(bottom = MiraTheme.spacing.noticeFloat),
        enter = fadeIn(MiraMotion.fade()),
        exit = fadeOut(MiraMotion.fade(MiraMotion.QUICK_MS)),
    ) {
        showing?.let { shown ->
            MiraInlineNotice(
                text = shown.message,
                tone = when (shown.tone) {
                    AlarmsNoticeTone.CONFIRM -> MiraNoticeTone.CONFIRM
                    AlarmsNoticeTone.PROBLEM -> MiraNoticeTone.DANGER
                },
                dismissContentDescription = words.dismiss,
                onDismiss = onDismiss,
            )
        }
    }
}

/**
 * The one honest thing to say when Android has taken exact alarms away.
 *
 * Said once, in one voice: a timer and an alarm are withheld by the same permission, and wording
 * it two ways would suggest they were two problems. One sentence, too — [MiraInlineNotice]
 * measures its action before the text, so what is left for the words is narrow, and this notice
 * is danger-coloured and sits above everything else on two screens.
 */
@Composable
internal fun ExactAlarmsNotice(
    words: AlarmsWording,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraInlineNotice(
        text = words.exactAlarmsDenied,
        modifier = modifier,
        tone = MiraNoticeTone.DANGER,
        actionText = words.openExactAlarmSettings,
        onAction = onOpenSettings,
    )
}

@Preview
@Composable
private fun AlarmsNoticesPreview() {
    MiraPreviewSurface {
        val words = alarmsWording(SpokenLanguage.ENGLISH)
        AlarmsNoticeBar(
            notice = AlarmsNotice(words.alarmSaved, AlarmsNoticeTone.CONFIRM),
            words = words,
            onDismiss = {},
        )
        ExactAlarmsNotice(words = words, onOpenSettings = {})
    }
}
