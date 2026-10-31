package com.rayluxeo.aida.di

import com.rayluxeo.aida.ai.CommandRouter
import com.rayluxeo.aida.domain.command.CommandDispatcher
import com.rayluxeo.aida.domain.command.CommandInterpreter
import com.rayluxeo.aida.domain.command.CommandValidator
import com.rayluxeo.aida.domain.command.DefaultCommandDispatcher
import com.rayluxeo.aida.domain.command.DefaultCommandValidator
import com.rayluxeo.aida.domain.usecase.InvoiceMessageComposer
import com.rayluxeo.aida.ui.invoices.delivery.InvoiceDeliveryMessages
import com.rayluxeo.aida.ui.response.MiraResponseRenderer
import com.rayluxeo.aida.ui.response.ResponseRenderer
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Cross-cutting wiring that belongs to no single feature: the command pipeline's gate and
 * executor, and the renderer that turns Mira's structured replies into words.
 *
 * [CommandValidator] in particular must resolve to exactly one implementation — it is the single
 * gate every engine's output passes through, including Gemini's.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCommandValidator(impl: DefaultCommandValidator): CommandValidator

    @Binds
    @Singleton
    abstract fun bindCommandDispatcher(impl: DefaultCommandDispatcher): CommandDispatcher

    @Binds
    @Singleton
    abstract fun bindResponseRenderer(impl: MiraResponseRenderer): ResponseRenderer

    @Binds
    @Singleton
    abstract fun bindCommandInterpreter(impl: CommandRouter): CommandInterpreter

    @Binds
    @Singleton
    abstract fun bindInvoiceMessageComposer(impl: InvoiceDeliveryMessages): InvoiceMessageComposer
}
