package com.rayluxeo.aida.domain.automation

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.Automation
import com.rayluxeo.aida.domain.model.AutomationAction
import com.rayluxeo.aida.domain.model.AutomationTriggerConfig
import org.junit.Test
import java.time.Instant

class AutomationValidatorTest {

    private val validator = AutomationValidator()
    private val now = Instant.ofEpochMilli(1700000000000L)

    @Test
    fun `validate approves safe automation`() {
        val automation = Automation(
            name = "Weekly Planning Task",
            trigger = AutomationTriggerConfig.TimeOfDay(minuteOfDay = 20 * 60),
            action = AutomationAction.CreateTask(title = "Weekly planning"),
            createdAt = now,
            updatedAt = now,
        )

        val result = validator.validate(automation)
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `validate accepts an open-link action with a scheme`() {
        val result = validator.validate(
            Automation(
                name = "Open dashboard",
                trigger = AutomationTriggerConfig.AppStart,
                action = AutomationAction.OpenLink("https://dash.rayluxeo.com"),
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertThat(result).isInstanceOf(AppResult.Success::class.java)
    }

    @Test
    fun `validate rejects an open-link action with no scheme`() {
        val result = validator.validate(
            Automation(
                name = "Open dashboard",
                trigger = AutomationTriggerConfig.AppStart,
                action = AutomationAction.OpenLink("dash.rayluxeo.com"),
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `validate accepts a global action and a launch with a package, rejects a launch with none`() {
        val gesture = validator.validate(
            Automation(
                name = "Go home",
                trigger = AutomationTriggerConfig.NotificationPosted(),
                action = AutomationAction.PerformGlobalAction(
                    com.rayluxeo.aida.domain.model.GlobalDeviceAction.HOME,
                ),
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertThat(gesture).isInstanceOf(AppResult.Success::class.java)

        val launch = validator.validate(
            Automation(
                name = "Open maps",
                trigger = AutomationTriggerConfig.AppStart,
                action = AutomationAction.LaunchApp(packageName = "com.maps", label = "Maps"),
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertThat(launch).isInstanceOf(AppResult.Success::class.java)

        val blank = validator.validate(
            Automation(
                name = "Open nothing",
                trigger = AutomationTriggerConfig.AppStart,
                action = AutomationAction.LaunchApp(packageName = "", label = ""),
                createdAt = now,
                updatedAt = now,
            ),
        )
        assertThat(blank).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `validate rejects blank name`() {
        val automation = Automation(
            name = "   ",
            trigger = AutomationTriggerConfig.Manual,
            action = AutomationAction.Notify(title = "Alert"),
            createdAt = now,
            updatedAt = now,
        )

        val result = validator.validate(automation)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    }

    @Test
    fun `validate rejects dangerous action attempting silent messaging or payment recording`() {
        val dangerousAction = AutomationAction.RunUtterance("send invoice to client")
        val automation = Automation(
            name = "Send invoice rule",
            trigger = AutomationTriggerConfig.Manual,
            action = dangerousAction,
            createdAt = now,
            updatedAt = now,
        )

        val result = validator.validate(automation)
        assertThat(result).isInstanceOf(AppResult.Failure::class.java)
    }
}
