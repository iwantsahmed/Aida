package com.rayluxeo.aida.di

import com.rayluxeo.aida.domain.command.handler.BusinessCommandHandler
import com.rayluxeo.aida.domain.command.handler.DefaultBusinessCommandHandler
import com.rayluxeo.aida.domain.command.handler.DefaultKnowledgeCommandHandler
import com.rayluxeo.aida.domain.command.handler.DefaultMoneyCommandHandler
import com.rayluxeo.aida.domain.command.handler.DefaultSalahCommandHandler
import com.rayluxeo.aida.domain.command.handler.DefaultWorkCommandHandler
import com.rayluxeo.aida.domain.command.handler.KnowledgeCommandHandler
import com.rayluxeo.aida.domain.command.handler.MoneyCommandHandler
import com.rayluxeo.aida.domain.command.handler.SalahCommandHandler
import com.rayluxeo.aida.domain.command.handler.WorkCommandHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The dispatcher's seams.
 *
 * [com.rayluxeo.aida.domain.command.DefaultCommandDispatcher] takes all five handlers at once, so
 * a handler missing a binding is a graph error rather than a command that quietly does nothing.
 * Keeping them together here is what makes that failure land in a single file instead of being
 * hunted across five feature modules.
 *
 * `PhoneCommandHandler` is the one exception and is deliberately absent: it is bound by
 * `data.di.SystemModule`, beside the two ContentProvider-backed repositories it is useless
 * without. Binding it here as well would be a duplicate binding and would fail the graph.
 *
 * The handlers are singletons because each one composes use cases that hold no state of their own;
 * a second instance would buy nothing and cost a graph's worth of objects per command.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class HandlerModule {

    @Binds
    @Singleton
    abstract fun bindWorkCommandHandler(impl: DefaultWorkCommandHandler): WorkCommandHandler

    @Binds
    @Singleton
    abstract fun bindKnowledgeCommandHandler(impl: DefaultKnowledgeCommandHandler): KnowledgeCommandHandler

    @Binds
    @Singleton
    abstract fun bindBusinessCommandHandler(impl: DefaultBusinessCommandHandler): BusinessCommandHandler

    @Binds
    @Singleton
    abstract fun bindMoneyCommandHandler(impl: DefaultMoneyCommandHandler): MoneyCommandHandler

    @Binds
    @Singleton
    abstract fun bindSalahCommandHandler(impl: DefaultSalahCommandHandler): SalahCommandHandler
}
