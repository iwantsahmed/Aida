package com.rayluxeo.aida.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Hands Android a fresh [MiraVoiceInteractionSession] each time the assistant is invoked.
 *
 * A session is one turn's worth of state — a window, a lifecycle, whatever the platform shared
 * about the screen — and none of it should survive into the next invocation, which is why a new
 * one is built rather than a single instance kept.
 *
 * **No `android:process`.** AOSP's sample runs this service in its own process and Mira must not
 * follow it: a second process gets a second Hilt `SingletonComponent` and a second DataStore over
 * the same file, which is not multi-process safe and has already cost this project a
 * "multiple DataStores active for the same file" failure. One process, one store, one database.
 * If that attribute ever appears in the manifest for this service, it is a mistake.
 */
class MiraVoiceInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        MiraVoiceInteractionSession(this)
}
