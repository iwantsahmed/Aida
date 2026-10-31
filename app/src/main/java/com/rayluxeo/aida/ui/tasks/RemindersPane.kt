package com.rayluxeo.aida.ui.tasks

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.domain.model.ReminderKind
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal

/**
 * How the facts about one reminder read as a single line. The same separator a task row uses for
 * its own detail, because the two lists sit one tab apart and must not word themselves differently.
 */
private const val DETAIL_SEPARATOR = " · "

/**
 * One reminder as the tab shows it.
 *
 * Everything is already worded. [whenLabel] is the single line that says when — the moment, the
 * snooze Abu pushed it to, or the place it is waiting at — because those three are alternatives
 * rather than things that stack, and a row that showed both a time and a snooze would be telling
 * him two different answers to the same question.
 */
data class ReminderRow(
    val id: Long,
    val title: String,
    val kind: ReminderKind,
    val kindLabel: String,
    val whenLabel: String,
    val isSnoozed: Boolean,
    val repeats: Boolean,
)

/**
 * What Mira has to say about the places she is meant to be watching.
 *
 * [settingsFor] decides what the action does: with a refusal it opens the Android screen that
 * fixes that particular one, and without it the action is simply to try watching again. A null
 * [actionText] means there is nothing useful Abu could press, which is the honest answer for a
 * device with no location provider.
 */
data class RemindersPlaceNotice(
    val message: String,
    val actionText: String? = null,
    val settingsFor: PlaceWatchRefusal? = null,
)

/**
 * The Reminders half of the Tasks screen.
 *
 * Reminders are created by voice and by the daily sweep rather than from a button here, so the
 * pane is a list and two verbs: cancel one, or put the place watches back.
 */
@Composable
internal fun ColumnScope.RemindersPane(
    reminders: List<ReminderRow>,
    placeNotice: RemindersPlaceNotice?,
    words: TasksWording,
    reminderWords: RemindersWording,
    onRequestCancelReminder: (Long) -> Unit,
    onUnsnoozeReminder: (Long) -> Unit,
    onPlaceAction: (RemindersPlaceNotice) -> Unit,
    onDismissPlaceNotice: () -> Unit,
) {
    val spacing = MiraTheme.spacing

    placeNotice?.let { notice ->
        MiraInlineNotice(
            text = notice.message,
            modifier = Modifier.fillMaxWidth(),
            tone = MiraNoticeTone.DANGER,
            dismissContentDescription = words.dismiss,
            onDismiss = onDismissPlaceNotice,
            actionText = notice.actionText,
            onAction = if (notice.actionText != null) {
                { onPlaceAction(notice) }
            } else {
                null
            },
        )
        Spacer(Modifier.height(spacing.sm))
    }

    if (reminders.isEmpty()) {
        // Nothing waiting is the normal state of this tab, so it is centred in the space it owns
        // rather than pinned under the notice with the rest of the screen left over.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            MiraEmptyState(
                icon = MiraIcons.Alarm,
                message = words.noReminders,
            )
        }
    } else {
        LazyColumn(modifier = Modifier.weight(1f)) {
            itemsIndexed(reminders, key = { _, reminder -> reminder.id }) { position, reminder ->
                // Between rows, never after the last, and inset to where the titles start.
                if (position > 0) {
                    MiraDivider(modifier = Modifier.padding(start = spacing.xxl + spacing.md))
                }
                ReminderListRow(
                    reminder = reminder,
                    words = words,
                    reminderWords = reminderWords,
                    onCancel = { onRequestCancelReminder(reminder.id) },
                    onUnsnooze = { onUnsnoozeReminder(reminder.id) },
                )
            }
            item(key = "tail") { Spacer(Modifier.height(spacing.xxl)) }
        }
    }
}

/**
 * One reminder.
 *
 * What kind it is, when it is coming and whether it comes back are three answers to one question,
 * so they are one line under the title rather than a strip of chips below the row. The chips said
 * nothing the glyph beside the title was not already saying, they began at the screen gutter while
 * every title on the tab begins two columns in, and they gave a list of five reminders fifteen
 * pills to look at.
 *
 * Cancel keeps its word but no longer keeps the danger tone, because it no longer does the
 * damage: it asks, and the dialog's confirm is what cancels. One red word per row, five rows
 * deep, made a column of alarm out of a control Abu presses rarely — and MiraDialog already puts
 * the one danger signal on the control that actually acts. Ivory at label weight beside muted
 * supporting text is enough to read as the one thing on the row that can be pressed.
 */
@Composable
private fun ReminderListRow(
    reminder: ReminderRow,
    words: TasksWording,
    reminderWords: RemindersWording,
    onCancel: () -> Unit,
    onUnsnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    val detail = buildList {
        add(reminder.kindLabel)
        add(reminder.whenLabel)
        if (reminder.repeats) add(reminderWords.repeats)
    }.joinToString(DETAIL_SEPARATOR)

    Column(modifier = modifier.fillMaxWidth()) {
        MiraListRow(
            title = reminder.title,
            supporting = detail,
            leading = {
                // The same 48dp column the task list gives its completion control, so the two
                // tabs share one left edge and the titles do not step sideways when Abu switches
                // between them.
                Box(
                    modifier = Modifier.size(spacing.xxl),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = reminder.kind.icon(),
                        contentDescription = null,
                        // A snoozed reminder is deliberately quieter than one that is still on
                        // its own moment: it is waiting rather than coming.
                        tint = if (reminder.isSnoozed) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MiraTheme.colors.accentDim
                        },
                    )
                }
            },
            trailing = {
                MiraButton(
                    style = MiraButtonStyle.TEXT,
                    text = words.cancel,
                    onClick = onCancel,
                    accent = MaterialTheme.colorScheme.onBackground,
                )
            },
        )
        // Only for a reminder that is actually snoozed: there is nothing to call off on one that
        // is sitting on its own moment. Indented to the title's left edge — the glyph column plus
        // its gap, less the padding the text button carries itself — so the line it belongs to is
        // obvious without drawing anything.
        if (reminder.isSnoozed) {
            MiraButton(
                style = MiraButtonStyle.TEXT,
                text = reminderWords.unsnooze,
                onClick = onUnsnooze,
                modifier = Modifier.padding(start = spacing.xxl + spacing.md - spacing.sm),
            )
        }
    }
}

/**
 * The glyph for a kind.
 *
 * An icon is not wording, so it is chosen here rather than in a phrasebook — there is one set of
 * glyphs whichever language the row is written in.
 */
@Composable
private fun ReminderKind.icon(): ImageVector = when (this) {
    ReminderKind.ONE_TIME -> MiraIcons.Alarm
    ReminderKind.RECURRING -> MiraIcons.Repeat
    ReminderKind.TASK -> MiraIcons.TaskAlt
    ReminderKind.INVOICE -> MiraIcons.ReceiptLong
    ReminderKind.CLIENT_FOLLOW_UP -> MiraIcons.PersonPin
    ReminderKind.BIRTHDAY -> MiraIcons.Cake
    ReminderKind.PROJECT_DEADLINE -> MiraIcons.Event
    ReminderKind.SUBSCRIPTION -> MiraIcons.Autorenew
    ReminderKind.PLACE -> MiraIcons.Place
}

@Preview
@Composable
private fun ReminderListRowPreview() {
    MiraPreviewSurface {
        val words = tasksWording(SpokenLanguage.ENGLISH)
        val reminderWords = remindersWording(SpokenLanguage.ENGLISH)
        ReminderListRow(
            reminder = ReminderRow(
                id = 1L,
                title = "Chase invoice INV-0142",
                kind = ReminderKind.INVOICE,
                kindLabel = reminderWords.kindLabel(ReminderKind.INVOICE),
                whenLabel = reminderWords.at("Tomorrow, 10 am"),
                isSnoozed = false,
                repeats = false,
            ),
            words = words,
            reminderWords = reminderWords,
            onCancel = {},
            onUnsnooze = {},
        )
        ReminderListRow(
            reminder = ReminderRow(
                id = 2L,
                title = "Buy printer ink",
                kind = ReminderKind.PLACE,
                kindLabel = reminderWords.kindLabel(ReminderKind.PLACE),
                whenLabel = "${reminderWords.waitingAt("the office")} — ${reminderWords.notBeingWatched}",
                isSnoozed = false,
                repeats = false,
            ),
            words = words,
            reminderWords = reminderWords,
            onCancel = {},
            onUnsnooze = {},
        )
    }
}
