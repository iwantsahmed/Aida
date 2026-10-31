package com.rayluxeo.aida.ui.vault

import android.content.Context
import android.content.ContextWrapper
import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rayluxeo.aida.core.design.MiraTheme
import com.rayluxeo.aida.core.design.component.MiraAvatar
import com.rayluxeo.aida.core.design.component.MiraAvatarState
import com.rayluxeo.aida.core.design.component.MiraButton
import com.rayluxeo.aida.core.design.component.MiraButtonStyle

/**
 * Every word the lock screen puts in front of Abu, in one language. Worded by the shell from
 * [com.rayluxeo.aida.ui.navigation.ShellWording] so the gate itself stays language-free, the same
 * way the rest of the design system takes data rather than a [com.rayluxeo.aida.core.common.SpokenLanguage].
 */
data class AppLockStrings(
    val title: String,
    val subtitle: String,
    val unlock: String,
    val promptTitle: String,
    val promptSubtitle: String,
    val lockedOut: String,
    val cannotVerify: String,
    val notEnrolled: String,
    val enroll: String,
)

/**
 * Holds the whole app behind the device's biometric prompt when app-lock is on.
 *
 * The gate wraps the shell rather than sitting inside it: [content] stays composed behind the lock
 * so an unlock returns Abu to exactly the screen and the half-filled form he left, but an opaque
 * obsidian cover over the top means nothing behind it is seen or touched while locked.
 *
 * The locked flag lives in [AppLockController] (process-scoped), not here, because it has to
 * outlive a rotation and reset on a real relaunch — see that class. This composable only decides
 * when to raise the prompt and when to arm the lock:
 *
 *  - Every move to the background ([Lifecycle.Event.ON_STOP], not `ON_PAUSE`, so the system prompt
 *    coming up over us does not itself count as leaving) is reported through [onEnterBackground],
 *    and every return through [onEnterForeground]. The decision of whether that arms the lock —
 *    right away for "lock when I leave", or once the Auto-lock delay has passed on the return — is
 *    the controller's, so the delay is honoured rather than ignored (it was once read and never
 *    applied).
 *  - When the feature is switched off the lock is cleared, so turning it on later cannot
 *    retroactively gate the session Abu is already inside.
 *  - When the cover appears the prompt is raised once; if Abu dismisses it the button is the retry.
 */
@Composable
fun AppLockGate(
    enabled: Boolean,
    locked: Boolean,
    strings: AppLockStrings,
    onEnterBackground: () -> Unit,
    onEnterForeground: () -> Unit,
    onUnlock: () -> Unit,
    content: @Composable () -> Unit,
) {
    val gated = enabled && locked

    // Switching the feature off clears the default-shut lock; switching it on does nothing to the
    // current foreground session. Keyed on [enabled] so it settles again whenever the setting flips.
    LaunchedEffect(enabled) {
        if (!enabled) onUnlock()
    }

    // The lifecycle is reported to the controller, which owns the timing. Read the callbacks through
    // rememberUpdatedState so the long-lived observer always calls their latest versions.
    val background = rememberUpdatedState(onEnterBackground)
    val foreground = rememberUpdatedState(onEnterForeground)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> background.value()
                Lifecycle.Event.ON_START -> foreground.value()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (gated) {
            AppLockScreen(strings = strings, onUnlock = onUnlock)
        }
    }
}

/**
 * The cover. Opaque obsidian so the screen behind cannot be read, and a no-op pointer input across
 * the whole surface so nothing behind it can be touched either. Shared with [AreaLockGate], which
 * puts the same cover over a single sensitive area rather than the whole app.
 */
@Composable
internal fun AppLockScreen(
    strings: AppLockStrings,
    onUnlock: () -> Unit,
) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var errored by remember { mutableStateOf(false) }
    var offerEnrol by remember { mutableStateOf(false) }

    val requestUnlock = rememberVaultUnlock(
        promptTitle = strings.promptTitle,
        promptSubtitle = strings.promptSubtitle,
    ) { outcome ->
        when (outcome) {
            VaultUnlockOutcome.Succeeded -> {
                message = null
                errored = false
                offerEnrol = false
                onUnlock()
            }
            // The system prompt stays up after a rejected finger, so there is nothing to do here.
            VaultUnlockOutcome.NotRecognised -> Unit
            // Abu dismissed it himself; the button is the way back in.
            VaultUnlockOutcome.Cancelled -> Unit
            VaultUnlockOutcome.LockedOut -> {
                message = strings.lockedOut
                errored = true
            }
            VaultUnlockOutcome.NoneEnrolled -> {
                message = strings.notEnrolled
                errored = true
                offerEnrol = true
            }
            is VaultUnlockOutcome.Refused -> {
                message = strings.cannotVerify
                errored = true
            }
            is VaultUnlockOutcome.SystemRefused -> {
                message = outcome.message
                errored = true
            }
        }
    }

    // Raise the prompt the moment the cover appears, so unlocking is one gesture rather than two.
    LaunchedEffect(Unit) { requestUnlock() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            // Swallow every touch, so the screen behind the cover cannot be driven blind.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent()
                    }
                }
            }
            .padding(MiraTheme.spacing.screenHorizontal),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MiraTheme.spacing.md),
        ) {
            MiraAvatar(
                state = if (errored) MiraAvatarState.ERROR else MiraAvatarState.IDLE,
                amplitude = 0f,
                size = AVATAR_SIZE,
            )

            Text(
                text = strings.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Text(
                text = message ?: strings.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (errored) MiraTheme.colors.danger else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(MiraTheme.spacing.xs))

            MiraButton(
                text = strings.unlock,
                onClick = { requestUnlock() },
                modifier = Modifier.fillMaxWidth(),
            )

            if (offerEnrol) {
                MiraButton(
                    text = strings.enroll,
                    onClick = {
                        context.findActivity()?.startActivity(biometricEnrollIntent())
                    },
                    style = MiraButtonStyle.SECONDARY,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val AVATAR_SIZE = 128.dp

/** The hosting Activity, unwrapped from Compose's themed context chain. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
