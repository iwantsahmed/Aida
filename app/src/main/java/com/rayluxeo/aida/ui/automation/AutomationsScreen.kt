package com.rayluxeo.aida.ui.automation

import com.rayluxeo.aida.core.design.component.MiraIcons

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.fadingHorizontalEdges
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraChip
import com.rayluxeo.aida.core.design.component.MiraEmptyState
import com.rayluxeo.aida.core.design.component.MiraIconButton
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationRun
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import com.rayluxeo.aida.domain.model.GlobalDeviceAction
import com.rayluxeo.aida.domain.model.InstalledApp
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.ui.tasks.TaskTimePickerDialog
import com.rayluxeo.aida.ui.tasks.wallClockLabel
import java.time.DayOfWeek
import java.time.LocalTime

@Composable
fun AutomationsScreen(
    onNavigateUp: () -> Unit,
    onOpenPlaces: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableNotificationAccess: () -> Unit,
    viewModel: AutomationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val automations by viewModel.automations.collectAsStateWithLifecycle()
    val runsHistory by viewModel.selectedHistoryRuns.collectAsStateWithLifecycle()
    val savedPlaces by viewModel.savedPlaces.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val crossApp by viewModel.crossApp.collectAsStateWithLifecycle()
    val words = automationsWording(state.language)

    // The accessibility and notification grants can change while Abu is in system settings, so the
    // readiness the sheet shows is refreshed every time this screen comes back to the foreground.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCrossAppReadiness()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MiraScaffold(
        topBar = {
            MiraTopBar(
                title = words.title,
                onBack = onNavigateUp,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
        ) {
            state.notice?.let { notice ->
                MiraInlineNotice(
                    text = notice,
                    tone = if (state.isSuccess) MiraNoticeTone.CONFIRM else MiraNoticeTone.INFO,
                    onDismiss = { viewModel.dismissNotice() },
                )
            }

            MiraButton(
                text = words.newRule,
                onClick = { viewModel.openAddSheet() },
                leadingIcon = MiraIcons.Add,
                modifier = Modifier.fillMaxWidth(),
            )

            if (automations.isEmpty()) {
                MiraEmptyState(
                    icon = MiraIcons.AutoAwesomeMotion,
                    message = words.noRulesMessage,
                    actionText = words.addRule,
                    onAction = { viewModel.openAddSheet() },
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.sm),
                ) {
                    items(automations, key = { it.id }) { automation ->
                        AutomationItemCard(
                            automation = automation,
                            words = words,
                            onToggleEnabled = { enabled -> viewModel.toggleEnabled(automation, enabled) },
                            onRunManually = { viewModel.runManually(automation.id) },
                            onViewHistory = { viewModel.selectForHistory(automation) },
                            onEdit = { viewModel.setEditingAutomation(automation) },
                            onDelete = { viewModel.deleteAutomation(automation.id) },
                        )
                    }
                }
            }
        }

        // History sheet
        state.selectedAutomationForHistory?.let { automation ->
            MiraBottomSheet(
                onDismissRequest = { viewModel.selectForHistory(null) },
                title = words.runHistoryTitle(automation.name),
            ) {
                if (runsHistory.isEmpty()) {
                    MiraEmptyState(
                        icon = MiraIcons.History,
                        message = words.noRunsMessage,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
                    ) {
                        items(runsHistory, key = { it.id }) { run ->
                            AutomationRunItemCard(run = run, words = words)
                        }
                    }
                }
            }
        }

        // Add/Edit sheet
        if (state.isAddSheetOpen) {
            AddEditAutomationBottomSheet(
                editingAutomation = state.editingAutomation,
                words = words,
                language = state.language,
                savedPlaces = savedPlaces,
                installedApps = installedApps,
                crossApp = crossApp,
                onOpenPlaces = onOpenPlaces,
                onEnableAccessibility = onEnableAccessibility,
                onEnableNotificationAccess = onEnableNotificationAccess,
                onDismiss = { viewModel.closeAddSheet() },
                onSave = { name, desc, trigger, action, isEnabled ->
                    viewModel.saveAutomation(name, desc, trigger, action, isEnabled)
                },
            )
        }
    }
}

@Composable
private fun AutomationItemCard(
    automation: Automation,
    words: AutomationsWording,
    onToggleEnabled: (Boolean) -> Unit,
    onRunManually: () -> Unit,
    onViewHistory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MiraTheme.spacing.md),
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = automation.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )

                Switch(
                    checked = automation.isEnabled,
                    onCheckedChange = onToggleEnabled,
                )
            }

            if (!automation.description.isNullOrBlank()) {
                Text(
                    text = automation.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
            ) {
                MiraChip(
                    text = words.triggerChip(automation.trigger.kind),
                    selected = true,
                )
                MiraChip(
                    text = words.actionChip(automation.action),
                    selected = false,
                )
            }

            Spacer(modifier = Modifier.height(MiraTheme.spacing.xxs))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiraIconButton(
                    icon = MiraIcons.PlayArrow,
                    contentDescription = words.runManually,
                    onClick = onRunManually,
                    tint = MaterialTheme.colorScheme.primary,
                )
                MiraIconButton(
                    icon = MiraIcons.History,
                    contentDescription = words.runHistory,
                    onClick = onViewHistory,
                )
                MiraIconButton(
                    icon = MiraIcons.Edit,
                    contentDescription = words.editAutomation,
                    onClick = onEdit,
                )
                MiraIconButton(
                    icon = MiraIcons.Delete,
                    contentDescription = words.deleteAutomation,
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun AutomationRunItemCard(run: AutomationRun, words: AutomationsWording) {
    MiraCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MiraTheme.spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = words.runOutcome(run.outcome),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (run.outcome == AutomationOutcome.SUCCESS) MaterialTheme.colorScheme.primary else MiraTheme.colors.danger,
                    fontWeight = FontWeight.Bold,
                )
                if (!run.detail.isNullOrBlank()) {
                    Text(
                        text = run.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Text(
                text = run.startedAt.toString().take(19).replace("T", " "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** What the action picker offers. Each maps to an [AutomationAction] on save. */
private enum class ActionChoice { RUN_COMMAND, NOTIFY, OPEN_LINK, PERFORM_GLOBAL_ACTION, LAUNCH_APP, DAILY_BRIEF }

/**
 * The triggers the sheet offers — and only these, because these are the ones that actually fire:
 * Manual (Run now), a time of day, a repeating interval, a saved place, app-open, the charger going
 * in or out, and the record-driven kinds (an invoice or task reaching a date). The trigger kinds
 * still unarmed in the model (a calendar event, a bare date) are left out, so the picker never
 * offers a control that does nothing (§12). They arrive in later increments with their scheduling.
 */
private val PickableTriggers = listOf(
    AutomationTrigger.MANUAL,
    AutomationTrigger.TIME_OF_DAY,
    AutomationTrigger.INTERVAL,
    AutomationTrigger.PLACE,
    AutomationTrigger.NOTIFICATION_POSTED,
    AutomationTrigger.APP_START,
    AutomationTrigger.POWER_CONNECTED,
    AutomationTrigger.POWER_DISCONNECTED,
    AutomationTrigger.INVOICE_DUE,
    AutomationTrigger.INVOICE_OVERDUE,
    AutomationTrigger.TASK_DUE,
    AutomationTrigger.TASK_OVERDUE,
    AutomationTrigger.SUBSCRIPTION_RENEWAL,
    AutomationTrigger.PROJECT_DEADLINE,
)

/** The record-driven triggers: no setup, they fire on the daily check when the record condition is met. */
private val RecordTriggers = setOf(
    AutomationTrigger.INVOICE_DUE,
    AutomationTrigger.INVOICE_OVERDUE,
    AutomationTrigger.TASK_DUE,
    AutomationTrigger.TASK_OVERDUE,
    AutomationTrigger.SUBSCRIPTION_RENEWAL,
    AutomationTrigger.PROJECT_DEADLINE,
)

/** The interval a brand-new repeating rule opens on when Abu has typed nothing yet. */
private const val DEFAULT_INTERVAL_MINUTES = 60

/** The time a brand-new time-of-day rule opens on: a sensible morning hour, not midnight. */
private val DEFAULT_TRIGGER_TIME: LocalTime = LocalTime.of(8, 0)

/** The trigger a rule opens the sheet on, folded to one the picker can show (else Manual). */
private fun AutomationTriggerConfig?.toPickableKind(): AutomationTrigger =
    this?.kind?.takeIf { it in PickableTriggers } ?: AutomationTrigger.MANUAL

/** The action choice a rule opens the sheet on. Anything beyond the shown set reads as a command. */
private fun AutomationAction?.toChoice(): ActionChoice = when (this) {
    is AutomationAction.Notify -> ActionChoice.NOTIFY
    is AutomationAction.OpenLink -> ActionChoice.OPEN_LINK
    is AutomationAction.PerformGlobalAction -> ActionChoice.PERFORM_GLOBAL_ACTION
    is AutomationAction.LaunchApp -> ActionChoice.LAUNCH_APP
    AutomationAction.GenerateDailyBrief -> ActionChoice.DAILY_BRIEF
    else -> ActionChoice.RUN_COMMAND
}

@Composable
private fun AddEditAutomationBottomSheet(
    editingAutomation: Automation?,
    words: AutomationsWording,
    language: SpokenLanguage,
    savedPlaces: List<SavedPlace>,
    installedApps: List<InstalledApp>,
    crossApp: CrossAppReadiness,
    onOpenPlaces: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableNotificationAccess: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String?, AutomationTriggerConfig, AutomationAction, Boolean) -> Unit,
) {
    val spacing = MiraTheme.spacing
    val seedAction = editingAutomation?.action
    val seedTrigger = editingAutomation?.trigger
    var name by remember { mutableStateOf(editingAutomation?.name ?: "") }
    var description by remember { mutableStateOf(editingAutomation?.description ?: "") }
    var triggerKind by remember { mutableStateOf(editingAutomation?.trigger.toPickableKind()) }
    var actionChoice by remember { mutableStateOf(seedAction.toChoice()) }
    var utterance by remember { mutableStateOf((seedAction as? AutomationAction.RunUtterance)?.utterance ?: "") }
    var notifyTitle by remember { mutableStateOf((seedAction as? AutomationAction.Notify)?.title ?: "") }
    var notifyBody by remember { mutableStateOf((seedAction as? AutomationAction.Notify)?.body ?: "") }
    var linkUrl by remember { mutableStateOf((seedAction as? AutomationAction.OpenLink)?.url ?: "") }
    // The cross-app action state: a chosen global gesture (defaulting to Back) and a chosen app.
    var globalAction by remember {
        mutableStateOf((seedAction as? AutomationAction.PerformGlobalAction)?.action ?: GlobalDeviceAction.BACK)
    }
    var selectedApp by remember { mutableStateOf(seedAction as? AutomationAction.LaunchApp) }
    // The time-based inputs seed from the rule being edited, so reopening a 7am-weekday rule shows
    // 7am and those weekdays rather than the blank defaults.
    var triggerTime by remember {
        mutableStateOf(
            (seedTrigger as? AutomationTriggerConfig.TimeOfDay)?.let {
                LocalTime.of(it.minuteOfDay / 60, it.minuteOfDay % 60)
            } ?: DEFAULT_TRIGGER_TIME,
        )
    }
    var selectedDays by remember {
        mutableStateOf((seedTrigger as? AutomationTriggerConfig.TimeOfDay)?.daysOfWeek ?: emptySet())
    }
    var intervalText by remember {
        mutableStateOf(
            (seedTrigger as? AutomationTriggerConfig.Interval)?.everyMinutes?.toString()
                ?: DEFAULT_INTERVAL_MINUTES.toString(),
        )
    }
    // The place is held as the config snapshot itself, not a SavedPlace id: a rule carries its own
    // coordinates so an edited rule keeps its place even if that saved place was since removed, and
    // tapping a chip just rebuilds the snapshot from the chosen place.
    var selectedPlace by remember {
        mutableStateOf(seedTrigger as? AutomationTriggerConfig.Place)
    }
    // The notification trigger's two optional filters: one app (null = any) and a title phrase.
    var notificationPackage by remember {
        mutableStateOf((seedTrigger as? AutomationTriggerConfig.NotificationPosted)?.packageName)
    }
    var notificationTitleFilter by remember {
        mutableStateOf((seedTrigger as? AutomationTriggerConfig.NotificationPosted)?.titleContains ?: "")
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var isEnabled by remember { mutableStateOf(editingAutomation?.isEnabled ?: true) }

    if (showTimePicker) {
        TaskTimePickerDialog(
            title = words.timePickerTitle,
            initialTime = triggerTime,
            setText = words.setLabel,
            cancelText = words.cancelLabel,
            onPick = { triggerTime = it },
            onDismiss = { showTimePicker = false },
        )
    }

    MiraBottomSheet(
        onDismissRequest = onDismiss,
        title = if (editingAutomation == null) words.createRuleTitle else words.editRuleTitle,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            MiraTextField(
                value = name,
                onValueChange = { name = it },
                label = words.nameLabel,
                placeholder = words.namePlaceholder,
            )

            MiraTextField(
                value = description,
                onValueChange = { description = it },
                label = words.descriptionLabel,
            )

            PickerSectionLabel(words.triggerSectionLabel)
            TriggerPickerRow(selected = triggerKind, words = words, onSelect = { triggerKind = it })
            when (triggerKind) {
                AutomationTrigger.MANUAL -> Text(
                    text = words.manualTriggerNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AutomationTrigger.TIME_OF_DAY -> {
                    MiraButton(
                        style = MiraButtonStyle.SECONDARY,
                        text = "${words.timeLabel}: ${wallClockLabel(triggerTime, language)}",
                        onClick = { showTimePicker = true },
                        leadingIcon = MiraIcons.Schedule,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PickerSectionLabel(words.daysLabel)
                    DayOfWeekPickerRow(
                        selected = selectedDays,
                        words = words,
                        onToggle = { day ->
                            selectedDays =
                                if (day in selectedDays) selectedDays - day else selectedDays + day
                        },
                    )
                    if (selectedDays.isEmpty()) {
                        Text(
                            text = words.everyDayNote,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                AutomationTrigger.INTERVAL -> MiraTextField(
                    value = intervalText,
                    onValueChange = { entered -> intervalText = entered.filter(Char::isDigit) },
                    label = words.intervalLabel,
                    placeholder = words.intervalPlaceholder,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                AutomationTrigger.PLACE -> {
                    PickerSectionLabel(words.choosePlaceLabel)
                    // Only a true empty: no saved places AND nothing already chosen. Editing a rule
                    // whose saved place was since deleted keeps its own coordinate snapshot, so it
                    // still shows the place (its label + radius) rather than this "add one" state.
                    if (savedPlaces.isEmpty() && selectedPlace == null) {
                        // No places to pick: a calm line and a real way forward (the Places screen),
                        // never a dead picker.
                        Text(
                            text = words.noSavedPlacesYet,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MiraButton(
                            style = MiraButtonStyle.SECONDARY,
                            text = words.openPlacesAction,
                            onClick = onOpenPlaces,
                            leadingIcon = MiraIcons.Place,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        if (savedPlaces.isNotEmpty()) {
                            PlacePickerRow(
                                places = savedPlaces,
                                selectedLabel = selectedPlace?.label,
                                onSelect = { place ->
                                    selectedPlace = AutomationTriggerConfig.Place(
                                        label = place.label,
                                        latitude = place.latitude,
                                        longitude = place.longitude,
                                        radiusMetres = place.radiusMeters.toInt(),
                                    )
                                },
                            )
                        }
                        selectedPlace?.let { place ->
                            // Names the place as well as the radius, so an edited rule whose saved
                            // place is gone still shows which place it watches.
                            Text(
                                text = "${place.label} — ${words.placeRadiusNote(place.radiusMetres)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                AutomationTrigger.NOTIFICATION_POSTED -> {
                    // Distinguish the two ways it is not ready: the master toggle (fixed in Settings,
                    // no system-screen button) vs the OS grant (fixed on its system screen).
                    when {
                        !crossApp.toggleOn -> CrossAppOffNote(text = words.crossAppTierOffNote)
                        !crossApp.notificationAccessOn -> CrossAppOffNote(
                            text = words.notificationAccessOffNote,
                            actionText = words.enableNotificationAccessAction,
                            onEnable = onEnableNotificationAccess,
                        )
                    }
                    PickerSectionLabel(words.notificationFromAppLabel)
                    NotificationAppPickerRow(
                        apps = installedApps,
                        anyAppLabel = words.notificationAnyApp,
                        selectedPackage = notificationPackage,
                        onSelect = { notificationPackage = it },
                    )
                    MiraTextField(
                        value = notificationTitleFilter,
                        onValueChange = { notificationTitleFilter = it },
                        label = words.notificationTitleFilterLabel,
                        placeholder = words.notificationTitleFilterPlaceholder,
                    )
                }
                in RecordTriggers -> Text(
                    text = words.recordTriggerNote(triggerKind),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Unit
            }

            PickerSectionLabel(words.actionSectionLabel)
            ActionPickerRow(selected = actionChoice, words = words, onSelect = { actionChoice = it })
            when (actionChoice) {
                ActionChoice.RUN_COMMAND -> MiraTextField(
                    value = utterance,
                    onValueChange = { utterance = it },
                    label = words.actionFieldLabel,
                    placeholder = words.actionPlaceholder,
                )
                ActionChoice.NOTIFY -> {
                    MiraTextField(
                        value = notifyTitle,
                        onValueChange = { notifyTitle = it },
                        label = words.notifyTitleLabel,
                    )
                    MiraTextField(
                        value = notifyBody,
                        onValueChange = { notifyBody = it },
                        label = words.notifyBodyLabel,
                        singleLine = false,
                    )
                }
                ActionChoice.OPEN_LINK -> MiraTextField(
                    value = linkUrl,
                    onValueChange = { linkUrl = it },
                    label = words.linkLabel,
                    placeholder = words.linkPlaceholder,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                ActionChoice.PERFORM_GLOBAL_ACTION -> {
                    when {
                        !crossApp.toggleOn -> CrossAppOffNote(text = words.crossAppTierOffNote)
                        !crossApp.accessibilityOn -> CrossAppOffNote(
                            text = words.accessibilityOffNote,
                            actionText = words.enableAccessibilityAction,
                            onEnable = onEnableAccessibility,
                        )
                    }
                    PickerSectionLabel(words.chooseGestureLabel)
                    GlobalActionPickerRow(
                        selected = globalAction,
                        words = words,
                        onSelect = { globalAction = it },
                    )
                }
                ActionChoice.LAUNCH_APP -> {
                    PickerSectionLabel(words.chooseAppLabel)
                    if (installedApps.isEmpty()) {
                        Text(
                            text = words.noAppsFound,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LaunchAppPickerRow(
                            apps = installedApps,
                            selectedPackage = selectedApp?.packageName,
                            onSelect = { app ->
                                selectedApp = AutomationAction.LaunchApp(packageName = app.packageName, label = app.label)
                            },
                        )
                    }
                }
                ActionChoice.DAILY_BRIEF -> Unit
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = words.enableImmediately,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Switch(
                    checked = isEnabled,
                    onCheckedChange = { isEnabled = it },
                )
            }

            Spacer(modifier = Modifier.height(spacing.xs))

            MiraButton(
                text = if (editingAutomation == null) words.createRuleButton else words.saveChangesButton,
                onClick = {
                    val trigger = when (triggerKind) {
                        AutomationTrigger.TIME_OF_DAY -> AutomationTriggerConfig.TimeOfDay(
                            minuteOfDay = triggerTime.hour * 60 + triggerTime.minute,
                            daysOfWeek = selectedDays,
                        )
                        AutomationTrigger.INTERVAL -> AutomationTriggerConfig.Interval(
                            // A blank or zero field falls back to the default rather than saving an
                            // every-0-minutes rule the scheduler could not honour; the validator has
                            // the final say on the lower bound.
                            everyMinutes = intervalText.toIntOrNull()?.takeIf { it > 0 }
                                ?: DEFAULT_INTERVAL_MINUTES,
                        )
                        // A place rule needs a chosen place; the save guard below blocks the button
                        // until one is picked, so this fallback is never the rule that gets saved.
                        AutomationTrigger.PLACE -> selectedPlace ?: AutomationTriggerConfig.Manual
                        AutomationTrigger.NOTIFICATION_POSTED -> AutomationTriggerConfig.NotificationPosted(
                            // Both filters optional: a null package means any app, a blank title means
                            // any title. A rule with neither fires on every notification, which is a
                            // choice Abu can make.
                            packageName = notificationPackage,
                            titleContains = notificationTitleFilter.trim().takeIf { it.isNotBlank() },
                        )
                        AutomationTrigger.APP_START -> AutomationTriggerConfig.AppStart
                        AutomationTrigger.POWER_CONNECTED -> AutomationTriggerConfig.PowerConnected
                        AutomationTrigger.POWER_DISCONNECTED -> AutomationTriggerConfig.PowerDisconnected
                        AutomationTrigger.INVOICE_DUE -> AutomationTriggerConfig.InvoiceDue
                        AutomationTrigger.INVOICE_OVERDUE -> AutomationTriggerConfig.InvoiceOverdue
                        AutomationTrigger.TASK_DUE -> AutomationTriggerConfig.TaskDue
                        AutomationTrigger.TASK_OVERDUE -> AutomationTriggerConfig.TaskOverdue
                        AutomationTrigger.SUBSCRIPTION_RENEWAL -> AutomationTriggerConfig.SubscriptionRenewal
                        AutomationTrigger.PROJECT_DEADLINE -> AutomationTriggerConfig.ProjectDeadline
                        else -> AutomationTriggerConfig.Manual
                    }
                    val action = when (actionChoice) {
                        ActionChoice.RUN_COMMAND -> AutomationAction.RunUtterance(utterance.trim())
                        ActionChoice.NOTIFY -> AutomationAction.Notify(
                            title = notifyTitle.trim(),
                            body = notifyBody.trim().takeIf { it.isNotBlank() },
                        )
                        ActionChoice.OPEN_LINK -> AutomationAction.OpenLink(linkUrl.trim())
                        // A global action always has a chosen gesture; a launch needs a picked app,
                        // which the save guard below enforces, so this fallback never gets saved.
                        ActionChoice.PERFORM_GLOBAL_ACTION -> AutomationAction.PerformGlobalAction(globalAction)
                        ActionChoice.LAUNCH_APP -> selectedApp ?: AutomationAction.GenerateDailyBrief
                        ActionChoice.DAILY_BRIEF -> AutomationAction.GenerateDailyBrief
                    }
                    // The validator has the final say on blank utterances, missing titles and
                    // scheme-less links — the save handler only guards the name and, for a place or
                    // launch rule, that the thing it acts on was actually chosen, then lets the view
                    // model surface a worded refusal.
                    val placeReady = triggerKind != AutomationTrigger.PLACE || selectedPlace != null
                    val launchReady = actionChoice != ActionChoice.LAUNCH_APP || selectedApp != null
                    if (name.isNotBlank() && placeReady && launchReady) {
                        onSave(name, description.takeIf { it.isNotBlank() }, trigger, action, isEnabled)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PickerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TriggerPickerRow(
    selected: AutomationTrigger,
    words: AutomationsWording,
    onSelect: (AutomationTrigger) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        PickableTriggers.forEach { trigger ->
            MiraChip(
                text = words.triggerLabel(trigger),
                selected = trigger == selected,
                onClick = { onSelect(trigger) },
            )
        }
    }
}

/**
 * The saved places a place rule may watch, one chip each. Selection is by label rather than by a
 * SavedPlace id, because the rule stores a coordinate snapshot (not a reference) — see the
 * `selectedPlace` note in the sheet.
 */
@Composable
private fun PlacePickerRow(
    places: List<SavedPlace>,
    selectedLabel: String?,
    onSelect: (SavedPlace) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        places.forEach { place ->
            MiraChip(
                text = place.label,
                selected = place.label == selectedLabel,
                onClick = { onSelect(place) },
            )
        }
    }
}

@Composable
private fun ActionPickerRow(
    selected: ActionChoice,
    words: AutomationsWording,
    onSelect: (ActionChoice) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        ActionChoice.entries.forEach { choice ->
            MiraChip(
                text = when (choice) {
                    ActionChoice.RUN_COMMAND -> words.actionRunCommand
                    ActionChoice.NOTIFY -> words.actionNotify
                    ActionChoice.OPEN_LINK -> words.actionOpenLink
                    ActionChoice.PERFORM_GLOBAL_ACTION -> words.actionPerformGlobalAction
                    ActionChoice.LAUNCH_APP -> words.actionLaunchApp
                    ActionChoice.DAILY_BRIEF -> words.actionDailyBrief
                },
                selected = choice == selected,
                onClick = { onSelect(choice) },
            )
        }
    }
}

/**
 * A calm "this will not fire yet" note. When the fix is an OS grant it carries a one-tap button to
 * the right system screen; when the fix is the master toggle (fixed in Mira's own Settings) it is
 * text only, never a button that would open the wrong place (§5/§12).
 */
@Composable
private fun CrossAppOffNote(
    text: String,
    actionText: String? = null,
    onEnable: (() -> Unit)? = null,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MiraTheme.colors.danger,
    )
    if (actionText != null && onEnable != null) {
        MiraButton(
            style = MiraButtonStyle.SECONDARY,
            text = actionText,
            onClick = onEnable,
            leadingIcon = MiraIcons.Settings,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The system gestures a global-action rule may perform, one chip each. */
@Composable
private fun GlobalActionPickerRow(
    selected: GlobalDeviceAction,
    words: AutomationsWording,
    onSelect: (GlobalDeviceAction) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        GlobalDeviceAction.entries.forEach { action ->
            MiraChip(
                text = words.globalActionLabel(action),
                selected = action == selected,
                onClick = { onSelect(action) },
            )
        }
    }
}

/** The launchable apps a launch rule may open, one chip each, selected by package. */
@Composable
private fun LaunchAppPickerRow(
    apps: List<InstalledApp>,
    selectedPackage: String?,
    onSelect: (InstalledApp) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        apps.forEach { app ->
            MiraChip(
                text = app.label,
                selected = app.packageName == selectedPackage,
                onClick = { onSelect(app) },
            )
        }
    }
}

/**
 * The app a notification rule is narrowed to. The first chip is "any app" (null package), so the
 * default — fire for every app — is a visible choice rather than a blank.
 */
@Composable
private fun NotificationAppPickerRow(
    apps: List<InstalledApp>,
    anyAppLabel: String,
    selectedPackage: String?,
    onSelect: (String?) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        MiraChip(
            text = anyAppLabel,
            selected = selectedPackage == null,
            onClick = { onSelect(null) },
        )
        apps.forEach { app ->
            MiraChip(
                text = app.label,
                selected = app.packageName == selectedPackage,
                onClick = { onSelect(app.packageName) },
            )
        }
    }
}

/**
 * The weekdays a time-of-day rule may be pinned to. An empty selection is not "never" — it means
 * every day, which the note under the row says in words, so the row never reads as a rule that
 * cannot fire.
 */
@Composable
private fun DayOfWeekPickerRow(
    selected: Set<DayOfWeek>,
    words: AutomationsWording,
    onToggle: (DayOfWeek) -> Unit,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .fadingHorizontalEdges(scroll)
            .horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(MiraTheme.spacing.xs),
    ) {
        DayOfWeek.entries.forEach { day ->
            MiraChip(
                text = words.dayLabel(day),
                selected = day in selected,
                onClick = { onToggle(day) },
            )
        }
    }
}
