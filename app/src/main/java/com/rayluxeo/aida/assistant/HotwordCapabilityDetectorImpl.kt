package com.rayluxeo.aida.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.service.voice.VoiceInteractionService
import com.rayluxeo.aida.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Asks this phone whether a third-party always-on wake word is possible, instead of assuming it.
 *
 * The answer today is no on every shipping build — `AlwaysOnHotwordDetector` is reserved for the
 * assistant that came with the OS, and the keyphrase itself has to be enrolled in the SoundTrigger
 * HAL by a privileged enrollment app. Hard-coding that conclusion would mean Mira never notices a
 * build that changes it, so the conclusion is re-derived from four things the platform will
 * actually answer.
 *
 * **Nothing here opens a microphone.** No detector is ever constructed, which is a stronger
 * guarantee than constructing one and remembering to release it: there is no object to leak and
 * no recording session to start. That is the hard line in `docs/PRIVACY.md` — Mira listens when
 * Abu asks her to and at no other time.
 */
@Singleton
class HotwordCapabilityDetectorImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val role: AssistantRoleController,
    private val dispatchers: DispatcherProvider,
) : HotwordCapabilityDetector {

    override suspend fun detect(): HotwordSupport = withContext(dispatchers.io) {
        val isAssistant = role.status().isMira
        HotwordSupport(
            verdict = hotwordVerdictOf(
                isAssistant = isAssistant,
                detectorApiPresent = detectorApiPresent(),
                keyphraseEnrollmentPresent = keyphraseEnrollmentPresent(),
                privilegedHotwordAccess = privilegedHotwordAccess(),
            ),
            alternatives = hotwordAlternatives(isAssistant),
        )
    }

    /**
     * Is the public entry point to always-on detection still on this platform at all?
     *
     * Reflection rather than a direct reference, because the point is to find out whether the
     * method is reachable from an ordinary app on *this* build. A direct call would compile
     * against the SDK's opinion and tell us nothing; a hidden-API refusal arrives here as a
     * `NoSuchMethodException`, which is the same answer as the method being gone.
     */
    private fun detectorApiPresent(): Boolean = probe {
        val callback = Class.forName("android.service.voice.AlwaysOnHotwordDetector\$Callback")
        VoiceInteractionService::class.java.getMethod(
            "createAlwaysOnHotwordDetector",
            String::class.java,
            Locale::class.java,
            callback,
        )
        true
    }

    /**
     * Does anything on this device enrol keyphrases?
     *
     * The platform finds keyphrase hardware through an enrollment application: an activity
     * answering [ACTION_MANAGE_VOICE_KEYPHRASES] in a package that holds
     * [MANAGE_VOICE_KEYPHRASES]. A phone with no such app has no SoundTrigger keyphrase support to
     * offer anyone, which is a different fact from the platform reserving it — and the two lead to
     * different sentences on the settings screen.
     *
     * The permission check is what stops an ordinary app that merely declares the action from
     * being mistaken for the hardware.
     */
    private fun keyphraseEnrollmentPresent(): Boolean = probe {
        resolveEnrollmentActivities().any { resolved ->
            val enrollmentPackage = resolved.activityInfo?.packageName
            enrollmentPackage != null && holdsPermission(MANAGE_VOICE_KEYPHRASES, enrollmentPackage)
        }
    }

    /**
     * Would this build let Mira drive hotwording herself?
     *
     * Both permissions are `signature|privileged`, so on every shipping build the answer is no and
     * "Hey Mira" cannot be enrolled or run. They are asked for rather than assumed so that a build
     * which ever granted one is noticed instead of being quietly treated as a refusal.
     */
    private fun privilegedHotwordAccess(): Boolean = probe {
        holdsPermission(MANAGE_HOTWORD_DETECTION, context.packageName) ||
            holdsPermission(MANAGE_VOICE_KEYPHRASES, context.packageName)
    }

    private fun holdsPermission(permission: String, packageName: String): Boolean =
        context.packageManager.checkPermission(permission, packageName) ==
            PackageManager.PERMISSION_GRANTED

    private fun resolveEnrollmentActivities(): List<ResolveInfo> {
        val intent = Intent(ACTION_MANAGE_VOICE_KEYPHRASES)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
    }

    /**
     * Every probe fails closed.
     *
     * `Throwable` rather than `Exception` on purpose: a blocked hidden API can surface as
     * `NoSuchMethodError` or another `LinkageError`, and a probe that crashed the settings screen
     * while asking a question Mira does not need an answer to would be the worst of both. None of
     * these calls suspend, so nothing here can swallow a cancellation.
     */
    private fun probe(block: () -> Boolean): Boolean = try {
        block()
    } catch (failure: Throwable) {
        false
    }

    private companion object {
        /** AOSP's enrollment-application action, as `KeyphraseEnrollmentInfo` queries for it. */
        const val ACTION_MANAGE_VOICE_KEYPHRASES = "com.android.intent.action.MANAGE_VOICE_KEYPHRASES"
        const val MANAGE_VOICE_KEYPHRASES = "android.permission.MANAGE_VOICE_KEYPHRASES"
        const val MANAGE_HOTWORD_DETECTION = "android.permission.MANAGE_HOTWORD_DETECTION"
    }
}

/**
 * The verdict, as pure logic, so the ordering can be tested without a phone.
 *
 * The order is the order Abu needs to hear it in. Holding the assistant role is a precondition
 * either way, so it comes first. After that the device's own limits are reported before the
 * platform's policy: "this phone has no keyphrase hardware" is a fact about the hardware that no
 * Android release will change, while "Android reserves this" is a rule that a future build could.
 * Reporting the policy first would hide the more permanent of the two.
 */
internal fun hotwordVerdictOf(
    isAssistant: Boolean,
    detectorApiPresent: Boolean,
    keyphraseEnrollmentPresent: Boolean,
    privilegedHotwordAccess: Boolean,
): HotwordVerdict = when {
    !isAssistant -> HotwordVerdict.NOT_THE_ASSISTANT
    !detectorApiPresent -> HotwordVerdict.SYSTEM_ONLY
    !keyphraseEnrollmentPresent -> HotwordVerdict.NO_HARDWARE
    !privilegedHotwordAccess -> HotwordVerdict.SYSTEM_ONLY
    else -> HotwordVerdict.AVAILABLE
}

/**
 * The ways in that genuinely work, which is why this list is never empty.
 *
 * The assist gesture is the one entry point that depends on the role: without it the gesture
 * reaches whichever app does hold it, so listing it would be a promise Mira cannot keep. The other
 * five are shipped in the manifest and work whoever the assistant is.
 */
internal fun hotwordAlternatives(isAssistant: Boolean): List<AssistantEntryPoint> = buildList {
    if (isAssistant) add(AssistantEntryPoint.SYSTEM_ASSIST_GESTURE)
    add(AssistantEntryPoint.VOICE_BUTTON)
    add(AssistantEntryPoint.QUICK_SETTINGS_TILE)
    add(AssistantEntryPoint.HOME_SCREEN_WIDGET)
    add(AssistantEntryPoint.APP_SHORTCUT)
    add(AssistantEntryPoint.SHARE_SHEET)
}
