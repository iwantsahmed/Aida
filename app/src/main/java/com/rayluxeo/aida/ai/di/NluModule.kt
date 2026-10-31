package com.rayluxeo.aida.ai.di

import com.rayluxeo.aida.ai.local.LocalCommandEngine
import com.rayluxeo.aida.ai.nlu.MiraDateTimeParser
import com.rayluxeo.aida.ai.nlu.InputNormalizer
import com.rayluxeo.aida.di.LocalEngine
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.nlu.DateTimeParser
import com.rayluxeo.aida.domain.nlu.TextNormalizer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the on-device language understanding. The implementations are singletons in their own
 * right — each holds a folded vocabulary that is worth building once — so the aliases here are
 * deliberately unscoped.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NluModule {

    @Binds
    abstract fun bindTextNormalizer(normalizer: InputNormalizer): TextNormalizer

    @Binds
    abstract fun bindDateTimeParser(parser: MiraDateTimeParser): DateTimeParser

    @Binds
    @LocalEngine
    abstract fun bindLocalEngine(engine: LocalCommandEngine): CommandEngine
}
