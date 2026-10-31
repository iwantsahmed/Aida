package com.rayluxeo.aida.domain.task.di

import com.rayluxeo.aida.domain.task.DefaultRecurrenceCalculator
import com.rayluxeo.aida.domain.task.RecurrenceCalculator
import com.rayluxeo.aida.domain.task.RecurrenceCodec
import com.rayluxeo.aida.domain.task.TextRecurrenceCodec
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Binds the repeat arithmetic and the format it is stored in.
 *
 * Both must resolve to exactly one implementation: the editor previewing a series and the use case
 * that rolls it forward have to agree on what "every Monday" means, and a row encoded by one codec
 * and read by another would come back as a different rule or as no rule at all.
 *
 * The aliases are unscoped on purpose — neither implementation holds state, so an instance costs
 * nothing and a scope would claim a sharing that carries no meaning.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RecurrenceModule {

    @Binds
    abstract fun bindRecurrenceCalculator(calculator: DefaultRecurrenceCalculator): RecurrenceCalculator

    @Binds
    abstract fun bindRecurrenceCodec(codec: TextRecurrenceCodec): RecurrenceCodec
}
