package com.rayluxeo.aida.ui.tasks.detail

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraPreviewSurface
import com.rayluxeo.aida.domain.task.RecurrenceRule
import com.rayluxeo.aida.ui.tasks.TaskDatePickerDialog
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The shapes of repeat the picker offers.
 *
 * A flat list of what Abu can choose, rather than [RecurrenceRule]'s own variants, because the
 * picker has to name the absence of a repeat as one of the choices and the rule type has no
 * variant for "never" — the rule for that is simply null.
 */
enum class RecurrenceKind { NONE, DAILY, WEEKLY, WEEKDAYS, MONTHLY, YEARLY, EVERY_N_DAYS }

/** How high the interval stepper goes. Past this a repeat is a date, not a rhythm. */
private const val MAX_INTERVAL = 30

/** Which kind of repeat a rule is, so the picker can show the choice already made. */
internal fun RecurrenceRule?.editorKind(): RecurrenceKind = when (this) {
    null -> RecurrenceKind.NONE
    is RecurrenceRule.Daily -> RecurrenceKind.DAILY
    is RecurrenceRule.Weekly -> RecurrenceKind.WEEKLY
    is RecurrenceRule.Weekdays -> RecurrenceKind.WEEKDAYS
    is RecurrenceRule.Monthly -> RecurrenceKind.MONTHLY
    is RecurrenceRule.Yearly -> RecurrenceKind.YEARLY
    is RecurrenceRule.EveryNDays -> RecurrenceKind.EVERY_N_DAYS
}

/**
 * The rule for [this] kind, carrying over what still applies from [previous].
 *
 * Switching between kinds keeps the interval and the end date, so changing "every 2 weeks" to
 * months leaves Abu with "every 2 months" rather than silently resetting to one. [anchor] is the
 * day the task falls due, which is the only honest source for the day of the month.
 */
internal fun RecurrenceKind.ruleFrom(previous: RecurrenceRule?, anchor: LocalDate): RecurrenceRule? {
    val interval = previous?.interval?.coerceAtLeast(1) ?: 1
    val until = previous?.until
    return when (this) {
        RecurrenceKind.NONE -> null
        RecurrenceKind.DAILY -> RecurrenceRule.Daily(interval, until)
        RecurrenceKind.WEEKLY -> RecurrenceRule.Weekly(
            days = (previous as? RecurrenceRule.Weekly)?.days ?: emptySet(),
            interval = interval,
            until = until,
        )
        RecurrenceKind.WEEKDAYS -> RecurrenceRule.Weekdays(until)
        RecurrenceKind.MONTHLY -> RecurrenceRule.Monthly(anchor.dayOfMonth, interval, until)
        RecurrenceKind.YEARLY -> RecurrenceRule.Yearly(interval, until)
        RecurrenceKind.EVERY_N_DAYS -> RecurrenceRule.EveryNDays(interval.coerceAtLeast(2), until)
    }
}

/** Whether the interval stepper applies. Weekdays means Monday to Friday and nothing else. */
internal fun RecurrenceRule.hasInterval(): Boolean = this !is RecurrenceRule.Weekdays

/** Every day is [RecurrenceRule.Daily]'s job, so the escape hatch starts where it stops being one. */
internal fun RecurrenceRule.minimumInterval(): Int = if (this is RecurrenceRule.EveryNDays) 2 else 1

internal fun RecurrenceRule.withInterval(interval: Int): RecurrenceRule {
    val clamped = interval.coerceIn(1, MAX_INTERVAL)
    return when (this) {
        is RecurrenceRule.Daily -> copy(interval = clamped)
        is RecurrenceRule.Weekly -> copy(interval = clamped)
        is RecurrenceRule.Weekdays -> this
        is RecurrenceRule.Monthly -> copy(interval = clamped)
        is RecurrenceRule.Yearly -> copy(interval = clamped)
        is RecurrenceRule.EveryNDays -> copy(interval = clamped.coerceAtLeast(2))
    }
}

internal fun RecurrenceRule.withUntil(until: LocalDate?): RecurrenceRule = when (this) {
    is RecurrenceRule.Daily -> copy(until = until)
    is RecurrenceRule.Weekly -> copy(until = until)
    is RecurrenceRule.Weekdays -> RecurrenceRule.Weekdays(until)
    is RecurrenceRule.Monthly -> copy(until = until)
    is RecurrenceRule.Yearly -> copy(until = until)
    is RecurrenceRule.EveryNDays -> copy(until = until)
}

/** Toggling the last remaining day leaves the set empty, which means "the same day every week". */
internal fun RecurrenceRule.withWeekdayToggled(day: DayOfWeek): RecurrenceRule {
    if (this !is RecurrenceRule.Weekly) return this
    return copy(days = if (day in days) days - day else days + day)
}

/**
 * How often a task comes back.
 *
 * Every change is applied straight away rather than held until a confirm, so the next few dates
 * under the controls move as Abu moves them. Seeing Friday, Friday, Friday is how he knows he set
 * up what he meant; a preview that only appears after the sheet closes would tell him too late.
 */
@Composable
internal fun RecurrencePicker(
    rule: RecurrenceRule?,
    preview: List<String>,
    anchorDate: LocalDate,
    words: TaskDetailWording,
    onRuleChange: (RecurrenceRule?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEndPicker by remember { mutableStateOf(false) }

    MiraBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = words.fieldName(TaskDetailField.RECURRENCE),
    ) {
        RecurrenceBody(
            rule = rule,
            preview = preview,
            anchorDate = anchorDate,
            words = words,
            onRuleChange = onRuleChange,
            onPickEndDate = { showEndPicker = true },
            onDone = onDismiss,
        )
    }

    if (showEndPicker) {
        TaskDatePickerDialog(
            initialDate = rule?.until ?: anchorDate,
            setText = words.set,
            cancelText = words.cancel,
            onPick = { picked -> rule?.let { onRuleChange(it.withUntil(picked)) } },
            onDismiss = { showEndPicker = false },
        )
    }
}

@Composable
private fun ColumnScope.RecurrenceBody(
    rule: RecurrenceRule?,
    preview: List<String>,
    anchorDate: LocalDate,
    words: TaskDetailWording,
    onRuleChange: (RecurrenceRule?) -> Unit,
    onPickEndDate: () -> Unit,
    onDone: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    val kind = rule.editorKind()

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        RecurrenceKind.entries.forEach { option ->
            MiraChip(
                text = words.repeatKindLabel(option),
                selected = option == kind,
                onClick = { onRuleChange(option.ruleFrom(rule, anchorDate)) },
            )
        }
    }

    if (rule == null) {
        Spacer(Modifier.height(spacing.xs))
        MiraButton(text = words.done, onClick = onDone, modifier = Modifier.fillMaxWidth())
        return
    }

    if (rule is RecurrenceRule.Weekly) {
        Spacer(Modifier.height(spacing.xs))
        WeekdayRow(
            selected = rule.days,
            words = words,
            onToggle = { onRuleChange(rule.withWeekdayToggled(it)) },
        )
    }

    if (rule.hasInterval()) {
        Spacer(Modifier.height(spacing.sm))
        IntervalStepper(
            rule = rule,
            words = words,
            onIntervalChange = { onRuleChange(rule.withInterval(it)) },
        )
    }

    Spacer(Modifier.height(spacing.sm))
    Text(
        text = words.repeatEndsLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(spacing.xxs))
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
        MiraChip(
            text = words.repeatEndsNever,
            selected = rule.until == null,
            onClick = { onRuleChange(rule.withUntil(null)) },
        )
        MiraChip(
            text = words.repeatEndsOn,
            selected = rule.until != null,
            onClick = onPickEndDate,
        )
    }

    if (preview.isNotEmpty()) {
        Spacer(Modifier.height(spacing.md))
        MiraDivider()
        Spacer(Modifier.height(spacing.sm))
        Text(
            text = words.repeatPreviewTitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(spacing.xxs))
        preview.forEach { date ->
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    Spacer(Modifier.height(spacing.lg))
    MiraButton(text = words.done, onClick = onDone, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun WeekdayRow(
    selected: Set<DayOfWeek>,
    words: TaskDetailWording,
    onToggle: (DayOfWeek) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xxs),
    ) {
        DayOfWeek.entries.forEach { day ->
            MiraChip(
                text = words.weekdayShort(day),
                selected = day in selected,
                onClick = { onToggle(day) },
            )
        }
    }
}

@Composable
private fun IntervalStepper(
    rule: RecurrenceRule,
    words: TaskDetailWording,
    onIntervalChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = words.repeatEveryLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(spacing.sm))
        MiraIconButton(
            icon = MiraIcons.Remove,
            contentDescription = words.repeatFewer,
            onClick = { onIntervalChange(rule.interval - 1) },
            enabled = rule.interval > rule.minimumInterval(),
        )
        Text(
            text = words.number(rule.interval),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        MiraIconButton(
            icon = MiraIcons.Add,
            contentDescription = words.repeatMore,
            onClick = { onIntervalChange(rule.interval + 1) },
            enabled = rule.interval < MAX_INTERVAL,
        )
    }
}

@Preview
@Composable
private fun RecurrencePickerPreview() {
    MiraPreviewSurface {
        Column {
            RecurrenceBody(
                rule = RecurrenceRule.Weekly(days = setOf(DayOfWeek.MONDAY)),
                preview = listOf("16 March", "23 March", "30 March"),
                anchorDate = LocalDate.of(2026, 3, 16),
                words = EnglishTaskDetailWording,
                onRuleChange = {},
                onPickEndDate = {},
                onDone = {},
            )
        }
    }
}
