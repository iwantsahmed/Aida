package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.InkPageDao
import com.rayluxeo.aida.data.db.dao.MemoryDao
import com.rayluxeo.aida.data.db.dao.NoteDao
import com.rayluxeo.aida.data.db.dao.VoiceNoteDao
import com.rayluxeo.aida.data.repository.InkPageRepositoryImpl
import com.rayluxeo.aida.data.repository.MemoryRepositoryImpl
import com.rayluxeo.aida.data.repository.NoteRepositoryImpl
import com.rayluxeo.aida.data.repository.VoiceNoteRepositoryImpl
import com.rayluxeo.aida.domain.repository.InkPageRepository
import com.rayluxeo.aida.domain.repository.MemoryRepository
import com.rayluxeo.aida.domain.repository.NoteRepository
import com.rayluxeo.aida.domain.repository.VoiceNoteRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Notes, handwriting pages, memory and voice notes.
 *
 * Kept apart from [DataModule] because that module is shared ground and this cluster's DAOs and
 * repositories change together. The database itself still comes from there — there is one.
 *
 * Handwriting is wired here rather than in a module of its own because an ink page is a page OF a
 * note: it cascades from one, it is filtered for in the notes list, and it has nothing to say on
 * its own. The canvas that draws strokes and the codec that writes them down are a different
 * matter and bind where they are implemented.
 */
@Module
@InstallIn(SingletonComponent::class)
object KnowledgeModule {

    @Provides
    fun provideNoteDao(database: MiraDatabase): NoteDao = database.noteDao()

    @Provides
    fun provideMemoryDao(database: MiraDatabase): MemoryDao = database.memoryDao()

    @Provides
    fun provideVoiceNoteDao(database: MiraDatabase): VoiceNoteDao = database.voiceNoteDao()

    @Provides
    fun provideInkPageDao(database: MiraDatabase): InkPageDao = database.inkPageDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class KnowledgeBindingsModule {

    @Binds
    @Singleton
    abstract fun bindNoteRepository(impl: NoteRepositoryImpl): NoteRepository

    @Binds
    @Singleton
    abstract fun bindInkPageRepository(impl: InkPageRepositoryImpl): InkPageRepository

    @Binds
    @Singleton
    abstract fun bindMemoryRepository(impl: MemoryRepositoryImpl): MemoryRepository

    @Binds
    @Singleton
    abstract fun bindVoiceNoteRepository(impl: VoiceNoteRepositoryImpl): VoiceNoteRepository
}
