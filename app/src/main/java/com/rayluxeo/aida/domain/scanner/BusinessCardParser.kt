package com.rayluxeo.aida.domain.scanner

import java.util.Locale

object BusinessCardParser {

    private val EMAIL_REGEX = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")
    private val PHONE_REGEX = Regex("""(?:\+?\d{1,3}[-.\s]?)?\(?\d{2,5}\)?[-.\s]?\d{3,5}[-.\s]?\d{3,5}""")
    private val WEBSITE_REGEX = Regex("""\b(?:https?://)?(?:www\.)?[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?\b""")

    private val COMPANY_KEYWORDS = listOf(
        "inc", "ltd", "limited", "corp", "corporation", "llc", "technologies",
        "solutions", "group", "studio", "co.", "company", "pvt", "agency",
        "enterprises", "soft", "labs", "software", "design", "media", "consulting",
    )

    private val ADDRESS_KEYWORDS = listOf(
        "road", "street", "ave", "avenue", "block", "house", "floor", "sector",
        "dhaka", "chittagong", "sylhet", "bangladesh", "zip", "po box", "suite",
        "tower", "center", "plaza", "building", "lane",
    )

    fun parse(ocrText: String): ParsedBusinessCard {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val email = extractEmail(ocrText)
        val phone = extractPhone(ocrText)
        val website = extractWebsite(ocrText, email)
        val company = extractCompany(lines)
        val address = extractAddress(lines)
        val name = extractName(lines, email, company)

        return ParsedBusinessCard(
            name = name,
            company = company,
            phone = phone,
            email = email,
            website = website,
            address = address,
            rawText = ocrText,
        )
    }

    private fun extractEmail(text: String): String? =
        EMAIL_REGEX.find(text)?.value

    private fun extractPhone(text: String): String? =
        PHONE_REGEX.findAll(text)
            .map { it.value.trim() }
            .firstOrNull { it.count { c -> c.isDigit() } in 7..15 }

    private fun extractWebsite(text: String, email: String?): String? {
        val emailDomain = email?.substringAfter("@", "")
        for (m in WEBSITE_REGEX.findAll(text)) {
            val candidate = m.value.trim()
            if (candidate.contains("@")) continue
            if (emailDomain.isNullOrBlank() || !candidate.contains(emailDomain)) {
                if (candidate.contains("www.") || candidate.startsWith("http")) {
                    return candidate
                }
            } else {
                return candidate
            }
        }
        return WEBSITE_REGEX.findAll(text)
            .map { it.value.trim() }
            .firstOrNull { !it.contains("@") && (it.contains("www.") || it.startsWith("http")) }
    }

    private fun extractCompany(lines: List<String>): String? {
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (COMPANY_KEYWORDS.any { lower.contains(it) }) {
                return line
            }
        }
        return null
    }

    private fun extractAddress(lines: List<String>): String? {
        val matchingLines = mutableListOf<String>()
        for (line in lines) {
            val lower = line.lowercase(Locale.ROOT)
            if (ADDRESS_KEYWORDS.any { lower.contains(it) }) {
                matchingLines.add(line)
            }
        }
        return if (matchingLines.isNotEmpty()) matchingLines.joinToString(", ") else null
    }

    private fun extractName(
        lines: List<String>,
        email: String?,
        company: String?,
    ): String? {
        for (line in lines) {
            if (line == company) continue
            if (email != null && line.contains(email, ignoreCase = true)) continue
            val lower = line.lowercase(Locale.ROOT)
            if (COMPANY_KEYWORDS.any { lower.contains(it) }) continue
            if (ADDRESS_KEYWORDS.any { lower.contains(it) }) continue
            if (PHONE_REGEX.containsMatchIn(line)) continue
            if (WEBSITE_REGEX.containsMatchIn(line)) continue

            val words = line.split("\\s+".toRegex())
            if (words.size in 1..4 && words.all { it.isNotEmpty() && (it[0].isUpperCase() || it.all { c -> c.isLetter() }) }) {
                return line
            }
        }
        return lines.firstOrNull { line ->
            line != company && (email == null || !line.contains(email))
        }
    }
}
