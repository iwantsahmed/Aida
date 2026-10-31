package com.rayluxeo.aida.ai.di

import com.rayluxeo.aida.ai.gemini.AndroidConnectivityProbe
import com.rayluxeo.aida.ai.gemini.ConnectivityProbe
import com.rayluxeo.aida.ai.gemini.GeminiClient
import com.rayluxeo.aida.ai.gemini.GeminiCloudAiStatusProvider
import com.rayluxeo.aida.ai.gemini.GeminiCommandEngine
import com.rayluxeo.aida.ai.gemini.HttpGeminiClient
import com.rayluxeo.aida.di.GeminiEngine
import com.rayluxeo.aida.domain.command.CommandEngine
import com.rayluxeo.aida.domain.finance.Calculator
import com.rayluxeo.aida.domain.repository.CloudAiStatusProvider
import com.rayluxeo.aida.finance.MiraCalculator
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * The optional cloud engine and the arithmetic that is deliberately not optional.
 *
 * [Calculator] is bound here rather than beside the engines because it is the counterweight to
 * them: whatever an engine proposes, the sums are still done by deterministic Kotlin.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class GeminiModule {

    @Binds
    @GeminiEngine
    abstract fun bindGeminiEngine(engine: GeminiCommandEngine): CommandEngine

    @Binds
    abstract fun bindGeminiClient(client: HttpGeminiClient): GeminiClient

    @Binds
    abstract fun bindConnectivityProbe(probe: AndroidConnectivityProbe): ConnectivityProbe

    @Binds
    abstract fun bindGeminiRepository(impl: com.rayluxeo.aida.data.repository.GeminiRepositoryImpl): com.rayluxeo.aida.domain.repository.GeminiRepository

    @Binds
    abstract fun bindCloudAiStatusProvider(impl: GeminiCloudAiStatusProvider): CloudAiStatusProvider

    @Binds
    abstract fun bindCalculator(calculator: MiraCalculator): Calculator
}
