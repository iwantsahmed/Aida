package com.rayluxeo.aida.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The notification trigger's whole correctness is in [AutomationTriggerConfig.NotificationPosted.matches]
 * — the pure predicate the listener service and [com.rayluxeo.aida.domain.automation.AutomationScheduler]
 * both run before firing. If it is too loose, a rule meant for one app fires for every app; too tight,
 * and a rule never fires. These pin both filters, each optional.
 */
class NotificationPostedMatchesTest {

    @Test
    fun `no filters fires for any notification`() {
        val any = AutomationTriggerConfig.NotificationPosted()

        assertThat(any.matches("com.anything", "Anything")).isTrue()
        assertThat(any.matches("com.other", null)).isTrue()
    }

    @Test
    fun `a package filter fires only for that app, case-insensitively`() {
        val bankOnly = AutomationTriggerConfig.NotificationPosted(packageName = "com.bank.app")

        assertThat(bankOnly.matches("com.bank.app", "Payment received")).isTrue()
        assertThat(bankOnly.matches("COM.BANK.APP", "Payment received")).isTrue()
        assertThat(bankOnly.matches("com.other.app", "Payment received")).isFalse()
    }

    @Test
    fun `a title filter needs the phrase in the title`() {
        val received = AutomationTriggerConfig.NotificationPosted(titleContains = "received")

        assertThat(received.matches("com.bank", "Payment received")).isTrue()
        assertThat(received.matches("com.bank", "payment RECEIVED today")).isTrue()
        assertThat(received.matches("com.bank", "Payment sent")).isFalse()
        // A title filter with no title to read cannot match.
        assertThat(received.matches("com.bank", null)).isFalse()
    }

    @Test
    fun `both filters must hold together`() {
        val both = AutomationTriggerConfig.NotificationPosted(packageName = "com.bank", titleContains = "received")

        assertThat(both.matches("com.bank", "Payment received")).isTrue()
        assertThat(both.matches("com.bank", "Payment sent")).isFalse()
        assertThat(both.matches("com.other", "Payment received")).isFalse()
    }
}
