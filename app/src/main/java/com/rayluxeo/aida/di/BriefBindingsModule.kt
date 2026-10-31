package com.rayluxeo.aida.di

import com.rayluxeo.aida.ui.response.BriefNotificationWordingImpl
import com.rayluxeo.aida.domain.brief.BriefNotificationWording
import com.rayluxeo.aida.domain.brief.DailyBriefEngine
import com.rayluxeo.aida.domain.brief.DefaultDailyBriefEngine
import com.rayluxeo.aida.domain.brief.DefaultEveningReviewEngine
import com.rayluxeo.aida.domain.brief.EveningReviewEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The two engines that read Abu's day back to him.
 *
 * They live here rather than under `data/di` for the same reason `AppBindingsModule` does: both
 * implementations are pure domain, composing repository ports, and there is no `domain/di` — a
 * module under `data/di` would say they belong to the storage layer, which is exactly what they
 * must not do.
 *
 * Both are singletons because both are stateless and both have several callers — Home, the voice
 * pipeline and the scheduled notification — and there is nothing to be gained from a second copy.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class BriefBindingsModule {

    @Binds
    @Singleton
    abstract fun bindDailyBriefEngine(impl: DefaultDailyBriefEngine): DailyBriefEngine

    @Binds
    @Singleton
    abstract fun bindEveningReviewEngine(impl: DefaultEveningReviewEngine): EveningReviewEngine

    /**
     * The notification's words. Bound here rather than in `data/di` because the implementation
     * lives in `ui/response` beside the narrator it delegates to, while the consumer is the alarm
     * receiver in `data/scheduler` — the port in `domain` is what keeps that from being a
     * dependency pointing outward.
     */
    @Binds
    @Singleton
    abstract fun bindBriefNotificationWording(
        impl: BriefNotificationWordingImpl,
    ): BriefNotificationWording

    @Binds
    @Singleton
    abstract fun bindProactiveAttentionEngine(
        impl: com.rayluxeo.aida.domain.attention.DefaultProactiveAttentionEngine,
    ): com.rayluxeo.aida.domain.attention.ProactiveAttentionEngine
}

