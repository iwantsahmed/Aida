package com.rayluxeo.aida.di

import com.rayluxeo.aida.domain.report.DefaultReportEngine
import com.rayluxeo.aida.domain.report.ReportEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The report engine's binding.
 *
 * It lives here beside [BriefBindingsModule] rather than under `data/di` for the same reason: the
 * implementation is pure domain composing repository ports, and a module under the storage layer
 * would say it belongs to Room, which is exactly what it must not. A singleton because it is
 * stateless and read by the Reports screen, the voice pipeline and the exporters alike.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ReportBindingsModule {

    @Binds
    @Singleton
    abstract fun bindReportEngine(impl: DefaultReportEngine): ReportEngine
}
