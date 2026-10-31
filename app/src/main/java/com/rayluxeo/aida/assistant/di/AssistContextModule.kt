package com.rayluxeo.aida.assistant.di

import com.rayluxeo.aida.assistant.AssistContextReader
import com.rayluxeo.aida.assistant.AssistContextReaderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * What Mira is allowed to know about the screen Abu was looking at.
 *
 * The alias is unscoped: the reader holds nothing between calls, and a new one per injection costs
 * an object. It lives in [SingletonComponent] rather than a session-shaped component because the
 * only thing that resolves it is a `VoiceInteractionSession`, which the framework constructs
 * itself and which therefore reaches Hilt through `EntryPointAccessors.fromApplication` — an
 * entry point can only see bindings installed here.
 *
 * `OutboundConfirmation` is deliberately absent: it is a concrete class with an `@Inject`
 * constructor and no interface, so Hilt already knows how to build it and a `@Binds` for it would
 * be a line that says nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistContextModule {

    @Binds
    abstract fun bindAssistContextReader(impl: AssistContextReaderImpl): AssistContextReader
}
