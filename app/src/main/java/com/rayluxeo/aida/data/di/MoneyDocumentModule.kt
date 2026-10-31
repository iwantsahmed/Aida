package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.InvoiceDao
import com.rayluxeo.aida.data.db.dao.QuoteDao
import com.rayluxeo.aida.data.repository.InvoiceRepositoryImpl
import com.rayluxeo.aida.data.repository.QuoteRepositoryImpl
import com.rayluxeo.aida.data.repository.RecurringInvoiceRepositoryImpl
import com.rayluxeo.aida.domain.repository.InvoiceRepository
import com.rayluxeo.aida.domain.repository.QuoteRepository
import com.rayluxeo.aida.domain.repository.RecurringInvoiceRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The DAOs behind Abu's bills and offers.
 *
 * These live apart from `DataModule` so that the money documents can be wired without touching
 * the file every other cluster is also editing.
 */
@Module
@InstallIn(SingletonComponent::class)
object MoneyDocumentDaoModule {

    @Provides
    fun provideInvoiceDao(database: MiraDatabase): InvoiceDao = database.invoiceDao()

    @Provides
    fun provideQuoteDao(database: MiraDatabase): QuoteDao = database.quoteDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MoneyDocumentModule {

    @Binds
    @Singleton
    abstract fun bindInvoiceRepository(impl: InvoiceRepositoryImpl): InvoiceRepository

    @Binds
    @Singleton
    abstract fun bindQuoteRepository(impl: QuoteRepositoryImpl): QuoteRepository

    @Binds
    @Singleton
    abstract fun bindRecurringInvoiceRepository(impl: RecurringInvoiceRepositoryImpl): RecurringInvoiceRepository

    @Binds
    @Singleton
    abstract fun bindInvoiceDeliveryRepository(impl: com.rayluxeo.aida.data.repository.InvoiceDeliveryRepositoryImpl): com.rayluxeo.aida.domain.repository.InvoiceDeliveryRepository
}
