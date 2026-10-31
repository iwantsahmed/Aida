package com.rayluxeo.aida.voice.di

import com.rayluxeo.aida.voice.MiraAudioFocusManager
import com.rayluxeo.aida.voice.MiraAudioFocusManagerImpl
import com.rayluxeo.aida.voice.MiraEarconPlayer
import com.rayluxeo.aida.voice.MiraEarconPlayerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The audio output itself: who owns the speaker right now, and Mira's own tones.
 *
 * Both implementations hold a native resource that must exist exactly once — one outstanding
 * audio focus request, one [android.media.AudioTrack] per tone — so each is `@Singleton` in its
 * own right and the aliases here are deliberately unscoped.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AudioModule {

    @Binds
    abstract fun bindAudioFocusManager(impl: MiraAudioFocusManagerImpl): MiraAudioFocusManager

    @Binds
    abstract fun bindEarconPlayer(impl: MiraEarconPlayerImpl): MiraEarconPlayer
}
