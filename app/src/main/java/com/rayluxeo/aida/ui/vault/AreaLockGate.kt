package com.rayluxeo.aida.ui.vault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Holds one sensitive area behind the device's biometric prompt, for Abu's "Require unlock for"
 * settings (Money, Memory, Invoices, Backups). It reuses the whole-app lock's cover and prompt, so
 * it looks and behaves exactly like the app lock — only scoped to the one screen it wraps.
 *
 * The toggles that drive this used to persist and then be read by nothing, so turning "Money" on
 * gated nothing at all. This is what makes them real.
 *
 * The unlock is remembered with a plain [remember], not `rememberSaveable`, so it is tied to this
 * entry's composition: navigating away and opening the area again asks once more, which is what
 * "require unlock" means. It also re-locks when the app is backgrounded (ON_STOP), the same as the
 * vault, so a protected area left open and returned to asks again rather than standing unlocked
 * across a trip out of the app. A success only holds for one uninterrupted visit; a rotation is then
 * the sole place it would ask twice — acceptable on a phone that is almost always portrait, and the
 * safe way to err for a lock. [content] stays composed behind the cover so the unlock returns him to
 * exactly what he opened, with an opaque obsidian cover meaning nothing behind it is seen or touched
 * first.
 */
@Composable
fun AreaLockGate(
    protected: Boolean,
    strings: AppLockStrings,
    content: @Composable () -> Unit,
) {
    var unlocked by remember { mutableStateOf(false) }
    // Re-lock when the app goes to the background, the same as the vault does: a protected area left
    // open and returned to must ask again, or "require unlock" would hold across a trip away from the
    // app. The success only ever lives for one uninterrupted visit.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { unlocked = false }
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (protected && !unlocked) {
            AppLockScreen(strings = strings, onUnlock = { unlocked = true })
        }
    }
}
