package com.rayluxeo.aida.assistant.di

import com.rayluxeo.aida.assistant.HotwordCapabilityDetector
import com.rayluxeo.aida.assistant.HotwordCapabilityDetectorImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The ways into Mira that are not her own window.
 *
 * Only the hotword detector needs binding. The Quick Settings tile, the home-screen widget and the
 * launcher shortcuts are Android components rather than dependencies: the tile is an
 * `@AndroidEntryPoint` and gets its own injection, and the widget and the shortcuts need nothing
 * injected at all.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AssistantEntryModule {

    @Binds
    @Singleton
    abstract fun bindHotwordCapabilityDetector(
        impl: HotwordCapabilityDetectorImpl,
    ): HotwordCapabilityDetector
}
