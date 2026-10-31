package com.rayluxeo.aida.ui.vault

import com.rayluxeo.aida.core.common.MiraClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the app-lock is currently holding Abu out, for the life of one process.
 *
 * App-lock is a security gate, so it defaults to **shut**: a fresh process keeps [locked] true
 * until either Abu passes the biometric prompt or the gate learns the feature is switched off and
 * calls [unlock]. Defaulting the other way would show Home for a frame on every cold start before
 * the lock could close, and a lock you can see behind is not a lock.
 *
 * This is a [Singleton] because its lifetime is exactly a process. It must survive the Activity
 * being recreated on a rotation — so turning the phone does not throw away an unlock Abu just
 * made — and it must reset when the process is genuinely restarted, so a killed-and-relaunched app
 * asks again. A process-scoped holder is the one thing with that lifetime: persisting it would
 * re-lock after a crash recovery in the wrong way, and tying it to the composition would drop the
 * unlock on every configuration change.
 */
@Singleton
class AppLockController @Inject constructor(
    private val clock: MiraClock,
) {

    private val _locked = MutableStateFlow(true)

    /** True while the lock screen should cover everything. Only meaningful when the feature is on. */
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    /** When Abu last left the foreground, or null while he is here. Drives the auto-lock delay. */
    private var leftForegroundAtMillis: Long? = null

    /** Leaving the foreground arms the lock, so the next return has to pass the prompt again. */
    fun lock() {
        _locked.value = true
    }

    /**
     * Clears the lock. Called when the biometric prompt succeeds, and also when the gate finds the
     * feature switched off — that second call is what stops the default-shut lock from gating a
     * session Abu is already inside the moment he turns the feature on.
     */
    fun unlock() {
        _locked.value = false
        leftForegroundAtMillis = null
    }

    /**
     * The app went to the background. The moment is remembered so a later return can tell whether
     * the Auto-lock delay has elapsed; [lockImmediately] — Abu's "Lock when I leave" — shuts it now
     * on top of that.
     */
    fun onLeftForeground(lockImmediately: Boolean) {
        leftForegroundAtMillis = clock.now().toEpochMilli()
        if (lockImmediately) lock()
    }

    /**
     * The app returned to the foreground. It re-locks when it was away at least [timeoutSeconds] —
     * which is what makes the Auto-lock delay real rather than a label on a dead control. A delay of
     * 0 ("Immediately") re-locks on every return; a return with no recorded departure, such as a
     * cold start, leaves the default-shut lock exactly as it is.
     */
    fun onReturnedToForeground(timeoutSeconds: Int) {
        val leftAt = leftForegroundAtMillis ?: return
        leftForegroundAtMillis = null
        val awayMillis = clock.now().toEpochMilli() - leftAt
        if (awayMillis >= timeoutSeconds * 1000L) lock()
    }
}
