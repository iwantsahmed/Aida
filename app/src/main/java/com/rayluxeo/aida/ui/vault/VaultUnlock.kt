package com.rayluxeo.aida.ui.vault

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Strong biometrics, with the device PIN as the fallback. Class 3 is required because a vault
 * that opens to a photograph is not a vault; allowing the credential is what keeps Abu from
 * being locked out of his own data by a wet thumb.
 */
private const val VAULT_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * The single answer one unlock attempt produced.
 *
 * [NotRecognised] is deliberately separate from the terminal outcomes: the system prompt stays
 * up after a rejected finger, so the vault must stay in its unlocking state rather than fall
 * back to locked behind a prompt Abu can still use.
 *
 * Nothing here carries a sentence of Mira's own. [Refused] names a reason and the screen's
 * phrasebook words it, which is what lets the vault explain itself in Bengali.
 */
sealed interface VaultUnlockOutcome {
    data object Succeeded : VaultUnlockOutcome
    data object NotRecognised : VaultUnlockOutcome
    data object Cancelled : VaultUnlockOutcome
    data object LockedOut : VaultUnlockOutcome
    data object NoneEnrolled : VaultUnlockOutcome

    /** Mira's own reason, worded per language. */
    data class Refused(val reason: VaultLockReason) : VaultUnlockOutcome

    /** [message] is the system's own wording, so it arrives in the device's language. */
    data class SystemRefused(val message: String) : VaultUnlockOutcome
}

/**
 * Hands the screen one function that raises the platform prompt, and makes sure the prompt is
 * taken down again if Abu navigates away while it is up.
 *
 * The prompt's own title and subtitle are drawn by the system and so have to be handed in
 * already worded; they are read at call time rather than captured once, so switching language
 * while the vault is on screen changes the next prompt as well as the screen behind it.
 */
@Composable
fun rememberVaultUnlock(
    promptTitle: String,
    promptSubtitle: String,
    onOutcome: (VaultUnlockOutcome) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val session = remember { VaultUnlockSession() }
    DisposableEffect(session) {
        onDispose { session.cancel() }
    }
    return { session.start(context, promptTitle, promptSubtitle, onOutcome) }
}

/** Intent for the system's biometric enrolment flow, pre-set to the vault's own requirements. */
fun biometricEnrollIntent(): Intent =
    Intent(Settings.ACTION_BIOMETRIC_ENROLL)
        .putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED, VAULT_AUTHENTICATORS)

/**
 * Owns at most one live prompt. A [CancellationSignal] is single-use, so every attempt gets a
 * fresh one and the previous one is cancelled first — two prompts racing for the same screen
 * is how a vault ends up unlocking after Abu said no.
 */
private class VaultUnlockSession {

    private var signal: CancellationSignal? = null

    fun start(
        context: Context,
        promptTitle: String,
        promptSubtitle: String,
        onOutcome: (VaultUnlockOutcome) -> Unit,
    ) {
        cancel()
        val activity = context.findActivity()
        if (activity == null) {
            onOutcome(VaultUnlockOutcome.Refused(VaultLockReason.NO_WINDOW))
            return
        }

        val manager = activity.getSystemService(BiometricManager::class.java)
        when (manager?.canAuthenticate(VAULT_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS -> Unit

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                onOutcome(VaultUnlockOutcome.NoneEnrolled)
                return
            }

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                onOutcome(VaultUnlockOutcome.Refused(VaultLockReason.SENSOR_BUSY))
                return
            }

            else -> {
                onOutcome(VaultUnlockOutcome.Refused(VaultLockReason.CANNOT_VERIFY))
                return
            }
        }

        val fresh = CancellationSignal()
        signal = fresh
        BiometricPrompt.Builder(activity)
            .setTitle(promptTitle)
            .setSubtitle(promptSubtitle)
            .setAllowedAuthenticators(VAULT_AUTHENTICATORS)
            .build()
            .authenticate(
                fresh,
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onOutcome(VaultUnlockOutcome.Succeeded)
                    }

                    override fun onAuthenticationFailed() {
                        onOutcome(VaultUnlockOutcome.NotRecognised)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onOutcome(outcomeOf(errorCode, errString))
                    }
                },
            )
    }

    fun cancel() {
        signal?.cancel()
        signal = null
    }

    private fun outcomeOf(errorCode: Int, errString: CharSequence): VaultUnlockOutcome = when (errorCode) {
        BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED,
        BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
        -> VaultUnlockOutcome.Cancelled

        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
        BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT,
        -> VaultUnlockOutcome.LockedOut

        BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
        BiometricPrompt.BIOMETRIC_ERROR_NO_DEVICE_CREDENTIAL,
        -> VaultUnlockOutcome.NoneEnrolled

        else -> errString.toString()
            .takeIf { it.isNotBlank() }
            ?.let { VaultUnlockOutcome.SystemRefused(it) }
            ?: VaultUnlockOutcome.Refused(VaultLockReason.CANNOT_VERIFY)
    }
}

/**
 * Compose only offers a [Context], and on a themed or configuration-wrapped host that Context is
 * a chain of wrappers rather than the Activity itself, so it has to be unwrapped rather than cast.
 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
