package com.rayluxeo.aida.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService

/**
 * The component Android binds when Mira holds the assistant role, and keeps bound for as long as
 * the phone is on.
 *
 * It does almost nothing, and that near-emptiness is the feature. Everything this class does runs
 * for the life of the device, so it must not start listening, must not hold the microphone, must
 * not open the database and must not do work of any kind here. Mira's entry points are events —
 * the assist gesture, her own button, the tile, the widget, the shortcut, the share sheet — and
 * each of them brings up a session that lives only as long as the turn.
 *
 * The session is created by [MiraVoiceInteractionSessionService], named from
 * `res/xml/voice_interaction_service.xml` by `android:sessionService`. That XML also has to name
 * a `RecognitionService` in this package through `android:recognitionService`: it is a **required**
 * attribute, and without it Android never offers Mira in the assistant list — silently, with
 * nothing logged.
 *
 * An always-on "Hey Mira" is deliberately absent rather than forgotten, and the reason is checkable
 * rather than remembered: `android.service.voice.AlwaysOnHotwordDetector` is **not present in the
 * public SDK** — `javap` over `android-37.0/android.jar` does not find the class, and this class's
 * own public surface carries no `createAlwaysOnHotwordDetector` or `createHotwordDetector`. A
 * third-party app therefore cannot compile one against the SDK, let alone run one, and pretending
 * otherwise would mean holding a microphone open in this very class.
 */
class MiraVoiceInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        active = this
    }

    override fun onShutdown() {
        active = null
        super.onShutdown()
    }

    companion object {

        /**
         * The bound instance, or null when Mira is not the assistant.
         *
         * Held so that Mira's own Settings screen can open a real session to show Abu, which is
         * the only honest way to test the assistant path: there is no public API for an app to ask
         * the system to invoke *itself* as the assistant, and firing [android.content.Intent
         * .ACTION_ASSIST] proves nothing because that action is resolved against whichever app
         * holds the role rather than against Mira.
         *
         * Safe as a static only because every assistant component stays in the main process, which
         * is the same reason none of them may ever carry `android:process`.
         */
        private var active: MiraVoiceInteractionService? = null

        /**
         * Opens a Mira session the way the assist gesture does.
         *
         * @return false when Mira is not the bound assistant, which is a real answer and not a
         * failure — the caller says so rather than reporting that something went wrong.
         */
        fun showSessionNow(): Boolean {
            val service = active ?: return false
            return runCatching { service.showSession(Bundle(), 0) }.isSuccess
        }
    }
}
