package com.rayluxeo.aida.ui.mira

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiraEntryCoordinatorTest {

    @Test
    fun `starts with nothing pending`() {
        assertThat(MiraEntryCoordinator().pending.value).isNull()
    }

    @Test
    fun `request holds the intent until it is consumed`() {
        val coordinator = MiraEntryCoordinator()

        coordinator.request(MiraEntryIntent.StartListening)
        assertThat(coordinator.pending.value).isEqualTo(MiraEntryIntent.StartListening)

        coordinator.consume()
        assertThat(coordinator.pending.value).isNull()
    }

    @Test
    fun `the latest request wins`() {
        val coordinator = MiraEntryCoordinator()

        coordinator.request(MiraEntryIntent.Ask("first"))
        coordinator.request(MiraEntryIntent.Ask("second"))

        assertThat(coordinator.pending.value).isEqualTo(MiraEntryIntent.Ask("second"))
    }
}
