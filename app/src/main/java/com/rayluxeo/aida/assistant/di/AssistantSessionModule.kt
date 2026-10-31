package com.rayluxeo.aida.assistant.di

import android.content.Context
import com.rayluxeo.aida.assistant.AssistContextReader
import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.domain.repository.ConversationRepository
import com.rayluxeo.aida.domain.repository.SettingsRepository
import com.rayluxeo.aida.ui.response.ResponseRenderer
import com.rayluxeo.aida.voice.VoiceConversationController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * How the voice interaction session reaches the rest of Mira.
 *
 * `VoiceInteractionSession` is not an Android component — the framework constructs it directly —
 * so it cannot be `@AndroidEntryPoint` and nothing can be field-injected into it. This is the
 * supported way in: an entry point resolved from the application's own `SingletonComponent`, so
 * the session shares the one controller, the one database and the one settings store the app
 * already has rather than starting a second copy of any of them.
 *
 * Every dependency listed here is bound elsewhere. Nothing is created for the session's benefit,
 * which is what keeps the assistant overlay and the main screen answering the same way.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AssistantSessionEntryPoint {

    /** The spoken turn itself. The session drives it and never reimplements it. */
    fun voiceController(): VoiceConversationController

    /** Read-only: the overlay shows what Mira heard and what she answered, nothing else. */
    fun conversation(): ConversationRepository

    fun settings(): SettingsRepository

    fun responseRenderer(): ResponseRenderer

    /** Turns the platform's assist payload into the one shape Mira reasons about. */
    fun assistContext(): AssistContextReader

    fun clock(): MiraClock

    fun dispatchers(): DispatcherProvider
}

/**
 * Resolved from the application rather than from the service, because the session outlives no
 * component of its own and the singletons it needs all live in the application's graph.
 */
internal fun Context.assistantSessionEntryPoint(): AssistantSessionEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, AssistantSessionEntryPoint::class.java)
