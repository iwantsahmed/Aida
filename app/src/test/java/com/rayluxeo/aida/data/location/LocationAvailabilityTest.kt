package com.rayluxeo.aida.data.location

import android.Manifest
import android.app.Application
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.domain.scheduler.PlaceWatchRefusal
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The shared decision table, which is the whole reason this class exists: the scheduler and the
 * finder must never give Abu two different accounts of the same phone. The asymmetry asserted
 * below — one fix needs foreground location, watching for an arrival needs the background grant
 * too — is the part that would quietly cost him the feature if it were ever collapsed into one
 * check, because he would be asked for always-on location before he could save his first place.
 *
 * No Hilt: the local-test transform does not run under AGP 9 (google/dagger#5261).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocationAvailabilityTest {

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val manager: LocationManager =
        application.getSystemService(LocationManager::class.java)
    private val availability = LocationAvailability(application)

    /** Pinned rather than inherited: a default that changed would silently rewrite every case. */
    @Before
    fun switchLocationOn() {
        shadowOf(manager).setLocationEnabled(true)
    }

    @Test
    fun `the permission comes first, because it is the one thing Abu can grant on the spot`() {
        shadowOf(manager).removeProvider(LocationManager.GPS_PROVIDER)
        shadowOf(manager).removeProvider(LocationManager.NETWORK_PROVIDER)
        shadowOf(manager).setLocationEnabled(false)

        assertThat(availability.refusalFor(manager, LocationAvailability.Need.ONE_FIX))
            .isEqualTo(PlaceWatchRefusal.PERMISSION_MISSING)
        assertThat(availability.refusalFor(manager, LocationAvailability.Need.CONTINUOUS_WATCH))
            .isEqualTo(PlaceWatchRefusal.PERMISSION_MISSING)
    }

    @Test
    fun `a coarse grant is enough to stand somewhere and name it`() {
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertThat(availability.hasForegroundGrant()).isTrue()
        assertThat(availability.hasFineGrant()).isFalse()
        assertThat(availability.refusalFor(manager, LocationAvailability.Need.ONE_FIX)).isNull()
        // A proximity alert will not arm on coarse, so the watch still wants the fine grant.
        assertThat(availability.refusalFor(manager, LocationAvailability.Need.CONTINUOUS_WATCH))
            .isEqualTo(PlaceWatchRefusal.PERMISSION_MISSING)
    }

    @Test
    fun `a fix does not wait on the background grant that watching for an arrival does`() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION)

        assertThat(availability.refusalFor(manager, LocationAvailability.Need.ONE_FIX)).isNull()
        assertThat(availability.refusalFor(manager, LocationAvailability.Need.CONTINUOUS_WATCH))
            .isEqualTo(PlaceWatchRefusal.BACKGROUND_PERMISSION_MISSING)

        grant(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

        assertThat(availability.refusalFor(manager, LocationAvailability.Need.CONTINUOUS_WATCH))
            .isNull()
    }

    @Test
    fun `a phone with only the passive provider cannot answer either question`() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shadowOf(manager).removeProvider(LocationManager.GPS_PROVIDER)
        shadowOf(manager).removeProvider(LocationManager.NETWORK_PROVIDER)

        // Passive is still there and still useless: it echoes what another app has already asked
        // for, so it can neither raise an alert nor answer a fix of Mira's own.
        assertThat(manager.allProviders).contains(LocationManager.PASSIVE_PROVIDER)
        assertThat(availability.usableProviders(manager)).isEmpty()
        assertThat(availability.refusalFor(manager, LocationAvailability.Need.ONE_FIX))
            .isEqualTo(PlaceWatchRefusal.NO_PROVIDER)
        assertThat(availability.refusalFor(manager, LocationAvailability.Need.CONTINUOUS_WATCH))
            .isEqualTo(PlaceWatchRefusal.NO_PROVIDER)
    }

    @Test
    fun `hardware Abu cannot change is reported before the switch he can flip`() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        shadowOf(manager).setLocationEnabled(false)

        assertThat(availability.refusalFor(manager, LocationAvailability.Need.ONE_FIX))
            .isEqualTo(PlaceWatchRefusal.LOCATION_DISABLED)
    }

    @Test
    fun `the usable providers keep Mira's order and leave the passive one out`() {
        assertThat(availability.usableProviders(manager))
            .containsExactly(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .inOrder()
    }

    private fun grant(vararg permissions: String) {
        shadowOf(application).grantPermissions(*permissions)
    }
}
