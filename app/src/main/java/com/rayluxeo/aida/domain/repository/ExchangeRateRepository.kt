package com.rayluxeo.aida.domain.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.domain.model.ExchangeRate
import com.rayluxeo.aida.domain.model.Money
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.Currency

/**
 * Manages currency exchange rates.
 *
 * Manual rates configured by Abu are always retained offline and take precedence
 * over any online rates. All conversions are performed using deterministic Kotlin arithmetic.
 */
interface ExchangeRateRepository {
    fun observeAll(): Flow<List<ExchangeRate>>

    suspend fun getRate(fromCurrencyCode: String, toCurrencyCode: String): AppResult<ExchangeRate?>

    suspend fun setManualRate(
        fromCurrencyCode: String,
        toCurrencyCode: String,
        rate: BigDecimal,
    ): AppResult<ExchangeRate>

    suspend fun removeManualRate(fromCurrencyCode: String, toCurrencyCode: String): AppResult<Unit>

    suspend fun convert(money: Money, targetCurrency: Currency): AppResult<Money>
}
