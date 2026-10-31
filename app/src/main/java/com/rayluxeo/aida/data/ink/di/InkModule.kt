package com.rayluxeo.aida.data.ink.di

import com.rayluxeo.aida.data.ink.AndroidInkRasterizer
import com.rayluxeo.aida.data.ink.JsonInkCodec
import com.rayluxeo.aida.domain.ink.InkCodec
import com.rayluxeo.aida.domain.ink.InkRasterizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The two ports that turn strokes into bytes.
 *
 * Bound beside the implementations rather than in the knowledge cluster's module because these are
 * the file format and the exporter, not a repository: what reads and writes ink pages depends on
 * them, and nothing else in Mira does.
 *
 * [InkCodec] must resolve to exactly one implementation. A second one would mean two stored
 * formats, and a page written by one and read by the other is a page Abu loses.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class InkModule {

    @Binds
    @Singleton
    abstract fun bindInkCodec(impl: JsonInkCodec): InkCodec

    @Binds
    @Singleton
    abstract fun bindInkRasterizer(impl: AndroidInkRasterizer): InkRasterizer
}
