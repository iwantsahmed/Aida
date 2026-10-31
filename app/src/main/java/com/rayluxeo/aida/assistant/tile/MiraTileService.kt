package com.rayluxeo.aida.assistant.tile

import android.Manifest
import android.app.PendingIntent
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.rayluxeo.aida.R
import com.rayluxeo.aida.assistant.MiraShortcuts
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.voice.MiraVoiceState
import com.rayluxeo.aida.voice.VoiceConversationController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Mira in the Quick Settings shade: one pull and one tap from anywhere, including over another app.
 *
 * The tile reports what is actually true rather than sitting at a fixed label. It reads the live
 * turn from [VoiceConversationController], so a session Abu started from the app, the widget or the
 * assist gesture shows here as active, and it goes unavailable when the microphone permission is
 * missing instead of collapsing the shade onto a screen that cannot listen.
 *
 * It stays in Mira's single process. A tile in its own process would get its own Hilt graph and a
 * second DataStore over the same file, which is not multi-process safe — never add
 * `android:process` to this service.
 */
@AndroidEntryPoint
class MiraTileService : TileService() {

    @Inject
    lateinit var controller: VoiceConversationController

    @Inject
    lateinit var dispatchers: DispatcherProvider

    /**
     * Alive only while the shade is open.
     *
     * Quick Settings binds this service whenever the tile becomes visible and unbinds it after, so
     * the collection is tied to that window rather than to the service instance: holding it any
     * longer would keep a subscription on a turn nobody is watching.
     */
    private var visible: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val scope = CoroutineScope(SupervisorJob() + dispatchers.main)
        visible = scope
        scope.launch {
            controller.state.collect { render(it.state) }
        }
    }

    override fun onStopListening() {
        releaseScope()
        super.onStopListening()
    }

    override fun onDestroy() {
        releaseScope()
        super.onDestroy()
    }

    /**
     * Nothing is shown before the device is unlocked.
     *
     * A tap from the locked shade would otherwise open Mira's conversation — tasks, reminders and
     * everything she has been told — to whoever is holding the phone.
     */
    override fun onClick() {
        super.onClick()
        val pendingIntent = MiraShortcuts.talkPendingIntent(this)
        if (isLocked) {
            unlockAndRun { startMira(pendingIntent) }
        } else {
            startMira(pendingIntent)
        }
    }

    private fun render(state: MiraVoiceState) {
        val tile = qsTile ?: return
        if (!hasMicrophonePermission()) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.subtitle = getString(R.string.mira_tile_no_microphone)
        } else {
            tile.state = if (state.isTurnInFlight()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = getString(state.subtitle())
        }
        tile.updateTile()
    }

    /**
     * The `Intent` overload was deprecated in Android 14 and throws there, while the `PendingIntent`
     * one does not exist below it. Mira's minSdk is 31, so both paths are real.
     */
    private fun startMira(pendingIntent: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(MiraShortcuts.talkIntent(this))
        }
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun releaseScope() {
        visible?.cancel()
        visible = null
    }
}

/**
 * A turn Abu could interrupt, which is what the tile's active state means.
 *
 * `SUCCESS` and `ERROR` are the end of a turn rather than part of one — the orb shows them for a
 * moment and settles — so the tile reads them as idle and invites the next question.
 */
private fun MiraVoiceState.isTurnInFlight(): Boolean = when (this) {
    MiraVoiceState.LISTENING,
    MiraVoiceState.PROCESSING,
    MiraVoiceState.EXECUTING,
    MiraVoiceState.SPEAKING,
    -> true

    MiraVoiceState.IDLE,
    MiraVoiceState.SUCCESS,
    MiraVoiceState.ERROR,
    -> false
}

@StringRes
private fun MiraVoiceState.subtitle(): Int = when (this) {
    MiraVoiceState.LISTENING -> R.string.mira_tile_listening
    MiraVoiceState.PROCESSING, MiraVoiceState.EXECUTING -> R.string.mira_tile_working
    MiraVoiceState.SPEAKING -> R.string.mira_tile_speaking
    MiraVoiceState.IDLE, MiraVoiceState.SUCCESS, MiraVoiceState.ERROR -> R.string.mira_tile_idle
}
