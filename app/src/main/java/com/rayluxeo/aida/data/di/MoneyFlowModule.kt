package com.rayluxeo.aida.data.di

import com.rayluxeo.aida.data.db.MiraDatabase
import com.rayluxeo.aida.data.db.dao.BudgetDao
import com.rayluxeo.aida.data.db.dao.CalculationDao
import com.rayluxeo.aida.data.db.dao.ExpenseDao
import com.rayluxeo.aida.data.db.dao.IncomeDao
import com.rayluxeo.aida.data.db.dao.PaymentDao
import com.rayluxeo.aida.data.db.dao.SubscriptionDao
import com.rayluxeo.aida.data.repository.BudgetRepositoryImpl
import com.rayluxeo.aida.data.repository.CalculationRepositoryImpl
import com.rayluxeo.aida.data.repository.ExpenseRepositoryImpl
import com.rayluxeo.aida.data.repository.IncomeRepositoryImpl
import com.rayluxeo.aida.data.repository.PaymentRepositoryImpl
import com.rayluxeo.aida.data.repository.SubscriptionRepositoryImpl
import com.rayluxeo.aida.domain.repository.BudgetRepository
import com.rayluxeo.aida.domain.repository.CalculationRepository
import com.rayluxeo.aida.domain.repository.ExpenseRepository
import com.rayluxeo.aida.domain.repository.IncomeRepository
import com.rayluxeo.aida.domain.repository.PaymentRepository
import com.rayluxeo.aida.domain.repository.SubscriptionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Everything that moves money: payments, expenses, income, budgets, subscriptions, and the
 * record of sums Mira has worked out.
 *
 * Kept out of `DataModule` because that file is shared ground and this cluster is edited as one
 * thing. The database itself, the clock and the dispatchers still come from `DataModule` — there
 * is exactly one `MiraDatabase` and this module only asks it for DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object MoneyFlowModule {

    @Provides
    fun providePaymentDao(database: MiraDatabase): PaymentDao = database.paymentDao()

    @Provides
    fun provideExpenseDao(database: MiraDatabase): ExpenseDao = database.expenseDao()

    @Provides
    fun provideIncomeDao(database: MiraDatabase): IncomeDao = database.incomeDao()

    @Provides
    fun provideBudgetDao(database: MiraDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideSubscriptionDao(database: MiraDatabase): SubscriptionDao = database.subscriptionDao()

    @Provides
    fun provideCalculationDao(database: MiraDatabase): CalculationDao = database.calculationDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class MoneyFlowBindingsModule {

    @Binds
    @Singleton
    abstract fun bindPaymentRepository(impl: PaymentRepositoryImpl): PaymentRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository

    @Binds
    @Singleton
    abstract fun bindIncomeRepository(impl: IncomeRepositoryImpl): IncomeRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindCalculationRepository(impl: CalculationRepositoryImpl): CalculationRepository

    @Binds
    @Singleton
    abstract fun bindExchangeRateRepository(impl: com.rayluxeo.aida.data.repository.ExchangeRateRepositoryImpl): com.rayluxeo.aida.domain.repository.ExchangeRateRepository
}
