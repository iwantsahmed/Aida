package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.design.component.MiraIcons

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.domain.brief.BriefKind
import com.rayluxeo.aida.ui.onboarding.hasPermissions
import com.rayluxeo.aida.ui.onboarding.notificationsEnabled
import com.rayluxeo.aida.ui.onboarding.notificationsRequestable
import com.rayluxeo.aida.ui.onboarding.openNotificationSettings
import java.time.LocalTime

/**
 * The two moments Mira may speak up unasked, and the one thing that can silence her.
 *
 * Both briefs are off until Abu turns one on, so this section is the only place either is ever
 * armed. It states the next occurrence in his own language whenever one is on, because a time he
 * changed is worth nothing if he cannot see that it took.
 *
 * Notifications are checked against the platform on every resume rather than remembered: a brief
 * switched on while notifications are blocked would look armed and stay silent for good, which is
 * the worst outcome this section can produce, so the notice appears the moment that is true.
 */
@Composable
internal fun BriefSettingsSection(
    state: SettingsUiState,
    words: BriefSettingsWording,
    onEnabledChange: (BriefKind, Boolean) -> Unit,
    onEditTime: (BriefKind) -> Unit,
    onTimeChosen: (BriefKind, LocalTime) -> Unit,
    onDismissTimeEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val spacing = MiraTheme.spacing
    val schedule = state.briefSchedule

    var notificationsOn by remember { mutableStateOf(context.notificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsOn = context.notificationsEnabled()
    }
    val permissionRequest = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        // The manager's answer, not the dialog's: a granted permission still posts nothing when
        // Mira's notifications are switched off in system settings.
        notificationsOn = context.notificationsEnabled()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        MiraSectionHeader(text = words.section)

        if (!notificationsOn && (schedule.morningEnabled || schedule.eveningEnabled)) {
            MiraInlineNotice(
                text = words.notificationsBlocked,
                tone = MiraNoticeTone.DANGER,
                actionText = words.allowNotifications,
                onAction = {
                    if (notificationsRequestable &&
                        !context.hasPermissions(listOf(Manifest.permission.POST_NOTIFICATIONS))
                    ) {
                        permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Either the permission is already held and the channel is off, or this is
                        // an Android below 33 where the dialog does not exist. Both are settled on
                        // the system screen, and a request here would return without being shown.
                        context.openNotificationSettings()
                    }
                },
            )
            Spacer(Modifier.height(spacing.xs))
        }

        ToggleRow(
            title = words.morningTitle,
            supporting = state.morningNextLabel ?: words.morningSupporting,
            icon = MiraIcons.WbSunny,
            checked = schedule.morningEnabled,
            onCheckedChange = { onEnabledChange(BriefKind.MORNING, it) },
        )
        if (schedule.morningEnabled) {
            BriefTimeRow(
                label = words.time,
                value = state.morningTimeLabel,
                onClick = { onEditTime(BriefKind.MORNING) },
            )
        }

        MiraDivider()

        ToggleRow(
            title = words.eveningTitle,
            supporting = state.eveningNextLabel ?: words.eveningSupporting,
            icon = MiraIcons.Bedtime,
            checked = schedule.eveningEnabled,
            onCheckedChange = { onEnabledChange(BriefKind.EVENING, it) },
        )
        if (schedule.eveningEnabled) {
            BriefTimeRow(
                label = words.time,
                value = state.eveningTimeLabel,
                onClick = { onEditTime(BriefKind.EVENING) },
            )
        }
    }

    when (state.editingBriefTime) {
        BriefKind.MORNING -> BriefTimeDialog(
            title = words.morningTimeTitle,
            initial = schedule.morningAt,
            words = words,
            onPick = { onTimeChosen(BriefKind.MORNING, it) },
            onDismiss = onDismissTimeEditor,
        )

        BriefKind.EVENING -> BriefTimeDialog(
            title = words.eveningTimeTitle,
            initial = schedule.eveningAt,
            words = words,
            onPick = { onTimeChosen(BriefKind.EVENING, it) },
            onDismiss = onDismissTimeEditor,
        )

        null -> Unit
    }
}

/** The hour a brief lands at, shown only while that brief is on. */
@Composable
private fun BriefTimeRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraListRow(
        title = label,
        modifier = modifier,
        onClick = onClick,
        leading = {
            Icon(
                imageVector = MiraIcons.Schedule,
                contentDescription = null,
                tint = MiraTheme.colors.accentDim,
            )
        },
        trailing = {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        },
    )
}

/**
 * Material's own picker, in Mira's dialog. Twelve-hour to match the way every other time in the
 * app is worded; the picker's own numerals follow the device locale, which is the one thing here
 * Mira does not get to choose.
 */
@Composable
private fun BriefTimeDialog(
    title: String,
    initial: LocalTime,
    words: BriefSettingsWording,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    MiraDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmText = words.set,
        onConfirm = { onPick(LocalTime.of(pickerState.hour, pickerState.minute)) },
        modifier = modifier,
        dismissText = words.cancel,
        content = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
    )
}

/**
 * Every word this section says, in the language Abu chose.
 *
 * Settings is otherwise still English-only, which is a defect of its own; nothing added here is
 * allowed to make it worse. The shape is `ui/home/HomeWording`'s: one interface, one object per
 * language, one selector. Numbers inside a next-occurrence line arrive already in Bengali
 * numerals, because [com.rayluxeo.aida.ui.tasks.TaskTimeFormatter] words the moment itself.
 */
internal interface BriefSettingsWording {
    val section: String
    val morningTitle: String
    val morningSupporting: String
    val eveningTitle: String
    val eveningSupporting: String
    val time: String
    val morningTimeTitle: String
    val eveningTimeTitle: String
    val set: String
    val cancel: String
    val notificationsBlocked: String
    val allowNotifications: String
    val scheduleRefused: String

    /** "Next — Tomorrow, 8 am", so a time he has just changed visibly took effect. */
    fun nextOccurrence(moment: String): String
}

internal object EnglishBriefSettingsWording : BriefSettingsWording {
    override val section = "Daily brief"
    override val morningTitle = "Morning brief"
    override val morningSupporting =
        "Today's tasks, your calendar and what is owed, once in the morning."
    override val eveningTitle = "Evening review"
    override val eveningSupporting =
        "What the day came to, and what tomorrow holds, once at night."
    override val time = "Time"
    override val morningTimeTitle = "Morning brief time"
    override val eveningTimeTitle = "Evening review time"
    override val set = "Set"
    override val cancel = "Cancel"
    override val notificationsBlocked =
        "Notifications are off, so a brief cannot arrive on this phone."
    override val allowNotifications = "Allow"
    override val scheduleRefused =
        "Mira could not set that alarm, so the brief will not arrive. Switch it off and on again."

    override fun nextOccurrence(moment: String) = "Next — $moment"
}

internal object BengaliBriefSettingsWording : BriefSettingsWording {
    override val section = "দৈনিক ব্রিফ"
    override val morningTitle = "সকালের ব্রিফ"
    override val morningSupporting = "আজকের কাজ, ক্যালেন্ডার আর বকেয়া — সকালে একবার।"
    override val eveningTitle = "সন্ধ্যার রিভিউ"
    override val eveningSupporting = "দিনে যা হলো আর কাল যা আছে — রাতে একবার।"
    override val time = "সময়"
    override val morningTimeTitle = "সকালের ব্রিফের সময়"
    override val eveningTimeTitle = "সন্ধ্যার রিভিউয়ের সময়"
    override val set = "ঠিক আছে"
    override val cancel = "বাতিল"
    override val notificationsBlocked = "নোটিফিকেশন বন্ধ, তাই এই ফোনে ব্রিফ আসবে না।"
    override val allowNotifications = "অনুমতি দিন"
    override val scheduleRefused =
        "আইদা অ্যালার্মটি সেট করতে পারেনি, তাই ব্রিফ আসবে না। একবার বন্ধ করে আবার চালু করুন।"

    override fun nextOccurrence(moment: String) = "পরবর্তী — $moment"
}

internal fun briefSettingsWording(language: SpokenLanguage): BriefSettingsWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishBriefSettingsWording
    SpokenLanguage.BENGALI -> BengaliBriefSettingsWording
}
