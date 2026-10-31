package com.rayluxeo.aida.ui.automation

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationOutcome
import com.rayluxeo.aida.domain.model.AutomationTrigger
import com.rayluxeo.aida.domain.model.GlobalDeviceAction
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import org.junit.Test

/**
 * The Automations phrasebook is the one place the list, the two sheets and the view model's own
 * notices get their words, so a missed translation here is not a red build — it is an English label
 * in a Bengali screen, which nobody notices until Abu does.
 *
 * These walk every trigger and every outcome rather than sampling one, because the fault this guards
 * against is a value added to an enum and worded in English on one side only.
 */
class AutomationsWordingTest {

    private val english = automationsWording(SpokenLanguage.ENGLISH)
    private val bengali = automationsWording(SpokenLanguage.BENGALI)

    @Test
    fun `each language resolves to its own phrasebook`() {
        assertThat(english).isSameInstanceAs(EnglishAutomationsWording)
        assertThat(bengali).isSameInstanceAs(BengaliAutomationsWording)
    }

    @Test
    fun `every fixed label is written in both languages`() {
        STATIC_LABELS.forEach { label ->
            assertThat(label(english)).isNotEmpty()
            assertThat(label(bengali)).isNotEmpty()
        }
    }

    @Test
    fun `no fixed label was left in English on the Bengali side`() {
        STATIC_LABELS.forEach { label ->
            assertThat(label(bengali)).isNotEqualTo(label(english))
        }
    }

    /**
     * A Bengali label with a stray Latin letter in it reads as broken even to someone who cannot
     * read the words around it. The runtime notices are left out here on purpose: they frame a name
     * or a developer detail that is Latin by nature.
     */
    @Test
    fun `no fixed Bengali label is written in Latin letters`() {
        STATIC_LABELS.forEach { label ->
            val value = label(bengali)
            assertThat(value.none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
        }
    }

    @Test
    fun `every trigger is labelled in both languages, and differently`() {
        AutomationTrigger.entries.forEach { trigger ->
            assertThat(english.triggerLabel(trigger)).isNotEmpty()
            assertThat(bengali.triggerLabel(trigger)).isNotEmpty()
            assertThat(bengali.triggerLabel(trigger)).isNotEqualTo(english.triggerLabel(trigger))

            assertThat(english.triggerChip(trigger)).isNotEmpty()
            assertThat(bengali.triggerChip(trigger)).isNotEmpty()
            assertThat(bengali.triggerChip(trigger)).isNotEqualTo(english.triggerChip(trigger))
        }
    }

    @Test
    fun `no Bengali trigger label is written in Latin letters`() {
        AutomationTrigger.entries.forEach { trigger ->
            val value = bengali.triggerLabel(trigger)
            assertThat(value.none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
        }
    }

    @Test
    fun `every record trigger has a note in both languages, differently, and Bengali in Bengali`() {
        val recordTriggers = listOf(
            AutomationTrigger.INVOICE_DUE,
            AutomationTrigger.INVOICE_OVERDUE,
            AutomationTrigger.TASK_DUE,
            AutomationTrigger.TASK_OVERDUE,
            AutomationTrigger.SUBSCRIPTION_RENEWAL,
            AutomationTrigger.PROJECT_DEADLINE,
        )
        recordTriggers.forEach { trigger ->
            assertThat(english.recordTriggerNote(trigger)).isNotEmpty()
            assertThat(bengali.recordTriggerNote(trigger)).isNotEmpty()
            assertThat(bengali.recordTriggerNote(trigger)).isNotEqualTo(english.recordTriggerNote(trigger))
            assertThat(bengali.recordTriggerNote(trigger).none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
        }
    }

    @Test
    fun `every place-watch refusal is worded in both languages, differently, and Bengali in Bengali`() {
        // The whole point of the refusal path is to tell Abu the truth when a place rule saved but is
        // not being watched (§5), so every reason has to carry real words on both sides. This walks
        // the enum rather than sampling, because a reason added later and worded on one side only is
        // exactly the silent gap this guards.
        PlaceWatchRefusal.entries.forEach { reason ->
            assertThat(english.placeWatchRefused(reason)).isNotEmpty()
            assertThat(bengali.placeWatchRefused(reason)).isNotEmpty()
            assertThat(bengali.placeWatchRefused(reason)).isNotEqualTo(english.placeWatchRefused(reason))
            assertThat(bengali.placeWatchRefused(reason).none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
        }
    }

    @Test
    fun `the place radius note carries the distance in each script's own digits`() {
        // English keeps ASCII digits; Bengali must rewrite them, or an invoice-grade screen shows a
        // Latin "150" sitting in a Bengali sentence.
        assertThat(english.placeRadiusNote(150)).contains("150")
        assertThat(bengali.placeRadiusNote(150)).contains("১৫০")
        assertThat(bengali.placeRadiusNote(150).none { it in '0'..'9' }).isTrue()
        assertThat(bengali.placeRadiusNote(150).none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
    }

    @Test
    fun `every global action is labelled in both languages, differently, and Bengali in Bengali`() {
        GlobalDeviceAction.entries.forEach { action ->
            assertThat(english.globalActionLabel(action)).isNotEmpty()
            assertThat(bengali.globalActionLabel(action)).isNotEmpty()
            assertThat(bengali.globalActionLabel(action)).isNotEqualTo(english.globalActionLabel(action))
            assertThat(bengali.globalActionLabel(action).none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
        }
    }

    @Test
    fun `every weekday is labelled in both languages, differently, and Bengali in Bengali`() {
        java.time.DayOfWeek.entries.forEach { day ->
            assertThat(english.dayLabel(day)).isNotEmpty()
            assertThat(bengali.dayLabel(day)).isNotEmpty()
            assertThat(bengali.dayLabel(day)).isNotEqualTo(english.dayLabel(day))
            assertThat(bengali.dayLabel(day).none { it in 'a'..'z' || it in 'A'..'Z' }).isTrue()
        }
    }

    @Test
    fun `every outcome is worded in both languages, and differently`() {
        AutomationOutcome.entries.forEach { outcome ->
            assertThat(english.outcomeLabel(outcome)).isNotEmpty()
            assertThat(bengali.outcomeLabel(outcome)).isNotEmpty()
            assertThat(bengali.outcomeLabel(outcome)).isNotEqualTo(english.outcomeLabel(outcome))

            assertThat(bengali.runOutcome(outcome)).isNotEqualTo(english.runOutcome(outcome))
            assertThat(bengali.runFinished(outcome)).isNotEqualTo(english.runFinished(outcome))
        }
    }

    @Test
    fun `the action chip is worded in both languages, and differently`() {
        val action = AutomationAction.GenerateDailyBrief

        assertThat(english.actionChip(action)).isNotEmpty()
        assertThat(bengali.actionChip(action)).isNotEmpty()
        assertThat(bengali.actionChip(action)).isNotEqualTo(english.actionChip(action))
    }

    @Test
    fun `every runtime notice is worded in both languages, and differently`() {
        NOTICES.forEach { notice ->
            assertThat(notice(english)).isNotEmpty()
            assertThat(notice(bengali)).isNotEmpty()
            assertThat(notice(bengali)).isNotEqualTo(notice(english))
        }
    }

    private companion object {
        /** Every member that is a fixed string, so the walks above can hit each one. */
        val STATIC_LABELS: List<(AutomationsWording) -> String> = listOf(
            { it.title },
            { it.newRule },
            { it.noRulesMessage },
            { it.addRule },
            { it.triggerWord },
            { it.actionWord },
            { it.outcomeWord },
            { it.runManually },
            { it.runHistory },
            { it.editAutomation },
            { it.deleteAutomation },
            { it.noRunsMessage },
            { it.createRuleTitle },
            { it.editRuleTitle },
            { it.nameLabel },
            { it.namePlaceholder },
            { it.descriptionLabel },
            { it.triggerSectionLabel },
            { it.manualTriggerNote },
            { it.choosePlaceLabel },
            { it.noSavedPlacesYet },
            { it.openPlacesAction },
            { it.actionPerformGlobalAction },
            { it.actionLaunchApp },
            { it.chooseGestureLabel },
            { it.chooseAppLabel },
            { it.noAppsFound },
            { it.crossAppTierOffNote },
            { it.accessibilityOffNote },
            { it.enableAccessibilityAction },
            { it.notificationFromAppLabel },
            { it.notificationAnyApp },
            { it.notificationTitleFilterLabel },
            { it.notificationTitleFilterPlaceholder },
            { it.notificationAccessOffNote },
            { it.enableNotificationAccessAction },
            { it.actionSectionLabel },
            { it.actionRunCommand },
            { it.actionNotify },
            { it.actionOpenLink },
            { it.actionDailyBrief },
            { it.actionFieldLabel },
            { it.actionPlaceholder },
            { it.notifyTitleLabel },
            { it.notifyBodyLabel },
            { it.linkLabel },
            { it.timeLabel },
            { it.daysLabel },
            { it.everyDayNote },
            { it.intervalLabel },
            { it.intervalPlaceholder },
            { it.timePickerTitle },
            { it.setLabel },
            { it.cancelLabel },
            { it.enableImmediately },
            { it.createRuleButton },
            { it.saveChangesButton },
            { it.runningAutomation },
            { it.automationDeleted },
        )

        /** The notices that frame runtime data; checked for wording, not for script. */
        val NOTICES: List<(AutomationsWording) -> String> = listOf(
            { it.runHistoryTitle("Morning Brief") },
            { it.runFailed("storage busy") },
            { it.automationSaved("Morning Brief") },
            { it.saveFailed("storage busy") },
            { it.validationError("name required") },
            { it.deleteFailed("storage busy") },
        )
    }
}
