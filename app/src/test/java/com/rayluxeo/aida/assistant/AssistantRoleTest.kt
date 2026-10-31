package com.rayluxeo.aida.assistant

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The decisions inside [AssistantRoleControllerImpl] that do not need a phone: parsing what
 * `Settings.Secure` stored, choosing a label worth showing, turning the platform's answer into a
 * state, and the Samsung branch.
 *
 * What is deliberately not here, because it cannot be honestly faked in a local test:
 * `RoleManager.isRoleHeld`, whether `Settings.Secure` will hand a value to a target this high, and
 * whether any of the three settings screens resolve. Those are device checks on the S24 Ultra.
 */
class AssistantRoleTest {

    // ------------------------------------------------------------------ component parsing

    @Test
    fun `no stored assistant parses to nothing`() {
        assertThat(parseAssistantComponent(null)).isNull()
        assertThat(parseAssistantComponent("")).isNull()
        assertThat(parseAssistantComponent("   ")).isNull()
    }

    @Test
    fun `parses a fully qualified component`() {
        val component = parseAssistantComponent(
            "com.samsung.android.bixby.agent/com.samsung.android.bixby.agent.mainui.MainUiService",
        )

        assertThat(component?.packageName).isEqualTo("com.samsung.android.bixby.agent")
        assertThat(component?.className)
            .isEqualTo("com.samsung.android.bixby.agent.mainui.MainUiService")
    }

    @Test
    fun `expands a relative class against its package`() {
        val component = parseAssistantComponent(
            "com.samsung.android.bixby.agent/.mainui.MainUiService",
        )

        assertThat(component?.packageName).isEqualTo("com.samsung.android.bixby.agent")
        assertThat(component?.className)
            .isEqualTo("com.samsung.android.bixby.agent.mainui.MainUiService")
    }

    @Test
    fun `takes a bare package as the holder with no class`() {
        val component = parseAssistantComponent("com.rayluxeo.aida")

        assertThat(component?.packageName).isEqualTo("com.rayluxeo.aida")
        assertThat(component?.className).isNull()
    }

    @Test
    fun `an empty class leaves the package standing`() {
        val component = parseAssistantComponent("com.rayluxeo.aida/")

        assertThat(component?.packageName).isEqualTo("com.rayluxeo.aida")
        assertThat(component?.className).isNull()
    }

    @Test
    fun `a component with no package is not a holder`() {
        assertThat(parseAssistantComponent("/com.example.AssistService")).isNull()
    }

    @Test
    fun `surrounding whitespace is not part of the name`() {
        val component = parseAssistantComponent("  com.example / .AssistService  ")

        assertThat(component?.packageName).isEqualTo("com.example")
        assertThat(component?.className).isEqualTo("com.example.AssistService")
    }

    // ---------------------------------------------------------------------- holder label

    @Test
    fun `prefers the service label`() {
        val label = preferredHolderLabel(
            serviceLabel = "Bixby Voice",
            applicationLabel = "Bixby",
            packageName = "com.samsung.android.bixby.agent",
        )

        assertThat(label).isEqualTo("Bixby Voice")
    }

    @Test
    fun `falls back to the application label`() {
        val label = preferredHolderLabel(
            serviceLabel = null,
            applicationLabel = "Bixby",
            packageName = "com.samsung.android.bixby.agent",
        )

        assertThat(label).isEqualTo("Bixby")
    }

    @Test
    fun `a blank label is no label`() {
        val label = preferredHolderLabel(
            serviceLabel = "   ",
            applicationLabel = "Bixby",
            packageName = "com.samsung.android.bixby.agent",
        )

        assertThat(label).isEqualTo("Bixby")
    }

    @Test
    fun `a label that only repeats the package is not shown`() {
        val label = preferredHolderLabel(
            serviceLabel = "COM.EXAMPLE.ASSIST",
            applicationLabel = "com.example.assist",
            packageName = "com.example.assist",
        )

        assertThat(label).isNull()
    }

    @Test
    fun `nothing readable means no name is invented`() {
        val label = preferredHolderLabel(
            serviceLabel = null,
            applicationLabel = null,
            packageName = "com.example.assist",
        )

        assertThat(label).isNull()
    }

    // ---------------------------------------------------------------------------- state

    @Test
    fun `holding the role wins even with nowhere to change it`() {
        assertThat(assistantRoleStateOf(roleHeld = true, hasSettingsScreen = false))
            .isEqualTo(AssistantRoleState.HELD)
    }

    @Test
    fun `not holding it with a screen to change it is available`() {
        assertThat(assistantRoleStateOf(roleHeld = false, hasSettingsScreen = true))
            .isEqualTo(AssistantRoleState.AVAILABLE)
    }

    @Test
    fun `not holding it with nowhere to change it is unsupported`() {
        assertThat(assistantRoleStateOf(roleHeld = false, hasSettingsScreen = false))
            .isEqualTo(AssistantRoleState.UNSUPPORTED)
    }

    @Test
    fun `a platform that would not answer is unsupported, not merely unheld`() {
        assertThat(assistantRoleStateOf(roleHeld = null, hasSettingsScreen = true))
            .isEqualTo(AssistantRoleState.UNSUPPORTED)
    }

    // ------------------------------------------------------------------------ OEM branch

    @Test
    fun `Samsung is recognised whatever case the build reports`() {
        listOf("samsung", "SAMSUNG", "  Samsung  ").forEach { manufacturer ->
            val guidance = samsungGuidance(manufacturer, SpokenLanguage.ENGLISH)

            assertThat(guidance).isNotNull()
            assertThat(guidance?.manufacturer).isEqualTo("Samsung")
        }
    }

    @Test
    fun `no guidance is invented for another manufacturer`() {
        listOf("Google", "Xiaomi", "OnePlus", "").forEach { manufacturer ->
            assertThat(samsungGuidance(manufacturer, SpokenLanguage.ENGLISH)).isNull()
        }
    }

    @Test
    fun `Samsung guidance covers both the role and the side button`() {
        val steps = requireNotNull(samsungGuidance("samsung", SpokenLanguage.ENGLISH)).steps

        assertThat(steps).isNotEmpty()
        assertThat(steps.first()).contains("Digital assistant app")
        assertThat(steps.any { it.contains("side button") }).isTrue()
    }

    @Test
    fun `Samsung guidance says the same in Bengali`() {
        val english = requireNotNull(samsungGuidance("samsung", SpokenLanguage.ENGLISH)).steps
        val bengali = requireNotNull(samsungGuidance("samsung", SpokenLanguage.BENGALI)).steps

        assertThat(bengali).hasSize(english.size)
        assertThat(bengali).isNotEqualTo(english)
        assertThat(bengali.any { it.contains("আইদা") }).isTrue()
        // The menu itself is in English on the phone, so the path is quoted as Abu will see it.
        assertThat(bengali.first()).contains("Digital assistant app")
        assertThat(bengali.any { it.contains("side button") }).isTrue()
    }

    @Test
    fun `the guidance carries no intent of its own`() {
        val guidance = requireNotNull(samsungGuidance("samsung", SpokenLanguage.ENGLISH))

        assertThat(guidance.settingsIntent).isNull()
    }

    // --------------------------------------------------------------------------- language

    @Test
    fun `guidance follows the language Abu chose`() {
        assertThat(guidanceLanguage(LanguageMode.BANGLA)).isEqualTo(SpokenLanguage.BENGALI)
        assertThat(guidanceLanguage(LanguageMode.ENGLISH)).isEqualTo(SpokenLanguage.ENGLISH)
    }

    @Test
    fun `auto has no utterance to read on a settings screen`() {
        assertThat(guidanceLanguage(LanguageMode.AUTO)).isEqualTo(SpokenLanguage.ENGLISH)
    }
}
