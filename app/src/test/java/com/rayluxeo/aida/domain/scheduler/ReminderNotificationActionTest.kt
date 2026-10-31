package com.rayluxeo.aida.domain.scheduler

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.model.ReminderKind
import org.junit.Test

/**
 * Which buttons a reminder gets.
 *
 * The failure this guards against is not a crash: it is a button that cannot do anything, which
 * Abu presses once, sees nothing happen, and stops trusting. Complete on a birthday and a
 * ten-minute snooze on a place reminder are both that button.
 */
class ReminderNotificationActionTest {

    private fun actionsFor(kind: ReminderKind, hasLinkedTask: Boolean = false) =
        ReminderNotificationAction.forKind(kind, hasLinkedTask)

    @Test
    fun `no kind asks for more buttons than Android will draw`() {
        for (kind in ReminderKind.entries) {
            for (linked in listOf(true, false)) {
                assertThat(actionsFor(kind, linked).size)
                    .isAtMost(ReminderNotificationAction.MAX_SHOWN)
            }
        }
    }

    @Test
    fun `every kind offers at least one button`() {
        for (kind in ReminderKind.entries) {
            for (linked in listOf(true, false)) {
                assertThat(actionsFor(kind, linked)).isNotEmpty()
            }
        }
    }

    @Test
    fun `no button is offered twice`() {
        for (kind in ReminderKind.entries) {
            for (linked in listOf(true, false)) {
                assertThat(actionsFor(kind, linked)).containsNoDuplicates()
            }
        }
    }

    /** There is nothing to complete about somebody's birthday. */
    @Test
    fun `a birthday is never offered Complete`() {
        for (linked in listOf(true, false)) {
            assertThat(actionsFor(ReminderKind.BIRTHDAY, linked))
                .doesNotContain(ReminderNotificationAction.COMPLETE)
        }
    }

    @Test
    fun `Complete appears only where there is a task behind the reminder`() {
        for (kind in ReminderKind.entries) {
            assertThat(actionsFor(kind, hasLinkedTask = false))
                .doesNotContain(ReminderNotificationAction.COMPLETE)
        }
        assertThat(actionsFor(ReminderKind.TASK, hasLinkedTask = true))
            .contains(ReminderNotificationAction.COMPLETE)
    }

    /** A task reminder whose task was deleted still has to offer something that works. */
    @Test
    fun `a task reminder with no task left falls back to buttons that work`() {
        val actions = actionsFor(ReminderKind.TASK, hasLinkedTask = false)
        assertThat(actions).containsExactly(
            ReminderNotificationAction.SNOOZE_TEN_MINUTES,
            ReminderNotificationAction.SNOOZE_ONE_HOUR,
            ReminderNotificationAction.ASK_MIRA,
        ).inOrder()
    }

    /**
     * Pushing a place reminder ten minutes into the future would not re-arm the place, so a time
     * snooze on one is a button that lies.
     */
    @Test
    fun `a place reminder is never offered a time snooze`() {
        for (linked in listOf(true, false)) {
            assertThat(actionsFor(ReminderKind.PLACE, linked)).containsNoneOf(
                ReminderNotificationAction.SNOOZE_TEN_MINUTES,
                ReminderNotificationAction.SNOOZE_ONE_HOUR,
            )
        }
    }

    @Test
    fun `a place reminder tied to a task can still close it`() {
        assertThat(actionsFor(ReminderKind.PLACE, hasLinkedTask = true))
            .contains(ReminderNotificationAction.COMPLETE)
    }

    @Test
    fun `the money and people kinds lead with the record`() {
        val recordKinds = listOf(
            ReminderKind.INVOICE,
            ReminderKind.CLIENT_FOLLOW_UP,
            ReminderKind.PROJECT_DEADLINE,
            ReminderKind.SUBSCRIPTION,
        )
        for (kind in recordKinds) {
            assertThat(actionsFor(kind).first()).isEqualTo(ReminderNotificationAction.OPEN)
        }
    }

    @Test
    fun `a plain reminder can be pushed away or handed back to Mira`() {
        assertThat(actionsFor(ReminderKind.ONE_TIME)).containsExactly(
            ReminderNotificationAction.SNOOZE_TEN_MINUTES,
            ReminderNotificationAction.SNOOZE_ONE_HOUR,
            ReminderNotificationAction.ASK_MIRA,
        ).inOrder()
    }
}
