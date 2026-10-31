package com.rayluxeo.aida.ui.ink

import android.content.Context
import android.os.Build
import android.view.View
import android.view.inputmethod.ConnectionlessHandwritingCallback
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import com.rayluxeo.aida.domain.ink.HandwritingResult
import com.rayluxeo.aida.domain.ink.HandwritingUnavailable

/**
 * Reading Abu's handwriting with what the phone already has.
 *
 * This is the platform's own stylus handwriting, performed **on the device by the keyboard** — on
 * the S24 Ultra, Samsung Keyboard. Free, offline, no dependency and no account, which is the only
 * kind of recognition Mira is allowed (AGENTS.md §2, §3). Gemini is deliberately not used for
 * this: `docs/PRIVACY.md` promises the command engine gets one normalised utterance and nothing
 * else, and handing it a page of Abu's writing would break a documented guarantee.
 *
 * **It reads writing as it happens, not afterwards.** There is no platform call that takes a
 * finished page and returns its text, which is why this is a way of *writing into* a note rather
 * than an action performed on a note that already exists — and why nothing here may ever stand
 * between Abu and saving what he wrote.
 *
 * Its own class, constructed only above API 35, so the platform methods behind it are never
 * reached for on a phone that does not have them.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
internal class InkHandwritingSession(private val context: Context) {

    private val inputMethods: InputMethodManager? =
        context.getSystemService(InputMethodManager::class.java)

    /** Asked of the keyboard that is actually installed, never inferred from the phone's model. */
    fun isAvailable(): Boolean =
        inputMethods?.isConnectionlessStylusHandwritingAvailable() == true

    /**
     * Hands the next stylus strokes to the keyboard and reports back what it read.
     *
     * [onFinished] always fires exactly once, including on every refusal, so the screen can never
     * be left waiting on a session that quietly never started.
     */
    fun start(view: View, onFinished: (HandwritingResult) -> Unit) {
        val manager = inputMethods
        if (manager == null || !manager.isConnectionlessStylusHandwritingAvailable()) {
            onFinished(HandwritingResult.Unavailable(HandwritingUnavailable.NO_RECOGNISER))
            return
        }
        manager.startConnectionlessStylusHandwriting(
            view,
            // No cursor to anchor to: there is no text field here, only a page. That is exactly
            // what the connectionless form of this API is for.
            null,
            context.mainExecutor,
            object : ConnectionlessHandwritingCallback {

                override fun onResult(text: CharSequence) {
                    val read = text.toString().trim()
                    onFinished(
                        if (read.isEmpty()) {
                            HandwritingResult.NoTextRecognised
                        } else {
                            HandwritingResult.Recognised(read)
                        },
                    )
                }

                override fun onError(errorCode: Int) {
                    onFinished(errorCode.asHandwritingResult())
                }
            },
        )
    }
}

/**
 * Nothing readable is **not** an error.
 *
 * The platform reports it down the error channel, but a scribble, a diagram, or a hand the
 * recogniser does not know are all ordinary things to have written. Wording it as a failure
 * would tell Abu something went wrong with his own note when nothing did.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun Int.asHandwritingResult(): HandwritingResult = when (this) {
    ConnectionlessHandwritingCallback.CONNECTIONLESS_HANDWRITING_ERROR_NO_TEXT_RECOGNIZED ->
        HandwritingResult.NoTextRecognised

    ConnectionlessHandwritingCallback.CONNECTIONLESS_HANDWRITING_ERROR_UNSUPPORTED ->
        HandwritingResult.Unavailable(HandwritingUnavailable.REFUSED)

    else -> HandwritingResult.Unavailable(HandwritingUnavailable.FAILED)
}

/**
 * Whether this phone can read handwriting at all, asked at runtime.
 *
 * Below API 35 the platform has no connectionless handwriting to offer, and on a phone whose
 * keyboard does not implement it the answer is no — both are ordinary, and both mean the control
 * is not drawn rather than drawn and broken.
 */
internal fun Context.canReadHandwriting(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
        InkHandwritingSession(this).isAvailable()
