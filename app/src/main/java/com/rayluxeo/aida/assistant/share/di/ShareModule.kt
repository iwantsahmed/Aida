package com.rayluxeo.aida.assistant.share.di

import com.rayluxeo.aida.assistant.share.IntentSharedPayloadReader
import com.rayluxeo.aida.assistant.share.SharedPayloadReader
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The share sheet's one binding, kept beside the code it binds.
 *
 * The reader is behind an interface for the reason every other Android-shaped edge in this
 * project is: [SharedPayloadReader] is the last place an `Intent`, a `Uri` or a `ContentResolver`
 * is mentioned, and nothing downstream of it — the view model, the state, the sheet — has to know
 * that a share arrived as any of those things.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShareModule {

    @Binds
    @Singleton
    abstract fun bindSharedPayloadReader(impl: IntentSharedPayloadReader): SharedPayloadReader
}
