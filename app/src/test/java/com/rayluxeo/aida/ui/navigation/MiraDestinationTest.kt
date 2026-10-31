package com.rayluxeo.aida.ui.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * MiraDestination is a contract: the graph registers one composable per route and the hub lists
 * the same enum. Each invariant below would otherwise fail on the device as a blank screen or a
 * dead row rather than as a red build.
 *
 * What each area is *called* is no longer part of that contract — it changes with Abu's language
 * and is covered by `ShellWordingTest`.
 */
class MiraDestinationTest {

    @Test
    fun `every area has a distinct, non-blank route`() {
        val routes = MiraArea.entries.map { it.route }

        assertThat(routes).containsNoDuplicates()
        assertThat(routes.filter { it.isBlank() }).isEmpty()
    }

    @Test
    fun `every tab has a distinct, non-blank route`() {
        val routes = MiraTab.entries.map { it.route }

        assertThat(routes).containsNoDuplicates()
        assertThat(routes.filter { it.isBlank() }).isEmpty()
    }

    @Test
    fun `no area route collides with a tab route`() {
        val tabRoutes = MiraTab.entries.map { it.route }.toSet()

        assertThat(MiraArea.entries.filter { it.route in tabRoutes }).isEmpty()
    }

    @Test
    fun `fromRoute round-trips every area`() {
        MiraArea.entries.forEach { area ->
            assertThat(MiraArea.fromRoute(area.route)).isEqualTo(area)
        }
    }

    @Test
    fun `fromRoute answers for nothing it does not own`() {
        assertThat(MiraArea.fromRoute(MiraRoutes.HOME)).isNull()
        assertThat(MiraArea.fromRoute(MiraRoutes.ONBOARDING)).isNull()
        assertThat(MiraArea.fromRoute(null)).isNull()
    }

    @Test
    fun `the hub lists every area exactly once`() {
        assertThat(MiraArea.hubOrder).containsExactlyElementsIn(MiraArea.entries).inOrder()
    }
}
