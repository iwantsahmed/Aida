package com.rayluxeo.aida.ui.settings

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.data.FakeMiraClock
import com.rayluxeo.aida.domain.location.PlaceFix
import com.rayluxeo.aida.domain.model.SavedPlace
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import com.rayluxeo.aida.domain.usecase.DeletePlaceUseCase
import com.rayluxeo.aida.domain.usecase.RenamePlaceUseCase
import com.rayluxeo.aida.domain.usecase.SavePlaceHereUseCase
import com.rayluxeo.aida.domain.usecase.SavePlaceUseCase
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The Places screen, assembled by hand — Hilt's local-test transform does not run under AGP 9.
 *
 * The use cases are the real ones over fake ports, the way `TasksViewModelTest` builds its own:
 * what is worth pinning here is the decision the screen makes from what the platform answered,
 * and a hand-stubbed use case would let that decision be tested against a fiction.
 *
 * The mistake these tests exist to catch is the one Abu cannot see. A fix that never happened and
 * a place that was never stored must not leave the screen looking as though it had, because the
 * only other way he would find out is a reminder that never fires.
 */
class PlacesViewModelTest {

    private val clock = FakeMiraClock(LocalDateTime.of(2026, 3, 5, 9, 0))
    private val places = FakeSavedPlaceRepository()
    private val finder = FakeCurrentPlaceFinder()
    private val settings = FakeSettingsRepository()

    @Before
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun removeMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the list is the repository's, and follows it`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.state.test {
            val empty = awaitItem()
            assertThat(empty.loading).isFalse()
            assertThat(empty.places).isEmpty()
            assertThat(empty.isEmpty).isTrue()

            seed("Studio")
            seed("Office")

            val listed = expectMostRecentItem()
            // Alphabetical, as the port promises — the screen renders whatever order arrives.
            assertThat(listed.places.map { it.label }).containsExactly("Office", "Studio").inOrder()
            assertThat(listed.isEmpty).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a blank name is refused before the radio is ever touched`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.beginSaving()
        viewModel.onDraftChange("   ")
        viewModel.confirmSave()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.problem).isEqualTo(PlacesProblem.NAME_BLANK)
        assertThat(state.prompt).isNull()
        assertThat(state.saving).isFalse()
        assertThat(state.refusal).isNull()
        // A name that cannot be stored is no reason to ask the phone where Abu is.
        assertThat(finder.asked).isEqualTo(0)
        assertThat(places.stored).isEmpty()
    }

    @Test
    fun `no fix surfaces the refusal and never claims a place was saved`() = runTest {
        finder.fix = PlaceFix.Unavailable(PlaceWatchRefusal.LOCATION_DISABLED)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.beginSaving()
        viewModel.onDraftChange("Office")
        viewModel.confirmSave()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertThat(state.refusal).isEqualTo(PlaceWatchRefusal.LOCATION_DISABLED)
        // Not a failure: the phone declined to say where he is, and Mira did nothing wrong.
        assertThat(state.problem).isNull()
        assertThat(state.places).isEmpty()
        assertThat(state.saving).isFalse()
        // The name survives, so granting the permission and trying again is one tap.
        assertThat(state.draftLabel).isEqualTo("Office")
        assertThat(places.stored).isEmpty()
    }

    @Test
    fun `a fix that did not arrive is answered by asking again`() = runTest {
        finder.fix = PlaceFix.Unavailable(PlaceWatchRefusal.NO_FIX_YET)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.beginSaving()
        viewModel.onDraftChange("Office")
        viewModel.confirmSave()
        advanceUntilIdle()

        assertThat(viewModel.state.value.refusal).isEqualTo(PlaceWatchRefusal.NO_FIX_YET)
        assertThat(viewModel.state.value.draftLabel).isEqualTo("Office")
        assertThat(places.stored).isEmpty()
        // Nothing on a settings screen would change this one, so the notice offers a retry
        // instead — and this is the retry.
        assertThat(PlaceWatchRefusal.NO_FIX_YET.remedy()).isEqualTo(PlaceRefusalRemedy.RETRY)

        // The platform never said how accurate it was, which is null rather than zero.
        finder.fix = PlaceFix.Found(
            latitude = FakeCurrentPlaceFinder.DHAKA_LATITUDE,
            longitude = FakeCurrentPlaceFinder.DHAKA_LONGITUDE,
            accuracyMeters = null,
        )
        viewModel.confirmSave()
        advanceUntilIdle()

        assertThat(finder.asked).isEqualTo(2)
        assertThat(places.stored.single().label).isEqualTo("Office")
        assertThat(viewModel.state.value.refusal).isNull()
    }

    @Test
    fun `a fix is kept under the name Abu gave, and the draft is spent`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.beginSaving()
        viewModel.onDraftChange("  Office  ")
        viewModel.confirmSave()
        advanceUntilIdle()

        val stored = places.stored.single()
        assertThat(stored.label).isEqualTo("Office")
        // Derived by the use case, never by the screen: the lookup and the index agree by
        // construction, and "the office" spoken later has to reach this row.
        assertThat(stored.labelKey).isEqualTo("office")
        assertThat(stored.latitude).isEqualTo(FakeCurrentPlaceFinder.DHAKA_LATITUDE)
        assertThat(stored.longitude).isEqualTo(FakeCurrentPlaceFinder.DHAKA_LONGITUDE)
        assertThat(stored.radiusMeters).isEqualTo(SavePlaceUseCase.DEFAULT_RADIUS_METERS)

        val state = viewModel.state.value
        assertThat(state.places.map { it.label }).containsExactly("Office")
        assertThat(state.draftLabel).isEmpty()
        assertThat(state.problem).isNull()
        assertThat(state.refusal).isNull()
        assertThat(state.saving).isFalse()
    }

    @Test
    fun `renaming a place renames its row and re-derives the key`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val office = seed("Office")

        viewModel.beginRenaming(office)
        assertThat(viewModel.state.value.prompt).isEqualTo(PlacePrompt.Renaming(office.id))
        // The draft starts at the name it already has, so a small correction is a small edit.
        assertThat(viewModel.state.value.draftLabel).isEqualTo("Office")

        viewModel.onDraftChange("The studio")
        viewModel.confirmRename()
        advanceUntilIdle()

        assertThat(viewModel.state.value.places.map { it.label }).containsExactly("The studio")
        assertThat(places.stored.single().labelKey).isEqualTo("studio")
        assertThat(viewModel.state.value.problem).isNull()
        assertThat(viewModel.state.value.prompt).isNull()
    }

    @Test
    fun `renaming onto a name already taken changes nothing and says so`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val office = seed("Office")
        seed("Studio")

        viewModel.beginRenaming(office)
        viewModel.onDraftChange("studio")
        viewModel.confirmRename()
        advanceUntilIdle()

        assertThat(viewModel.state.value.problem).isEqualTo(PlacesProblem.NAME_TAKEN)
        assertThat(places.stored.map { it.label }).containsExactly("Office", "Studio")
    }

    @Test
    fun `renaming to nothing is refused and the place keeps its name`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val office = seed("Office")

        viewModel.beginRenaming(office)
        viewModel.onDraftChange("")
        viewModel.confirmRename()
        advanceUntilIdle()

        assertThat(viewModel.state.value.problem).isEqualTo(PlacesProblem.NAME_BLANK)
        assertThat(places.stored.single().label).isEqualTo("Office")
    }

    @Test
    fun `forgetting a place takes its row away`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val office = seed("Office")
        seed("Studio")

        viewModel.beginForgetting(office)
        assertThat(viewModel.state.value.prompt)
            .isEqualTo(PlacePrompt.Forgetting(office.id, "Office"))

        viewModel.confirmForget()
        advanceUntilIdle()

        assertThat(viewModel.state.value.places.map { it.label }).containsExactly("Studio")
        assertThat(places.stored.map { it.label }).containsExactly("Studio")
        assertThat(viewModel.state.value.prompt).isNull()
        assertThat(viewModel.state.value.problem).isNull()
    }

    @Test
    fun `cancelling the question leaves the place alone`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val office = seed("Office")

        viewModel.beginForgetting(office)
        viewModel.dismissPrompt()
        advanceUntilIdle()

        assertThat(viewModel.state.value.prompt).isNull()
        assertThat(places.stored.map { it.label }).containsExactly("Office")
    }

    @Test
    fun `a permission Abu never granted is reported as a refusal, not a failure`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.beginSaving()
        viewModel.onDraftChange("Office")
        // What the screen finds out when Android will not show the dialog again.
        viewModel.reportRefusal(PlaceWatchRefusal.PERMISSION_MISSING)

        val state = viewModel.state.value
        assertThat(state.refusal).isEqualTo(PlaceWatchRefusal.PERMISSION_MISSING)
        assertThat(state.problem).isNull()
        assertThat(state.prompt).isNull()
        assertThat(state.saving).isFalse()
        assertThat(finder.asked).isEqualTo(0)
    }

    @Test
    fun `the screen speaks whichever language Abu has chosen`() = runTest {
        settings.setLanguageMode(LanguageMode.BANGLA)
        val viewModel = viewModel()
        advanceUntilIdle()

        assertThat(viewModel.state.value.language).isEqualTo(SpokenLanguage.BENGALI)

        settings.setLanguageMode(LanguageMode.ENGLISH)
        advanceUntilIdle()

        assertThat(viewModel.state.value.language).isEqualTo(SpokenLanguage.ENGLISH)
    }

    private fun viewModel(): PlacesViewModel = PlacesViewModel(
        places = places,
        savePlaceHere = SavePlaceHereUseCase(finder, SavePlaceUseCase(places, clock)),
        renamePlace = RenamePlaceUseCase(places, clock),
        deletePlace = DeletePlaceUseCase(places),
        settings = settings,
    )

    /** A place already in the directory, written the way the use case would have written it. */
    private suspend fun seed(label: String): SavedPlace {
        val at = clock.now()
        val row = SavedPlace(
            label = label,
            labelKey = SavedPlace.keyOf(label),
            latitude = FakeCurrentPlaceFinder.DHAKA_LATITUDE,
            longitude = FakeCurrentPlaceFinder.DHAKA_LONGITUDE,
            radiusMeters = SavePlaceUseCase.DEFAULT_RADIUS_METERS,
            createdAt = at,
            updatedAt = at,
        )
        return checkNotNull(places.create(row).getOrNull()) { "seeding $label was refused" }
    }
}
