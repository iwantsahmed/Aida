package com.rayluxeo.aida.ui.response

import com.rayluxeo.aida.core.common.DispatcherProvider
import com.rayluxeo.aida.domain.model.ConversationStyle
import com.rayluxeo.aida.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the current [ConversationStyle] readable synchronously.
 *
 * Abu's setting lives in DataStore, which only yields a `suspend`/`Flow` read, but the renderer
 * words a reply on the calling thread. So the style is collected once into a volatile cache: the
 * renderer reads the cache, and a change to the setting reaches it on the next emission. The seed
 * is [ConversationStyle.BALANCED] — the same default [MiraSettings] carries — so the very first
 * reply, before the flow has emitted, is already correct rather than guessed.
 *
 * The scope mirrors the one [com.rayluxeo.aida.voice.MiraTextToSpeechImpl] holds: a singleton that
 * must observe a flow for the life of the process owns its own scope, since there is no narrower
 * lifecycle to bind to.
 */
@Singleton
internal class SettingsConversationStyleProvider @Inject constructor(
    settings: SettingsRepository,
    dispatchers: DispatcherProvider,
) : ConversationStyleProvider {

    @Volatile
    private var cached: ConversationStyle = ConversationStyle.BALANCED

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

    init {
        scope.launch {
            settings.settings
                .map { it.conversationStyle }
                .distinctUntilChanged()
                .collect { cached = it }
        }
    }

    override fun current(): ConversationStyle = cached
}

/** Binds the renderer's [ConversationStyleProvider] port to the settings-backed cache. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConversationStyleModule {

    @Binds
    @Singleton
    abstract fun bindConversationStyleProvider(
        impl: SettingsConversationStyleProvider,
    ): ConversationStyleProvider
}
