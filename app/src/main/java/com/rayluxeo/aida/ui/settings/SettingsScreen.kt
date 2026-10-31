package com.rayluxeo.aida.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraBottomSheet
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle
import com.rayluxeo.aida.core.design.component.MiraCard
import com.rayluxeo.aida.core.design.component.MiraDialog
import com.rayluxeo.aida.core.design.component.MiraDivider
import com.rayluxeo.aida.core.design.component.MiraIcons
import com.rayluxeo.aida.core.design.component.MiraInlineNotice
import com.rayluxeo.aida.core.design.component.MiraListRow
import com.rayluxeo.aida.core.design.component.MiraListSkeleton
import com.rayluxeo.aida.core.design.component.MiraNoticeTone
import com.rayluxeo.aida.core.design.component.MiraScaffold
import com.rayluxeo.aida.core.design.component.MiraSectionHeader
import com.rayluxeo.aida.core.design.component.MiraSegmentedControl
import com.rayluxeo.aida.core.design.component.MiraSwitch
import com.rayluxeo.aida.core.design.component.MiraTextField
import com.rayluxeo.aida.core.design.component.MiraTopBar
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraCapability
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.model.DateStyle
import com.rayluxeo.aida.domain.ota.AppUpdate
import com.rayluxeo.aida.domain.repository.CloudQuotaStatus
import com.rayluxeo.aida.ui.common.languageLabelsInOrder
import com.rayluxeo.aida.ui.common.languageModesInOrder
import com.rayluxeo.aida.ui.common.formatDate
import com.rayluxeo.aida.ui.mira.hasGranted
import com.rayluxeo.aida.ui.mira.settingsIntents
import com.rayluxeo.aida.ui.mira.tryStartActivity
import com.rayluxeo.aida.ui.tasks.displayLanguage
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.util.Currency

/**
 * Everything Abu can change about how Mira behaves, in eight calm groups. Each control writes
 * through immediately — there is no Save button and nothing on this screen that only looks like it
 * did something (§12).
 *
 * The layout rule is the one the screen has always kept: a section is a [MiraSectionHeader] a
 * `sectionGap` below the last thing, every label and glyph begins at the screen gutter, and a row
 * with nothing to tap is set as text rather than a dead control.
 */
@Composable
fun SettingsScreen(
    onOpenVoiceSetup: () -> Unit,
    onOpenAssistantSetup: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenActivityLog: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenSalahSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val spacing = MiraTheme.spacing
    val language = state.settings.languageMode.displayLanguage()
    val words = settingsWording(language)
    val places = placesWording(language)
    val settings = state.settings

    var editingName by remember { mutableStateOf(false) }
    var nameDraft by remember(settings.userName) { mutableStateOf(settings.userName) }

    // Re-read OS grants and the exact-alarm permission whenever Abu comes back from a system screen.
    var permissionRefresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshExactAlarmPermission()
        permissionRefresh++
    }

    MiraScaffold(
        modifier = modifier,
        topBar = { MiraTopBar(title = words.title, onBack = onBack, backContentDescription = words.back) },
    ) { padding ->
        if (state.loading) {
            MiraListSkeleton(modifier = Modifier.padding(padding))
            return@MiraScaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(spacing.md))
            state.error?.let { error ->
                MiraInlineNotice(
                    text = error,
                    tone = MiraNoticeTone.DANGER,
                    dismissContentDescription = words.dismiss,
                    onDismiss = viewModel::dismissError,
                )
                Spacer(Modifier.height(spacing.lg))
            }

            // ===================== PROFILE =====================
            MiraSectionHeader(text = words.sectionProfile)
            NavigationRow(
                title = words.userNameTitle,
                supporting = settings.userName.ifBlank { words.userNameNotSet },
                icon = MiraIcons.Person,
                onClick = {
                    nameDraft = settings.userName
                    editingName = true
                },
            )

            Spacer(Modifier.height(spacing.sectionGap))

            // ===================== MIRA =====================
            MiraSectionHeader(text = words.sectionMira)
            NavigationRow(words.assistantRole, words.assistantRoleDetail, MiraIcons.Assistant, onOpenAssistantSetup)
            MiraDivider()
            NavigationRow(words.salah, words.salahDetail, MiraIcons.Salah, onOpenSalahSettings)
            MiraDivider()
            NavigationRow(words.howMiraSounds, words.voiceSummary(state.voice), MiraIcons.GraphicEq, onOpenVoiceSetup)
            MiraDivider()
            ToggleRow(words.spokenResponses, words.spokenResponsesDetail, MiraIcons.RecordVoiceOver, state.voice.spokenResponsesEnabled, viewModel::setSpokenResponses)
            MiraDivider()
            ToggleRow(words.activationSound, words.activationSoundDetail, MiraIcons.NotificationsActive, settings.activationSound, viewModel::setActivationSound)

            Spacer(Modifier.height(spacing.lg))
            LabeledSegment(
                title = words.language,
                supporting = words.languageFootnote,
                options = languageLabelsInOrder,
                selectedIndex = languageModesInOrder.indexOf(settings.languageMode),
                onSelect = { viewModel.setLanguageMode(languageModesInOrder[it]) },
            )
            Spacer(Modifier.height(spacing.lg))
            LabeledSegment(
                title = words.conversationStyle,
                supporting = words.conversationStyleDetail,
                options = ConversationStyle.entries.map { words.conversationStyleName(it) },
                selectedIndex = ConversationStyle.entries.indexOf(settings.conversationStyle),
                onSelect = { viewModel.setConversationStyle(ConversationStyle.entries[it]) },
            )

            // ===================== GENERAL =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionGeneral)
            StatementRow(words.theme, words.themeValue, MiraIcons.AutoAwesome)
            MiraDivider()
            ToggleRow(words.hapticFeedback, words.hapticFeedbackDetail, MiraIcons.Vibration, settings.hapticFeedback, viewModel::setHapticFeedback)
            MiraDivider()
            SelectorRow(words.dateFormat, words.dateFormatDetail, formatDate(SAMPLE_DATE, settings.dateStyle, language), MiraIcons.CalendarToday) { viewModel.openSheet(SettingsSheet.DATE_STYLE) }
            MiraDivider()
            SelectorRow(words.currency, words.currencyDetail, currencyLabel(state.currency), MiraIcons.Paid) { viewModel.openSheet(SettingsSheet.CURRENCY) }
            MiraDivider()
            SelectorRow(words.firstDayOfWeek, words.firstDayOfWeekDetail, words.dayName(settings.firstDayOfWeek), MiraIcons.CalendarMonth) { viewModel.openSheet(SettingsSheet.FIRST_DAY) }

            // ===================== NOTIFICATIONS =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionNotifications)
            ToggleRow(words.notifyTasks, words.notifyTasksDetail, MiraIcons.TaskAlt, settings.notifyTasks, viewModel::setNotifyTasks)
            MiraDivider()
            ToggleRow(words.notifyReminders, words.notifyRemindersDetail, MiraIcons.NotificationsActive, settings.notifyReminders, viewModel::setNotifyReminders)
            MiraDivider()
            ToggleRow(words.notifyInvoices, words.notifyInvoicesDetail, MiraIcons.ReceiptLong, settings.notifyInvoices, viewModel::setNotifyInvoices)
            MiraDivider()
            ToggleRow(words.notifySubscriptions, words.notifySubscriptionsDetail, MiraIcons.Autorenew, settings.notifySubscriptions, viewModel::setNotifySubscriptions)
            MiraDivider()
            ToggleRow(words.quietHours, words.quietHoursDetail, MiraIcons.Bedtime, settings.quietHoursEnabled, viewModel::setQuietHoursEnabled)
            if (settings.quietHoursEnabled) {
                SelectorRow(words.quietHoursFrom, null, state.quietStartLabel, MiraIcons.Schedule) { viewModel.editQuietHours(QuietBound.START) }
                SelectorRow(words.quietHoursTo, null, state.quietEndLabel, MiraIcons.Schedule) { viewModel.editQuietHours(QuietBound.END) }
            }
            MiraDivider()
            NavigationRow(places.settingsRowTitle, places.settingsRowSupporting, MiraIcons.Place, onOpenPlaces)

            Spacer(Modifier.height(spacing.lg))
            BriefSettingsSection(
                state = state,
                words = briefSettingsWording(language),
                onEnabledChange = viewModel::setBriefEnabled,
                onEditTime = viewModel::editBriefTime,
                onTimeChosen = viewModel::setBriefTime,
                onDismissTimeEditor = viewModel::dismissBriefTimeEditor,
            )

            if (!state.canScheduleExact) {
                Spacer(Modifier.height(spacing.lg))
                SettingsStatementCard(
                    icon = MiraIcons.Alarm,
                    iconTint = MiraTheme.colors.danger,
                    title = words.remindersMayBeLate,
                    body = words.remindersMayBeLateDetail,
                ) {
                    MiraButton(
                        style = MiraButtonStyle.SECONDARY,
                        text = words.allowExactAlarms,
                        onClick = { context.openExactAlarmSettings() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ===================== AI =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionAi)
            GeminiSection(
                hasKey = state.hasGeminiKey,
                words = words,
                onAddKey = viewModel::openGeminiEditor,
                onRemoveKey = viewModel::removeGeminiKey,
            )
            Spacer(Modifier.height(spacing.md))
            StatusRow(words.cloudAvailability, availabilityText(state, words), MiraIcons.Cloud)
            if (state.hasGeminiKey) {
                MiraDivider()
                StatusRow(words.freeTierState, words.quotaLabel(state.cloudQuota), MiraIcons.AutoAwesome)
                MiraDivider()
                ToggleRow(
                    title = words.disableCloudAi,
                    supporting = words.disableCloudAiDetail,
                    icon = MiraIcons.Block,
                    checked = !settings.cloudAiEnabled,
                    onCheckedChange = { disabled -> viewModel.setCloudAiEnabled(!disabled) },
                )
            }

            // ===================== PRIVACY & SECURITY =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionPrivacy)
            ToggleRow(words.biometricLock, words.biometricLockDetail, MiraIcons.Fingerprint, settings.appLockEnabled, viewModel::setBiometricLock)
            if (settings.appLockEnabled) {
                SelectorRow(words.autoLock, words.autoLockDetail, words.timeoutLabel(settings.appLockTimeoutSeconds), MiraIcons.Schedule) { viewModel.openSheet(SettingsSheet.AUTO_LOCK) }
                ToggleRow(words.lockWhenBackgrounded, words.lockWhenBackgroundedDetail, MiraIcons.Lock, settings.lockWhenBackgrounded, viewModel::setLockWhenBackgrounded)
            }
            MiraDivider()
            NavigationRow(words.permissions, words.permissionsDetail, MiraIcons.Shield, onOpenPermissions)
            MiraDivider()
            NavigationRow(words.activityLog, words.activityLogDetail, MiraIcons.History, onOpenActivityLog)

            Spacer(Modifier.height(spacing.lg))
            MiraSectionHeader(text = words.protectHeader)
            SettingsFootnote(words.protectDetail, Modifier.padding(bottom = spacing.xs))
            ToggleRow(words.protectMoney, "", MiraIcons.Paid, settings.protectMoney, viewModel::setProtectMoney)
            MiraDivider()
            ToggleRow(words.protectMemory, "", MiraIcons.Psychology, settings.protectMemory, viewModel::setProtectMemory)
            MiraDivider()
            ToggleRow(words.protectInvoices, "", MiraIcons.ReceiptLong, settings.protectInvoices, viewModel::setProtectInvoices)
            MiraDivider()
            ToggleRow(words.protectBackups, "", MiraIcons.Cloud, settings.protectBackups, viewModel::setProtectBackups)

            // ===================== CROSS-APP AUTOMATIONS =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionCrossApp)
            ToggleRow(
                words.crossAppAutomations,
                words.crossAppAutomationsDetail,
                MiraIcons.AutoAwesomeMotion,
                settings.crossAppAutomationsEnabled,
                viewModel::setCrossAppAutomations,
            )

            // ===================== DATA =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionData)
            NavigationRow(words.dataBackup, words.dataBackupDetail, MiraIcons.Download, onOpenBackup)
            MiraDivider()
            MiraListRow(
                title = words.clearConversation,
                supporting = words.clearConversationDetail,
                onClick = viewModel::askClearHistory,
                leading = { Icon(MiraIcons.DeleteSweep, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
            MiraDivider()
            MiraListRow(
                title = if (state.erasingData) words.erasing else words.eraseData,
                supporting = words.eraseDataDetail,
                enabled = !state.erasingData,
                onClick = viewModel::askEraseData,
                leading = { Icon(MiraIcons.Delete, null, tint = MiraTheme.colors.danger) },
            )

            // ===================== INTEGRATIONS =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.sectionIntegrations)
            IntegrationRow(words.integrationCalendar, words.integrationCalendarDetail, MiraIcons.CalendarMonth, MiraCapability.CALENDAR, words, context, permissionRefresh)
            MiraDivider()
            IntegrationRow(words.integrationContacts, words.integrationContactsDetail, MiraIcons.Person, MiraCapability.CONTACTS, words, context, permissionRefresh)
            MiraDivider()
            NavigationRow(words.defaultAssistant, words.defaultAssistantDetail, MiraIcons.Assistant, onOpenAssistantSetup)

            // ===================== ABOUT =====================
            Spacer(Modifier.height(spacing.sectionGap))
            MiraSectionHeader(text = words.about)
            NavigationRow(words.privacyInformation, words.privacyInformationDetail, MiraIcons.Shield, onOpenPrivacy)
            MiraDivider()
            NavigationRow(words.licenses, words.licensesDetail, MiraIcons.Article, onOpenLicenses)
            MiraDivider()
            OtaUpdateSection(
                state = state,
                words = words,
                onCheckUpdate = { viewModel.checkForUpdates() },
                onDownloadUpdate = { update -> viewModel.downloadAndInstallUpdate(update, context) },
                onInstallApk = { file -> viewModel.installDownloadedApk(file, context) },
                onDismiss = { viewModel.dismissOta() },
            )
            Spacer(Modifier.height(spacing.lg))
            Text(
                text = words.versionLine(state.versionName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(spacing.xxs))
            SettingsFootnote(words.buildLine(state.versionCode))
            Spacer(Modifier.height(spacing.xxl))
        }
    }

    // ----- dialogs, sheets and the key editor -----
    SettingsOverlays(state = state, words = words, viewModel = viewModel, language = language)

    if (editingName) {
        MiraDialog(
            title = words.editNameTitle,
            onDismissRequest = { editingName = false },
            confirmText = words.save,
            onConfirm = {
                viewModel.setUserName(nameDraft)
                editingName = false
            },
            dismissText = words.cancel,
            onDismiss = { editingName = false },
        ) {
            Spacer(Modifier.height(spacing.sm))
            MiraTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                placeholder = words.namePlaceholder,
                leadingIcon = MiraIcons.Person,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The modal surfaces, kept out of the long column so the structure above reads as the screen. */
@Composable
private fun SettingsOverlays(
    state: SettingsUiState,
    words: SettingsWording,
    viewModel: SettingsViewModel,
    language: SpokenLanguage,
) {
    val spacing = MiraTheme.spacing

    if (state.confirmingClearHistory) {
        MiraDialog(
            title = words.clearConversationTitle,
            onDismissRequest = viewModel::cancelClearHistory,
            confirmText = words.clearIt,
            onConfirm = viewModel::confirmClearHistory,
            message = words.clearConversationMessage,
            dismissText = words.keepIt,
            destructive = true,
        )
    }

    if (state.confirmingEraseData) {
        MiraDialog(
            title = words.eraseDataTitle,
            onDismissRequest = viewModel::cancelEraseData,
            confirmText = words.eraseDataConfirm,
            onConfirm = viewModel::confirmEraseData,
            message = words.eraseDataMessage,
            dismissText = words.eraseDataKeep,
            destructive = true,
        )
    }

    state.quietEditor?.let { bound ->
        val initialMinute = when (bound) {
            QuietBound.START -> state.settings.quietHoursStartMinute
            QuietBound.END -> state.settings.quietHoursEndMinute
        }
        QuietTimeDialog(
            title = if (bound == QuietBound.START) words.quietHoursFrom else words.quietHoursTo,
            initial = LocalTime.of(initialMinute / 60, initialMinute % 60),
            onPick = { viewModel.setQuietHoursBound(bound, it) },
            onDismiss = viewModel::dismissQuietEditor,
            words = words,
        )
    }

    when (state.activeSheet) {
        SettingsSheet.DATE_STYLE -> SettingsSelectionSheet(
            title = words.chooseDateFormat,
            options = DateStyle.entries.map {
                SelectionOption(formatDate(SAMPLE_DATE, it, language), null, it == state.settings.dateStyle)
            },
            onSelect = { viewModel.setDateStyle(DateStyle.entries[it]) },
            onDismiss = viewModel::dismissSheet,
        )
        SettingsSheet.CURRENCY -> {
            val currencies = currencyOptions()
            SettingsSelectionSheet(
                title = words.chooseCurrency,
                options = currencies.map {
                    SelectionOption(it.currencyCode, it.displayName, it.currencyCode == state.currency.currencyCode)
                },
                onSelect = { viewModel.setCurrency(currencies[it]) },
                onDismiss = viewModel::dismissSheet,
            )
        }
        SettingsSheet.FIRST_DAY -> SettingsSelectionSheet(
            title = words.chooseFirstDay,
            options = FIRST_DAY_OPTIONS.map {
                SelectionOption(words.dayName(it), null, it == state.settings.firstDayOfWeek)
            },
            onSelect = { viewModel.setFirstDayOfWeek(FIRST_DAY_OPTIONS[it]) },
            onDismiss = viewModel::dismissSheet,
        )
        SettingsSheet.AUTO_LOCK -> SettingsSelectionSheet(
            title = words.chooseAutoLock,
            options = AUTO_LOCK_OPTIONS.map {
                SelectionOption(words.timeoutLabel(it), null, it == state.settings.appLockTimeoutSeconds)
            },
            onSelect = { viewModel.setAutoLockTimeout(AUTO_LOCK_OPTIONS[it]) },
            onDismiss = viewModel::dismissSheet,
        )
        null -> Unit
    }

    if (state.geminiEditorOpen) {
        MiraBottomSheet(onDismissRequest = viewModel::dismissGeminiEditor, title = words.geminiKeyTitle) {
            Text(
                text = words.geminiKeyDetail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md))
            MiraTextField(
                value = state.geminiKeyDraft,
                onValueChange = viewModel::onGeminiKeyDraftChange,
                modifier = Modifier.fillMaxWidth(),
                label = words.keyLabel,
                enabled = !state.savingGeminiKey,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(spacing.lg))
            state.error?.let { error ->
                MiraInlineNotice(text = error, tone = MiraNoticeTone.DANGER, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(spacing.sm))
            }
            MiraButton(
                text = if (state.savingGeminiKey) words.savingKey else words.saveKey,
                onClick = viewModel::saveGeminiKey,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSaveGeminiKey,
            )
        }
    }
}

// ----------------------------------------------------------------------------- helpers

private val SAMPLE_DATE: LocalDate = LocalDate.of(2026, 1, 31)
private val AUTO_LOCK_OPTIONS = listOf(0, 30, 60, 300, 900)
private val FIRST_DAY_OPTIONS = listOf(
    DayOfWeek.SUNDAY,
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
)
private val CURRENCY_CODES = listOf("BDT", "USD", "EUR", "GBP", "INR", "AED", "SAR", "CAD", "AUD", "JPY")

private fun currencyOptions(): List<Currency> =
    CURRENCY_CODES.mapNotNull { runCatching { Currency.getInstance(it) }.getOrNull() }

private fun currencyLabel(currency: Currency): String {
    val symbol = currency.symbol
    return if (symbol == currency.currencyCode) currency.currencyCode else "${currency.currencyCode} · $symbol"
}

private fun availabilityText(state: SettingsUiState, words: SettingsWording): String = when {
    !state.hasGeminiKey -> words.availabilityNoKey
    !state.settings.cloudAiEnabled -> words.availabilityOff
    state.cloudQuota != CloudQuotaStatus.AVAILABLE -> words.quotaLabel(state.cloudQuota)
    else -> words.availabilityReady
}

/** One selectable option in a settings sheet. */
private data class SelectionOption(val label: String, val supporting: String?, val selected: Boolean)

@Composable
private fun SettingsSelectionSheet(
    title: String,
    options: List<SelectionOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    MiraBottomSheet(onDismissRequest = onDismiss, title = title) {
        options.forEachIndexed { index, option ->
            if (index > 0) MiraDivider()
            MiraListRow(
                title = option.label,
                supporting = option.supporting,
                onClick = { onSelect(index) },
                trailing = {
                    if (option.selected) {
                        Icon(MiraIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                },
            )
        }
    }
}

/** Material's time picker, in Mira's dialog — the same shape the briefs use. */
@Composable
private fun QuietTimeDialog(
    title: String,
    initial: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    words: SettingsWording,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = false,
    )
    MiraDialog(
        title = title,
        onDismissRequest = onDismiss,
        confirmText = words.timeSet,
        onConfirm = { onPick(LocalTime.of(pickerState.hour, pickerState.minute)) },
        dismissText = words.timeCancel,
        onDismiss = onDismiss,
        content = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = pickerState)
            }
        },
    )
}

/** A segmented control with its own label and footnote above it. */
@Composable
private fun LabeledSegment(
    title: String,
    supporting: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val spacing = MiraTheme.spacing
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(spacing.xs))
    }
    MiraSegmentedControl(options = options, selectedIndex = selectedIndex, onSelect = onSelect)
    Spacer(Modifier.height(spacing.xs))
    SettingsFootnote(supporting)
}

/** A row that opens a selection sheet: its glyph, its label, and the current choice at the end. */
@Composable
private fun SelectorRow(
    title: String,
    supporting: String?,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraListRow(
        title = title,
        supporting = supporting,
        onClick = onClick,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(spacing.xs))
                Icon(MiraIcons.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

/** A read-only row: a fact Mira is reporting, with no control to touch. */
@Composable
private fun StatusRow(title: String, value: String, icon: ImageVector) {
    MiraListRow(
        title = title,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = { Text(value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

/** A row that states a single fixed value — like the one theme there is. No control, no chevron. */
@Composable
private fun StatementRow(title: String, value: String, icon: ImageVector) {
    MiraListRow(
        title = title,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = { Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

/** A system integration: its live grant state, tapping to the system screen that manages it. */
@Composable
private fun IntegrationRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    capability: MiraCapability,
    words: SettingsWording,
    context: Context,
    refreshKey: Int,
) {
    val granted = remember(refreshKey, capability) { context.hasGranted(capability) }
    MiraListRow(
        title = title,
        supporting = supporting,
        onClick = { capability.settingsIntents(context).firstOrNull()?.let { context.tryStartActivity(it) } },
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = {
            Text(
                text = if (granted) words.connected else words.notConnected,
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) MiraTheme.colors.positive else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

@Composable
private fun GeminiSection(
    hasKey: Boolean,
    words: SettingsWording,
    onAddKey: () -> Unit,
    onRemoveKey: () -> Unit,
) {
    val spacing = MiraTheme.spacing
    SettingsStatementCard(
        icon = MiraIcons.Cloud,
        iconTint = if (hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        title = if (hasKey) words.geminiOn else words.geminiOff,
        body = words.geminiDetail,
    ) {
        if (hasKey) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                MiraButton(text = words.replaceKey, onClick = onAddKey, modifier = Modifier.weight(1f), style = MiraButtonStyle.SECONDARY)
                MiraButton(text = words.removeKey, onClick = onRemoveKey, modifier = Modifier.weight(1f), style = MiraButtonStyle.SECONDARY)
            }
        } else {
            MiraButton(text = words.addKey, onClick = onAddKey, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SettingsStatementCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actions: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MiraTheme.spacing
    MiraCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = iconTint)
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = spacing.sm),
            )
        }
        Spacer(Modifier.height(spacing.xs))
        Text(text = body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(spacing.md))
        actions()
    }
}

@Composable
private fun SettingsFootnote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NavigationRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraListRow(
        title = title,
        modifier = modifier,
        supporting = supporting,
        onClick = onClick,
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = { Icon(MiraIcons.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
    )
}

@Composable
internal fun ToggleRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    MiraListRow(
        title = title,
        modifier = modifier,
        supporting = supporting.ifEmpty { null },
        onClick = { onCheckedChange(!checked) },
        leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = { MiraSwitch(checked = checked, onCheckedChange = onCheckedChange) },
    )
}

private fun Context.openExactAlarmSettings() {
    val intent = Intent(
        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.fromParts("package", packageName, null),
    )
    try {
        startActivity(intent)
    } catch (missing: ActivityNotFoundException) {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)),
        )
    }
}

@Composable
private fun OtaUpdateSection(
    state: SettingsUiState,
    words: SettingsWording,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: (AppUpdate) -> Unit,
    onInstallApk: (File) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MiraTheme.spacing

    when (val ota = state.otaState) {
        is OtaUpdateState.Idle -> {
            NavigationRow(
                title = words.checkForUpdates,
                supporting = words.checkForUpdatesDetail,
                icon = MiraIcons.Download,
                onClick = onCheckUpdate,
                modifier = modifier,
            )
        }
        is OtaUpdateState.Checking -> {
            MiraCard(modifier = modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = spacing.xs),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(spacing.md))
                    Text(
                        text = words.checkingForUpdates,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        is OtaUpdateState.UpToDate -> {
            MiraCard(modifier = modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = spacing.xs),
                ) {
                    Icon(
                        imageVector = MiraIcons.CheckCircle,
                        contentDescription = null,
                        tint = MiraTheme.colors.positive,
                    )
                    Spacer(Modifier.width(spacing.sm))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = words.appUpToDate,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "সর্বশেষ ভার্সন: v${ota.version}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    MiraButton(
                        text = "পুনরায় চেক",
                        onClick = onCheckUpdate,
                        style = MiraButtonStyle.SECONDARY,
                    )
                }
            }
        }
        is OtaUpdateState.Available -> {
            val update = ota.update
            MiraCard(
                modifier = modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MiraIcons.Download,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(spacing.xs))
                        Text(
                            text = "${words.updateAvailable}: v${update.latestVersionName}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (update.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(spacing.xs))
                        Text(
                            text = update.releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                        )
                    }
                    Spacer(Modifier.height(spacing.md))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        MiraButton(
                            text = "পরে",
                            onClick = onDismiss,
                            style = MiraButtonStyle.TEXT,
                        )
                        Spacer(Modifier.width(spacing.sm))
                        MiraButton(
                            text = words.downloadAndInstall,
                            onClick = { onDownloadUpdate(update) },
                            style = MiraButtonStyle.PRIMARY,
                        )
                    }
                }
            }
        }
        is OtaUpdateState.Downloading -> {
            val percent = (ota.progress * 100).toInt()
            MiraCard(modifier = modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = spacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = words.downloadingUpdate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(spacing.xs))
                    LinearProgressIndicator(
                        progress = { ota.progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MiraTheme.colors.accentWash,
                    )
                }
            }
        }
        is OtaUpdateState.ReadyToInstall -> {
            MiraCard(modifier = modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = MiraIcons.CheckCircle,
                            contentDescription = null,
                            tint = MiraTheme.colors.positive,
                        )
                        Spacer(Modifier.width(spacing.xs))
                        Text(
                            text = "ডাউনলোড সম্পন্ন!",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(Modifier.height(spacing.md))
                    MiraButton(
                        text = words.installNow,
                        onClick = { onInstallApk(ota.file) },
                        style = MiraButtonStyle.PRIMARY,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        is OtaUpdateState.Error -> {
            MiraInlineNotice(
                text = ota.message,
                tone = MiraNoticeTone.DANGER,
                actionText = "আবার চেষ্টা",
                onAction = onCheckUpdate,
                onDismiss = onDismiss,
            )
        }
    }
}
