package com.rayluxeo.aida.domain.scanner

import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object ReceiptParser {

    private val GENERIC_HEADER_KEYWORDS = listOf(
        "receipt", "tax invoice", "invoice", "cash memo", "bill",
        "welcome", "thank you", "customer copy", "merchant copy",
        "official receipt", "sales receipt", "terminal", "order", "date:", "date",
    )

    private val DATE_PATTERNS = listOf(
        Regex("""\b(\d{4})[-/\.](\d{1,2})[-/\.](\d{1,2})\b"""), // YYYY-MM-DD
        Regex("""\b(\d{1,2})[-/\.](\d{1,2})[-/\.](\d{4})\b"""), // DD-MM-YYYY or MM-DD-YYYY
        Regex("""\b(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{4})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\s+(\d{1,2}),?\s+(\d{4})\b""", RegexOption.IGNORE_CASE),
    )

    private val AMOUNT_LINE_KEYWORDS = listOf(
        "total", "grand total", "net total", "amount due", "net amount",
        "total amount", "payable", "cash", "card", "subtotal",
    )

    private val NUMBER_REGEX = Regex("""\b([0-9]{1,3}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)\b""")

    fun parse(
        ocrText: String,
        defaultCurrencyCode: String = "BDT",
    ): ParsedReceipt {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val merchant = extractMerchant(lines)
        val date = extractDate(lines)
        val amount = extractAmount(lines, defaultCurrencyCode)
        val category = inferCategory(merchant, lines)

        return ParsedReceipt(
            merchant = merchant,
            date = date,
            amount = amount,
            category = category,
            rawText = ocrText,
        )
    }

    private fun extractMerchant(lines: List<String>): String? {
        for (line in lines.take(6)) {
            val lower = line.lowercase(Locale.ROOT)
            if (GENERIC_HEADER_KEYWORDS.any { lower.startsWith(it) || lower == it }) continue
            if (line.length < 2) continue
            if (line.all { it.isDigit() || it.isWhitespace() || !it.isLetterOrDigit() }) continue
            return line
        }
        return lines.firstOrNull { it.length > 2 }
    }

    private fun extractDate(lines: List<String>): LocalDate? {
        for (line in lines) {
            for (pattern in DATE_PATTERNS) {
                val match = pattern.find(line) ?: continue
                val groupValues = match.groupValues
                runCatching {
                    if (pattern.pattern.contains("Jan|Feb")) {
                        val dayStr = groupValues[1]
                        val monthStr = groupValues[2]
                        val yearStr = groupValues[3]
                        if (dayStr.all { it.isDigit() }) {
                            val parsed = LocalDate.parse(
                                "$dayStr ${monthStr.substring(0, 3)} $yearStr",
                                DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH),
                            )
                            return parsed
                        }
                    } else if (groupValues.size == 4) {
                        val first = groupValues[1].toInt()
                        val second = groupValues[2].toInt()
                        val third = groupValues[3].toInt()

                        if (first > 1000) {
                            return LocalDate.of(first, second.coerceIn(1, 12), third.coerceIn(1, 31))
                        } else if (third > 1000) {
                            val month = if (first <= 12 && second > 12) first else if (second <= 12) second else 1
                            val day = if (first > 12) first else second
                            return LocalDate.of(third, month.coerceIn(1, 12), day.coerceIn(1, 31))
                        }
                    }
                }
            }
        }
        return null
    }

    private fun extractAmount(lines: List<String>, currencyCode: String): Money? {
        var highestAmount: BigDecimal? = null

        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (AMOUNT_LINE_KEYWORDS.any { lower.contains(it) }) {
                val numbers = NUMBER_REGEX.findAll(line)
                for (m in numbers) {
                    val cleaned = m.groupValues[1].replace(",", "")
                    val valDec = cleaned.toBigDecimalOrNull()
                    if (valDec != null && valDec.signum() > 0) {
                        if (highestAmount == null || valDec > highestAmount) {
                            highestAmount = valDec
                        }
                    }
                }
            }
        }

        if (highestAmount != null) {
            val curr = runCatching { Currency.getInstance(currencyCode) }.getOrDefault(Money.BDT)
            return Money(highestAmount.setScale(2, RoundingMode.HALF_UP), curr)
        }

        val searchLines = if (lines.size > 5) lines.drop(lines.size / 3) else lines
        for (line in searchLines) {
            val numbers = NUMBER_REGEX.findAll(line)
            for (m in numbers) {
                val cleaned = m.groupValues[1].replace(",", "")
                val valDec = cleaned.toBigDecimalOrNull()
                if (valDec != null && valDec.signum() > 0 && valDec < BigDecimal("1000000")) {
                    if (highestAmount == null || valDec > highestAmount) {
                        highestAmount = valDec
                    }
                }
            }
        }

        return highestAmount?.let {
            val curr = runCatching { Currency.getInstance(currencyCode) }.getOrDefault(Money.BDT)
            Money(it.setScale(2, RoundingMode.HALF_UP), curr)
        }
    }

    private fun inferCategory(merchant: String?, lines: List<String>): ExpenseCategory {
        val fullText = ((merchant ?: "") + " " + lines.joinToString(" ")).lowercase(Locale.ROOT)

        return when {
            containsAny(fullText, "restaurant", "dine", "dining", "cafe", "coffee", "kitchen", "diner", "food", "burger", "pizza", "kebab", "bakery", "supermarket", "grocery", "sweets", "catering", "biryani") ->
                ExpenseCategory.FOOD

            containsAny(fullText, "computer", "laptop", "tech", "mobile", "gadget", "electronics", "star tech", "ryans", "apple", "samsung", "hardware", "engineering") ->
                ExpenseCategory.HARDWARE

            containsAny(fullText, "uber", "pathao", "fuel", "petrol", "filling station", "oil", "parking", "taxi", "cab", "biman", "airline", "railway", "train", "express", "flight", "cng", "ride") ->
                ExpenseCategory.TRAVEL

            containsAny(fullText, "power", "electric", "desco", "dpdc", "wasa", "gas", "titas", "internet", "link3", "amberit", "broadband", "utility", "water", "bill") ->
                ExpenseCategory.UTILITIES

            containsAny(fullText, "github", "aws", "google", "cloud", "domain", "hosting", "jetbrains", "microsoft", "software", "saas", "subscription") ->
                ExpenseCategory.SOFTWARE

            else -> ExpenseCategory.OTHER
        }
    }

    private fun containsAny(text: String, vararg keywords: String): Boolean =
        keywords.any { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) }
}
