package com.rayluxeo.aida.ui.vault

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.data.FakeMiraClock
import java.time.LocalDateTime
import org.junit.Test

/**
 * The Auto-lock delay, proved against a clock the test owns. The delay used to be a label on a
 * control that did nothing; these pin that it now re-locks on the terms Abu set.
 */
class AppLockControllerTest {

    private val start = LocalDateTime.of(2026, 10, 15, 9, 0)
    private val clock = FakeMiraClock(start)
    private val controller = AppLockController(clock)

    @Test
    fun `a fresh controller is locked`() {
        assertThat(controller.locked.value).isTrue()
    }

    @Test
    fun `a return with no recorded departure leaves the lock as it is`() {
        controller.unlock()
        controller.onReturnedToForeground(timeoutSeconds = 60)
        assertThat(controller.locked.value).isFalse()
    }

    @Test
    fun `lock when I leave shuts it the moment the app leaves`() {
        controller.unlock()
        controller.onLeftForeground(lockImmediately = true)
        assertThat(controller.locked.value).isTrue()
    }

    @Test
    fun `away less than the delay stays open, and away at least the delay re-locks`() {
        controller.unlock()
        controller.onLeftForeground(lockImmediately = false)
        assertThat(controller.locked.value).isFalse()

        clock.moveTo(start.plusSeconds(59))
        controller.onReturnedToForeground(timeoutSeconds = 60)
        assertThat(controller.locked.value).isFalse()

        controller.onLeftForeground(lockImmediately = false)
        clock.moveTo(start.plusSeconds(59 + 60))
        controller.onReturnedToForeground(timeoutSeconds = 60)
        assertThat(controller.locked.value).isTrue()
    }

    @Test
    fun `a zero delay re-locks on every return`() {
        controller.unlock()
        controller.onLeftForeground(lockImmediately = false)
        controller.onReturnedToForeground(timeoutSeconds = 0)
        assertThat(controller.locked.value).isTrue()
    }
}
