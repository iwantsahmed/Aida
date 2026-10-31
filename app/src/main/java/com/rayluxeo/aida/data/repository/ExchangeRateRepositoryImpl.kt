package com.rayluxeo.aida.data.repository

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraClock
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.asSuccess
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.core.common.getOrNull
import com.rayluxeo.aida.domain.model.ExchangeRate
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.repository.AppSettingRepository
import com.rayluxeo.aida.domain.repository.ExchangeRateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Instant
import java.util.Currency
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StoredRateDto(
    val base: String,
    val target: String,
    val rate: String,
    val isManual: Boolean,
    val updatedAtEpochMillis: Long,
)

@Singleton
class ExchangeRateRepositoryImpl @Inject constructor(
    private val settings: AppSettingRepository,
    private val clock: MiraClock,
) : ExchangeRateRepository {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override fun observeAll(): Flow<List<ExchangeRate>> =
        settings.observe(KEY_EXCHANGE_RATES).map { raw ->
            val manualRates = parseStored(raw)
            mergeWithDefaults(manualRates)
        }

    override suspend fun getRate(fromCurrencyCode: String, toCurrencyCode: String): AppResult<ExchangeRate?> {
        val from = fromCurrencyCode.uppercase()
        val to = toCurrencyCode.uppercase()
        if (from == to) {
            return ExchangeRate(
                baseCurrencyCode = from,
                targetCurrencyCode = to,
                rate = BigDecimal.ONE,
                isManual = false,
                updatedAt = clock.now(),
            ).asSuccess()
        }

        val all = getAllRates()
        val direct = all.firstOrNull { it.baseCurrencyCode == from && it.targetCurrencyCode == to }
        if (direct != null) return direct.asSuccess()

        val inverse = all.firstOrNull { it.baseCurrencyCode == to && it.targetCurrencyCode == from }
        if (inverse != null && inverse.rate.compareTo(BigDecimal.ZERO) != 0) {
            val invertedRate = BigDecimal.ONE.divide(inverse.rate, MathContext.DECIMAL64)
            return ExchangeRate(
                baseCurrencyCode = from,
                targetCurrencyCode = to,
                rate = invertedRate,
                isManual = inverse.isManual,
                updatedAt = inverse.updatedAt,
            ).asSuccess()
        }

        // Cross-rate via BDT
        val fromToBdt = all.firstOrNull { it.baseCurrencyCode == from && it.targetCurrencyCode == "BDT" }
            ?: all.firstOrNull { it.baseCurrencyCode == "BDT" && it.targetCurrencyCode == from }?.let {
                if (it.rate > BigDecimal.ZERO) BigDecimal.ONE.divide(it.rate, MathContext.DECIMAL64) else null
            }?.let { ExchangeRate(from, "BDT", it, false, clock.now()) }

        val toToBdt = all.firstOrNull { it.baseCurrencyCode == to && it.targetCurrencyCode == "BDT" }
            ?: all.firstOrNull { it.baseCurrencyCode == "BDT" && it.targetCurrencyCode == to }?.let {
                if (it.rate > BigDecimal.ZERO) BigDecimal.ONE.divide(it.rate, MathContext.DECIMAL64) else null
            }?.let { ExchangeRate(to, "BDT", it, false, clock.now()) }

        if (fromToBdt != null && toToBdt != null && toToBdt.rate > BigDecimal.ZERO) {
            val cross = fromToBdt.rate.divide(toToBdt.rate, MathContext.DECIMAL64)
            return ExchangeRate(
                baseCurrencyCode = from,
                targetCurrencyCode = to,
                rate = cross,
                isManual = fromToBdt.isManual || toToBdt.isManual,
                updatedAt = clock.now(),
            ).asSuccess()
        }

        return AppResult.Success(null)
    }

    override suspend fun setManualRate(
        fromCurrencyCode: String,
        toCurrencyCode: String,
        rate: BigDecimal,
    ): AppResult<ExchangeRate> {
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return failure(MiraErrorCode.VALIDATION, "Exchange rate must be positive")
        }
        val from = fromCurrencyCode.uppercase()
        val to = toCurrencyCode.uppercase()
        val now = clock.now()
        val existing = getStoredList().toMutableList()
        existing.removeAll { it.base == from && it.target == to }
        val updated = ExchangeRate(
            baseCurrencyCode = from,
            targetCurrencyCode = to,
            rate = rate,
            isManual = true,
            updatedAt = now,
        )
        existing.add(
            StoredRateDto(
                base = from,
                target = to,
                rate = rate.toPlainString(),
                isManual = true,
                updatedAtEpochMillis = now.toEpochMilli(),
            ),
        )
        saveStoredList(existing)
        return updated.asSuccess()
    }

    override suspend fun removeManualRate(fromCurrencyCode: String, toCurrencyCode: String): AppResult<Unit> {
        val from = fromCurrencyCode.uppercase()
        val to = toCurrencyCode.uppercase()
        val existing = getStoredList().toMutableList()
        val changed = existing.removeAll { it.base == from && it.target == to }
        if (changed) {
            saveStoredList(existing)
        }
        return AppResult.Success(Unit)
    }

    override suspend fun convert(money: Money, targetCurrency: Currency): AppResult<Money> {
        if (money.currency == targetCurrency) return money.asSuccess()

        val rate = when (val found = getRate(money.currency.currencyCode, targetCurrency.currencyCode)) {
            is AppResult.Failure -> return found
            is AppResult.Success -> found.value
        } ?: return failure(
            MiraErrorCode.NOT_FOUND,
            "No exchange rate configured between ${money.currency.currencyCode} and ${targetCurrency.currencyCode}",
        )

        val digits = targetCurrency.defaultFractionDigits.coerceAtLeast(0)
        val convertedAmount = money.amount.multiply(rate.rate).setScale(digits, RoundingMode.HALF_UP)
        return Money(convertedAmount, targetCurrency).asSuccess()
    }

    private suspend fun getAllRates(): List<ExchangeRate> {
        val manual = parseStored(settings.get(KEY_EXCHANGE_RATES).getOrNull())
        return mergeWithDefaults(manual)
    }

    private suspend fun getStoredList(): List<StoredRateDto> {
        val raw = settings.get(KEY_EXCHANGE_RATES).getOrNull() ?: return emptyList()
        return try {
            json.decodeFromString<List<StoredRateDto>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun saveStoredList(list: List<StoredRateDto>) {
        val encoded = json.encodeToString(list)
        settings.put(KEY_EXCHANGE_RATES, encoded)
    }

    private fun parseStored(raw: String?): List<ExchangeRate> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val dtoList = json.decodeFromString<List<StoredRateDto>>(raw)
            dtoList.mapNotNull {
                val rateVal = try { BigDecimal(it.rate) } catch (_: Exception) { null } ?: return@mapNotNull null
                ExchangeRate(
                    baseCurrencyCode = it.base,
                    targetCurrencyCode = it.target,
                    rate = rateVal,
                    isManual = it.isManual,
                    updatedAt = Instant.ofEpochMilli(it.updatedAtEpochMillis),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mergeWithDefaults(manual: List<ExchangeRate>): List<ExchangeRate> {
        val result = manual.toMutableList()
        val defaultEpoch = Instant.parse("2026-10-01T00:00:00Z")
        for (defaultRate in DEFAULT_OFFLINE_RATES) {
            if (result.none { it.baseCurrencyCode == defaultRate.base && it.targetCurrencyCode == defaultRate.target }) {
                result.add(
                    ExchangeRate(
                        baseCurrencyCode = defaultRate.base,
                        targetCurrencyCode = defaultRate.target,
                        rate = BigDecimal(defaultRate.rate),
                        isManual = false,
                        updatedAt = defaultEpoch,
                    ),
                )
            }
        }
        return result
    }

    private companion object {
        const val KEY_EXCHANGE_RATES = "manual_exchange_rates"

        // Baseline offline exchange rates relative to BDT
        val DEFAULT_OFFLINE_RATES = listOf(
            StoredRateDto("USD", "BDT", "121.50", false, 0L),
            StoredRateDto("EUR", "BDT", "132.20", false, 0L),
            StoredRateDto("GBP", "BDT", "158.40", false, 0L),
            StoredRateDto("INR", "BDT", "1.45", false, 0L),
            StoredRateDto("CAD", "BDT", "88.50", false, 0L),
            StoredRateDto("AUD", "BDT", "80.20", false, 0L),
            StoredRateDto("JPY", "BDT", "0.81", false, 0L),
            StoredRateDto("SGD", "BDT", "92.30", false, 0L),
            StoredRateDto("AED", "BDT", "33.10", false, 0L),
            StoredRateDto("SAR", "BDT", "32.40", false, 0L),
        )
    }
}
