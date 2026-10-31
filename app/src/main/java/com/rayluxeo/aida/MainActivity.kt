package com.rayluxeo.aida

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.design.MiraPalette
import com.rayluxeo.aida.assistant.MiraShortcuts
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.assistant.widget.WidgetRefreshScheduler
import com.rayluxeo.aida.domain.automation.AutomationScheduler
import com.rayluxeo.aida.ui.navigation.MiraApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var automationScheduler: AutomationScheduler

    @Inject lateinit var dispatchers: DispatcherProvider

    @Inject lateinit var widgetRefresh: WidgetRefreshScheduler

    /**
     * Which entry point opened Mira, held until the shell has somewhere to put it.
     *
     * The tile, the widget and the launcher shortcuts all arrive as an action on the intent rather
     * than as a route, because they are built before the navigation graph exists. Without this the
     * controls open Mira and then do nothing else, which is the defect AGENTS.md section 12 calls a
     * click target with no handler.
     */
    private var entryAction by mutableStateOf<String?>(null)

    /** The record a deep-link names, so a reminder opens the task it is about, not a blank form. */
    private var entryTargetId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // Mira is obsidian in every state, so both bars are forced dark rather than
        // following the system theme.
        val obsidian = MiraPalette.Obsidian.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(obsidian),
            navigationBarStyle = SystemBarStyle.dark(obsidian),
        )
        super.onCreate(savedInstanceState)
        // While Mira is open, the volume rocker moves the media stream — the one her voice plays on
        // — rather than the ring stream Android picks by default when nothing is playing at the
        // instant a key is pressed. Without this Abu pressed the rocker expecting Mira to get louder
        // and changed the ringer instead, so her voice seemed deaf to the device volume. (17 Oct 2026.)
        volumeControlStream = AudioManager.STREAM_MUSIC
        entryAction = intent?.action
        entryTargetId = intent?.targetId()

        // Published from a foreground entry point, where the platform does not rate-limit it, and
        // republished on every launch so the labels follow the device's language without anything
        // having to watch for a locale change.
        MiraShortcuts.refresh(this)

        // APP_START automations fire once per cold start, from here rather than Application.onCreate:
        // the process also starts for a broadcast or a worker, and only opening the UI is Abu opening
        // the app. The flag is process-static so a config-change recreation does not re-fire them.
        if (!appStartAutomationsFired) {
            appStartAutomationsFired = true
            CoroutineScope(SupervisorJob() + dispatchers.io).launch {
                automationScheduler.triggerAppStartAutomations()
            }
        }

        setContent {
            MiraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    MiraApp(
                        entryAction = entryAction,
                        entryTargetId = entryTargetId,
                        onEntryHandled = {
                            entryAction = null
                            entryTargetId = null
                        },
                    )
                }
            }
        }
    }

    /**
     * MainActivity is singleTask, so a second tap on the tile or a shortcut is delivered here
     * rather than creating another instance. Without this the first entry point to open Mira would
     * be the only one that ever worked.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entryAction = intent.action
        entryTargetId = intent.targetId()
    }

    /**
     * When Abu leaves Mira, refresh the home-screen glance so a task he just completed or a payment
     * he just recorded is reflected the moment he lands back on the launcher, rather than at the next
     * half-hour tick. A no-op when no glance is placed (the worker returns early).
     */
    override fun onStop() {
        super.onStop()
        widgetRefresh.refreshNow()
    }

    /** The deep-link record id, or null when the entry point names no particular record. */
    private fun Intent.targetId(): Long? =
        getLongExtra(MiraShortcuts.EXTRA_TARGET_ID, -1L).takeIf { it != -1L }

    private companion object {
        /** True once APP_START automations have fired this process, so a recreation does not repeat them. */
        @Volatile
        private var appStartAutomationsFired = false
    }
}
