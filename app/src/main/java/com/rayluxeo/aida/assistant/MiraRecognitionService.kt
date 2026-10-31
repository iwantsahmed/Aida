package com.rayluxeo.aida.assistant

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.SpeechRecognizer
import androidx.annotation.RequiresApi

/**
 * The recognizer Android demands before it will offer Mira as the assistant.
 *
 * `android:recognitionService` is a required attribute of the voice-interaction-service XML and it
 * must name a `RecognitionService` in this package, so this class is not optional — and a
 * do-nothing stub behind a required attribute is exactly the fake control the quality bar forbids.
 * It is therefore a real recognizer: every callback is delegated to the platform's own on-device
 * engine, which also keeps Mira's offline guarantee intact on this path.
 *
 * The delegate is always [SpeechRecognizer.createOnDeviceSpeechRecognizer], never
 * [SpeechRecognizer.createSpeechRecognizer]. The plain factory resolves to whichever recognition
 * service the device treats as default — which, once Abu has made Mira the assistant, can be this
 * one — and a recognition service that binds itself recurses until it dies.
 *
 * Everything here runs on the main thread. `RecognitionService` dispatches its callbacks there and
 * `SpeechRecognizer` requires it, so the delegate is created, driven and released on that thread
 * and nowhere else. Nothing is injected and nothing is stored: this class sees Abu's words only as
 * they pass through to the client that asked for them.
 */
class MiraRecognitionService : RecognitionService() {

    /** At most one live delegate, because a second would be a second claim on the microphone. */
    private var delegate: SpeechRecognizer? = null

    override fun onStartListening(recognizerIntent: Intent, listener: Callback) {
        // A client that starts again without cancelling would otherwise leave the previous
        // delegate holding the microphone.
        releaseDelegate()

        val recognizer = createOnDeviceDelegate()
        if (recognizer == null) {
            listener.deliver { this.error(SpeechRecognizer.ERROR_CLIENT) }
            return
        }
        delegate = recognizer
        recognizer.setRecognitionListener(DelegateListener(listener))
        recognizer.startListening(recognizerIntent)
    }

    /**
     * Stops the audio but keeps the delegate. Destroying it here would throw away the result the
     * caller is still waiting for; the session is released when the results or the error arrive.
     */
    override fun onStopListening(listener: Callback) {
        delegate?.stopListening()
    }

    override fun onCancel(listener: Callback) {
        delegate?.cancel()
        // Nothing further is coming after a cancel, so this is where the session ends.
        releaseDelegate()
    }

    /**
     * Answered by asking the engine Mira delegates to, rather than inheriting the platform default
     * of "cannot check" — Mira can check, by asking the recognizer she would have used.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCheckRecognitionSupport(
        recognizerIntent: Intent,
        supportCallback: SupportCallback,
    ) {
        val prober = createOnDeviceDelegate()
        if (prober == null) {
            supportCallback.onError(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
            return
        }
        // Its own recognizer rather than the listening one, because a support check may arrive
        // while a session is running and must not disturb it.
        prober.checkRecognitionSupport(
            recognizerIntent,
            mainExecutor,
            object : RecognitionSupportCallback {
                override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                    prober.destroy()
                    supportCallback.onSupportResult(recognitionSupport)
                }

                override fun onError(error: Int) {
                    prober.destroy()
                    supportCallback.onError(error)
                }
            },
        )
    }

    override fun onDestroy() {
        releaseDelegate()
        super.onDestroy()
    }

    private fun createOnDeviceDelegate(): SpeechRecognizer? {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) return null
        return try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } catch (unsupported: UnsupportedOperationException) {
            // The service can be disabled between the availability check and this call.
            null
        }
    }

    private fun releaseDelegate() {
        delegate?.destroy()
        delegate = null
    }

    /**
     * A client that has gone away throws [RemoteException] out of every callback. That is the end
     * of a session, not a fault in Mira, and it must not take the process with it.
     */
    private fun Callback.deliver(block: Callback.() -> Unit) {
        try {
            block()
        } catch (gone: RemoteException) {
            releaseDelegate()
        }
    }

    /**
     * Passes the platform engine's callbacks straight through, adding nothing and keeping
     * nothing. `this.error` is qualified because `kotlin.error` would otherwise be a candidate
     * and would throw where Mira means to report.
     */
    private inner class DelegateListener(private val client: Callback) : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            client.deliver { readyForSpeech(params) }
        }

        override fun onBeginningOfSpeech() {
            client.deliver { beginningOfSpeech() }
        }

        override fun onRmsChanged(rmsdB: Float) {
            client.deliver { rmsChanged(rmsdB) }
        }

        override fun onBufferReceived(buffer: ByteArray?) {
            client.deliver { bufferReceived(buffer) }
        }

        override fun onEndOfSpeech() {
            client.deliver { endOfSpeech() }
        }

        override fun onLanguageDetection(detection: Bundle) {
            client.deliver { languageDetection(detection) }
        }

        override fun onPartialResults(partial: Bundle?) {
            client.deliver { partialResults(partial ?: Bundle.EMPTY) }
        }

        override fun onSegmentResults(segment: Bundle) {
            client.deliver { segmentResults(segment) }
        }

        override fun onEndOfSegmentedSession() {
            client.deliver { endOfSegmentedSession() }
            releaseDelegate()
        }

        override fun onResults(recognized: Bundle?) {
            client.deliver { results(recognized ?: Bundle.EMPTY) }
            releaseDelegate()
        }

        override fun onError(errorCode: Int) {
            client.deliver { this.error(errorCode) }
            releaseDelegate()
        }

        // RecognitionService.Callback has no channel for an engine-private event, so there is
        // nowhere to pass one on to.
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
