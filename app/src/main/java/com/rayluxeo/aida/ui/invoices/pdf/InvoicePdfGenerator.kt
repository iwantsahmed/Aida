package com.rayluxeo.aida.ui.invoices.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.rayluxeo.aida.R
import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.InvoiceLine
import com.rayluxeo.aida.domain.model.InvoiceStatus
import com.rayluxeo.aida.domain.model.UserProfile
import com.rayluxeo.aida.ui.response.bengaliNumerals
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders Abu's invoice as a premium A4 PDF, entirely on-device.
 *
 * It holds to offline-first and zero cost: native [PdfDocument], no network, no library, and the
 * typeface is Mira's own **Manrope**, bundled in the APK — so the document looks identical on a
 * plane and matches the app rather than borrowing the system sans (§3, §10). The design is an
 * editorial letterhead in champagne on white: a wordmark, a large INVOICE mark with the number and
 * a status chip, a ruled line of meta, a gold-tinted item table, a totals stack that lands on a
 * balance-due plate, and a quiet thank-you — the look a bill from a studio should have.
 *
 * It is **bilingual**: the static labels and the closing line follow the [SpokenLanguage] the app is
 * speaking, drawn in Noto Serif Bengali (also bundled) when that is Bangla, and every figure's digits
 * fold to Bengali numerals there too. Names, descriptions and amounts are Abu's own and pass through
 * unchanged. The labels used to be hard-coded English, which an invoice Mira speaks Bengali about had
 * no business being (§1); they are a language-selected set now.
 */
@Singleton
class InvoicePdfGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val manropeRegular by lazy { font(R.font.manrope_regular, Typeface.SANS_SERIF) }
    private val manropeMedium by lazy { font(R.font.manrope_medium, Typeface.SANS_SERIF) }
    private val manropeSemiBold by lazy { font(R.font.manrope_semibold, Typeface.DEFAULT_BOLD) }
    private val manropeBold by lazy { font(R.font.manrope_bold, Typeface.DEFAULT_BOLD) }
    private val manropeExtraBold by lazy { font(R.font.manrope_extrabold, Typeface.DEFAULT_BOLD) }
    private val bengali by lazy { font(R.font.noto_serif_bengali, Typeface.SERIF) }

    /**
     * Abu's own "Sayed Ahmed" wordmark, the invoice's letterhead. Bundled in the APK like the fonts
     * (§3) — his site exports it near-white for a dark page, so the bundled copy is recoloured to a
     * print ink that reads on white. Decoded once and reused across every page and every invoice.
     * A missing or undecodable asset falls back to the typeset business name, so the header is never
     * blank.
     */
    private val letterheadLogo: Bitmap? by lazy {
        runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.invoice_logo) }.getOrNull()
    }

    private fun font(resId: Int, fallback: Typeface): Typeface =
        runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull() ?: fallback

    /** The typeface a plain label is drawn in — the Bengali serif when Mira is speaking Bangla. */
    private fun labelFace(language: SpokenLanguage): Typeface =
        if (language == SpokenLanguage.BENGALI) bengali else manropeSemiBold

    fun generate(
        invoice: Invoice,
        client: Client,
        profile: UserProfile? = null,
        language: SpokenLanguage = SpokenLanguage.ENGLISH,
        outputFile: File? = null,
    ): AppResult<File> {
        val target = outputFile ?: File(
            context.cacheDir,
            "invoice_${invoice.number.ifEmpty { "draft" }.replace(Regex("[^A-Za-z0-9]"), "_")}.pdf",
        )
        val labels = Labels.of(language)
        val document = PdfDocument()
        try {
            val lines = invoice.lines.ifEmpty {
                listOf(
                    InvoiceLine(
                        description = labels.serviceFallback,
                        quantity = BigDecimal.ONE,
                        unitPrice = invoice.total,
                        taxRatePercent = BigDecimal.ZERO,
                        lineTotal = invoice.total,
                        position = 0,
                    ),
                )
            }

            val pagesItems = splitItemsIntoPages(lines)
            val totalPages = pagesItems.size

            for (pageIndex in 0 until totalPages) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(COLOR_BG)

                var y = MARGIN_TOP
                if (pageIndex == 0) {
                    y = drawHeader(canvas, invoice, profile, labels, language, y)
                    y = drawClientAndMeta(canvas, invoice, client, labels, language, y)
                } else {
                    y = drawSubsequentHeader(canvas, invoice, labels, y)
                }

                val isLastPage = pageIndex == totalPages - 1
                y = drawTable(canvas, pagesItems[pageIndex], labels, language, y)

                if (isLastPage) {
                    y = drawTotals(canvas, invoice, labels, language, y)
                    drawFooterAndNotes(canvas, invoice, labels, language, y)
                }

                drawPageNumber(canvas, labels, language, pageIndex + 1, totalPages)
                document.finishPage(page)
            }

            FileOutputStream(target).use { out -> document.writeTo(out) }
            return AppResult.Success(target)
        } catch (e: Exception) {
            return failure(MiraErrorCode.STORAGE, "Failed to render invoice PDF: ${e.message}")
        } finally {
            document.close()
        }
    }

    private fun splitItemsIntoPages(lines: List<InvoiceLine>): List<List<InvoiceLine>> {
        if (lines.size <= FIRST_PAGE_CAPACITY) return listOf(lines)
        val pages = mutableListOf(lines.take(FIRST_PAGE_CAPACITY))
        var remaining = lines.drop(FIRST_PAGE_CAPACITY)
        while (remaining.isNotEmpty()) {
            pages.add(remaining.take(PAGE_CAPACITY))
            remaining = remaining.drop(PAGE_CAPACITY)
        }
        return pages
    }

    private fun drawHeader(
        canvas: Canvas,
        invoice: Invoice,
        profile: UserProfile?,
        labels: Labels,
        language: SpokenLanguage,
        startY: Float,
    ): Float {
        val y = startY

        // Left: Abu's wordmark and its contact lines. The bundled logo is the letterhead; the
        // typeset business name is only drawn when the asset could not be decoded, so the header is
        // never empty.
        val logo = letterheadLogo
        val wordmarkBottom: Float
        if (logo != null && logo.height > 0) {
            val logoWidth = LOGO_HEIGHT * logo.width / logo.height
            val dst = RectF(MARGIN_LEFT, y, MARGIN_LEFT + logoWidth, y + LOGO_HEIGHT)
            val logoPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { isAntiAlias = true }
            canvas.drawBitmap(logo, null, dst, logoPaint)
            wordmarkBottom = y + LOGO_HEIGHT
        } else {
            val wordmark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_CARBON
                textSize = 22f
                typeface = manropeExtraBold
                letterSpacing = 0.14f
            }
            val businessName = profile?.businessName ?: DEFAULT_BUSINESS
            canvas.drawText(businessName.uppercase(Locale.US), MARGIN_LEFT, y + 18f, wordmark)
            wordmarkBottom = y + 22f
        }

        val contact = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 9f
            typeface = manropeRegular
        }
        var cy = wordmarkBottom + 14f
        profile?.displayName?.let { canvas.drawText(it, MARGIN_LEFT, cy, contact); cy += 12f }
        profile?.email?.let { canvas.drawText(it, MARGIN_LEFT, cy, contact); cy += 12f }
        profile?.phone?.let { canvas.drawText(it, MARGIN_LEFT, cy, contact); cy += 12f }
        profile?.taxId?.let { canvas.drawText("${labels.taxId}: $it", MARGIN_LEFT, cy, contact); cy += 12f }

        // Right: the INVOICE mark with the number beneath it.
        val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CHAMPAGNE
            textSize = 26f
            typeface = if (language == SpokenLanguage.BENGALI) bengali else manropeBold
            letterSpacing = if (language == SpokenLanguage.BENGALI) 0f else 0.18f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(labels.invoice, PAGE_WIDTH - MARGIN_RIGHT, y + 20f, mark)

        val number = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARBON
            textSize = 11f
            typeface = manropeSemiBold
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(invoice.number.ifEmpty { labels.draft }, PAGE_WIDTH - MARGIN_RIGHT, y + 38f, number)

        val baseline = maxOf(cy, y + 52f)
        val rule = Paint().apply { color = COLOR_CHAMPAGNE; strokeWidth = 1.5f }
        canvas.drawLine(MARGIN_LEFT, baseline, PAGE_WIDTH - MARGIN_RIGHT, baseline, rule)
        return baseline + 26f
    }

    private fun drawSubsequentHeader(canvas: Canvas, invoice: Invoice, labels: Labels, startY: Float): Float {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARBON
            textSize = 10f
            typeface = manropeSemiBold
        }
        canvas.drawText("${labels.invoice}  •  ${invoice.number}", MARGIN_LEFT, startY + 10f, paint)
        val linePaint = Paint().apply { color = COLOR_HAIRLINE; strokeWidth = 1f }
        canvas.drawLine(MARGIN_LEFT, startY + 20f, PAGE_WIDTH - MARGIN_RIGHT, startY + 20f, linePaint)
        return startY + 38f
    }

    private fun drawClientAndMeta(
        canvas: Canvas,
        invoice: Invoice,
        client: Client,
        labels: Labels,
        language: SpokenLanguage,
        startY: Float,
    ): Float {
        val y = startY
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CHAMPAGNE
            textSize = 8.5f
            typeface = labelFace(language)
            letterSpacing = if (language == SpokenLanguage.BENGALI) 0f else 0.12f
        }
        val valueBold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARBON
            textSize = 13f
            typeface = manropeSemiBold
        }
        val valueRegular = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARBON
            textSize = 9.5f
            typeface = manropeRegular
        }

        canvas.drawText(labels.billedTo, MARGIN_LEFT, y, label)
        canvas.drawText(client.name, MARGIN_LEFT, y + 17f, valueBold)
        var clientY = y + 32f
        listOfNotNull(client.companyName, client.email, client.phone, client.addressLine).forEach {
            canvas.drawText(it, MARGIN_LEFT, clientY, valueRegular)
            clientY += 13f
        }

        // Right column: dates, right-aligned, over a status chip.
        val rightX = PAGE_WIDTH - MARGIN_RIGHT
        val metaLabel = Paint(label).apply { textAlign = Paint.Align.RIGHT; color = COLOR_MUTED }
        val metaValue = Paint(valueRegular).apply { textAlign = Paint.Align.RIGHT; typeface = manropeMedium }
        val formatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)

        var metaY = y
        canvas.drawText(labels.issued, rightX, metaY, metaLabel)
        canvas.drawText(digits(invoice.issuedOn.format(formatter), language), rightX, metaY + 13f, metaValue)
        metaY += 30f
        canvas.drawText(labels.dueDate, rightX, metaY, metaLabel)
        canvas.drawText(digits(invoice.dueOn.format(formatter), language), rightX, metaY + 13f, metaValue)
        metaY += 26f
        drawStatusPill(canvas, invoice.status, rightX - STATUS_PILL_WIDTH, metaY)

        return maxOf(clientY, metaY + STATUS_PILL_HEIGHT) + 26f
    }

    private fun drawStatusPill(canvas: Canvas, status: InvoiceStatus, x: Float, y: Float) {
        val text = when (status) {
            InvoiceStatus.DRAFT -> "DRAFT"
            InvoiceStatus.READY -> "READY"
            InvoiceStatus.SENT -> "SENT"
            InvoiceStatus.PARTIALLY_PAID -> "PARTIAL"
            InvoiceStatus.PAID -> "PAID"
            InvoiceStatus.OVERDUE -> "OVERDUE"
            InvoiceStatus.VOID -> "CANCELLED"
        }
        val accent = when (status) {
            InvoiceStatus.PAID -> Color.rgb(46, 110, 66)
            InvoiceStatus.OVERDUE -> Color.rgb(176, 58, 46)
            InvoiceStatus.PARTIALLY_PAID -> COLOR_CHAMPAGNE
            else -> Color.rgb(110, 110, 104)
        }
        val fill = Paint().apply { color = Color.rgb(248, 247, 243); style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent; style = Paint.Style.STROKE; strokeWidth = 1f }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
            textSize = 8f
            typeface = manropeBold
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.1f
        }
        canvas.drawRoundRect(x, y, x + STATUS_PILL_WIDTH, y + STATUS_PILL_HEIGHT, 8f, 8f, fill)
        canvas.drawRoundRect(x, y, x + STATUS_PILL_WIDTH, y + STATUS_PILL_HEIGHT, 8f, 8f, border)
        canvas.drawText(text, x + STATUS_PILL_WIDTH / 2f, y + 11.5f, textPaint)
    }

    private fun drawTable(
        canvas: Canvas,
        items: List<InvoiceLine>,
        labels: Labels,
        language: SpokenLanguage,
        startY: Float,
    ): Float {
        var y = startY
        val headerBg = Paint().apply { color = COLOR_TABLE_HEADER; style = Paint.Style.FILL }
        canvas.drawRoundRect(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y + 24f, 4f, 4f, headerBg)

        val header = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARBON
            textSize = 8.5f
            typeface = labelFace(language)
            letterSpacing = if (language == SpokenLanguage.BENGALI) 0f else 0.06f
        }
        val headerRight = Paint(header).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(labels.description, COL_DESC, y + 15f, header)
        canvas.drawText(labels.qty, COL_QTY, y + 15f, headerRight)
        canvas.drawText(labels.rate, COL_RATE, y + 15f, headerRight)
        canvas.drawText(labels.taxCol, COL_TAX, y + 15f, headerRight)
        canvas.drawText(labels.amount, COL_AMOUNT, y + 15f, headerRight)
        y += 28f

        val cell = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARBON
            textSize = 9.5f
            typeface = manropeRegular
        }
        val cellRight = Paint(cell).apply { textAlign = Paint.Align.RIGHT; typeface = manropeMedium }
        val cellDesc = Paint(cell).apply { typeface = manropeMedium }
        val hairline = Paint().apply { color = COLOR_HAIRLINE; strokeWidth = 0.6f }

        items.forEach { line ->
            val rowY = y + 15f
            canvas.drawText(line.description.ifEmpty { labels.serviceFallback }, COL_DESC, rowY, cellDesc)
            canvas.drawText(digits(line.quantity.stripTrailingZeros().toPlainString(), language), COL_QTY, rowY, cellRight)
            canvas.drawText(money(line.unitPrice.amount, language), COL_RATE, rowY, cellRight)
            canvas.drawText(digits("${line.taxRatePercent.stripTrailingZeros().toPlainString()}%", language), COL_TAX, rowY, cellRight)
            canvas.drawText(money(line.lineTotal.amount, language), COL_AMOUNT, rowY, cellRight)
            y += 26f
            canvas.drawLine(MARGIN_LEFT, y, PAGE_WIDTH - MARGIN_RIGHT, y, hairline)
        }
        return y + 18f
    }

    private fun drawTotals(
        canvas: Canvas,
        invoice: Invoice,
        labels: Labels,
        language: SpokenLanguage,
        startY: Float,
    ): Float {
        var y = startY
        val rightX = PAGE_WIDTH - MARGIN_RIGHT
        val labelX = rightX - TOTALS_WIDTH
        val curr = invoice.currency.currencyCode

        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 9.5f
            typeface = if (language == SpokenLanguage.BENGALI) bengali else manropeRegular
        }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARBON
            textSize = 9.5f
            typeface = manropeMedium
            textAlign = Paint.Align.RIGHT
        }

        fun row(name: String, amount: BigDecimal, negative: Boolean = false) {
            canvas.drawText(name, labelX, y, label)
            val prefix = if (negative) "-" else ""
            canvas.drawText("$prefix$curr ${money(amount, language)}", rightX, y, value)
            y += 17f
        }

        row(labels.subtotal, invoice.subtotal.amount)
        if (invoice.discount.amount > BigDecimal.ZERO) row(labels.discount, invoice.discount.amount, negative = true)
        if (invoice.tax.amount > BigDecimal.ZERO) row(labels.tax, invoice.tax.amount)

        canvas.drawLine(labelX, y - 4f, rightX, y - 4f, Paint().apply { color = COLOR_HAIRLINE; strokeWidth = 1f })
        y += 8f

        val totalLabel = Paint(label).apply {
            color = COLOR_CARBON
            typeface = if (language == SpokenLanguage.BENGALI) bengali else manropeBold
            textSize = 11.5f
        }
        val totalValue = Paint(value).apply { color = COLOR_CARBON; typeface = manropeBold; textSize = 12.5f }
        canvas.drawText(labels.total, labelX, y + 4f, totalLabel)
        canvas.drawText("$curr ${money(invoice.total.amount, language)}", rightX, y + 4f, totalValue)
        y += 24f

        if (invoice.amountPaid.amount > BigDecimal.ZERO) {
            row(labels.amountPaid, invoice.amountPaid.amount)
            y += 2f
        }

        // Balance-due plate.
        val plate = Paint().apply { color = COLOR_CHAMPAGNE_WASH; style = Paint.Style.FILL }
        canvas.drawRoundRect(labelX - 12f, y - 2f, rightX + 6f, y + 24f, 6f, 6f, plate)
        val balanceLabel = Paint(totalLabel).apply { color = COLOR_CHAMPAGNE_DEEP; textSize = 10f }
        val balanceValue = Paint(totalValue).apply { color = COLOR_CARBON; textSize = 12f }
        canvas.drawText(labels.balanceDue, labelX, y + 15f, balanceLabel)
        canvas.drawText("$curr ${money(invoice.outstanding.amount, language)}", rightX - 6f, y + 15f, balanceValue)
        return y + 42f
    }

    private fun drawFooterAndNotes(
        canvas: Canvas,
        invoice: Invoice,
        labels: Labels,
        language: SpokenLanguage,
        startY: Float,
    ) {
        var y = startY
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CHAMPAGNE
            textSize = 8.5f
            typeface = labelFace(language)
            letterSpacing = if (language == SpokenLanguage.BENGALI) 0f else 0.1f
        }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 9f
            typeface = if (language == SpokenLanguage.BENGALI) bengali else manropeRegular
        }

        canvas.drawText(labels.payment, MARGIN_LEFT, y, title)
        y += 14f
        canvas.drawText(labels.paymentLine, MARGIN_LEFT, y, body)
        y += 12f
        if (invoice.number.isNotEmpty()) {
            canvas.drawText(labels.paymentRef(digits(invoice.number, language)), MARGIN_LEFT, y, body)
            y += 22f
        }

        invoice.notes?.takeIf { it.isNotBlank() }?.let { notes ->
            canvas.drawText(labels.notes, MARGIN_LEFT, y, title)
            y += 14f
            notes.lines().take(MAX_NOTE_LINES).forEach { canvas.drawText(it, MARGIN_LEFT, y, body); y += 12f }
        }

        // A quiet thank-you, centred low on the page.
        val thanks = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CHAMPAGNE_DEEP
            textSize = 10.5f
            typeface = if (language == SpokenLanguage.BENGALI) bengali else manropeMedium
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(labels.thankYou, PAGE_WIDTH / 2f, PAGE_HEIGHT - 48f, thanks)
    }

    private fun drawPageNumber(canvas: Canvas, labels: Labels, language: SpokenLanguage, current: Int, total: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_MUTED
            textSize = 8.5f
            typeface = if (language == SpokenLanguage.BENGALI) bengali else manropeRegular
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            labels.pageOf(digits(current.toString(), language), digits(total.toString(), language)),
            PAGE_WIDTH / 2f,
            PAGE_HEIGHT - 26f,
            paint,
        )
    }

    /** Amounts are grouped at two decimals; in Bengali the digits fold to ০–৯ like everywhere else. */
    private fun money(amount: BigDecimal, language: SpokenLanguage): String =
        digits(AMOUNT_FORMAT.format(amount), language)

    private fun digits(text: String, language: SpokenLanguage): String =
        if (language == SpokenLanguage.BENGALI) bengaliNumerals(text) else text

    /**
     * The invoice's static text, in the language Mira is speaking. Names, descriptions and figures are
     * Abu's own and never pass through here — only the chrome.
     */
    private class Labels(
        val invoice: String,
        val draft: String,
        val billedTo: String,
        val issued: String,
        val dueDate: String,
        val description: String,
        val qty: String,
        val rate: String,
        val taxCol: String,
        val amount: String,
        val subtotal: String,
        val discount: String,
        val tax: String,
        val total: String,
        val amountPaid: String,
        val balanceDue: String,
        val payment: String,
        val paymentLine: String,
        val notes: String,
        val thankYou: String,
        val taxId: String,
        val serviceFallback: String,
        val paymentRef: (String) -> String,
        val pageOf: (String, String) -> String,
    ) {
        companion object {
            fun of(language: SpokenLanguage): Labels =
                if (language == SpokenLanguage.BENGALI) bengali() else english()

            private fun english() = Labels(
                invoice = "INVOICE",
                draft = "DRAFT",
                billedTo = "BILLED TO",
                issued = "ISSUED",
                dueDate = "DUE",
                description = "DESCRIPTION",
                qty = "QTY",
                rate = "RATE",
                taxCol = "TAX",
                amount = "AMOUNT",
                subtotal = "Subtotal",
                discount = "Discount",
                tax = "Tax",
                total = "TOTAL",
                amountPaid = "Amount paid",
                balanceDue = "BALANCE DUE",
                payment = "PAYMENT",
                paymentLine = "Bank transfer or mobile wallet (bKash, Nagad).",
                notes = "NOTES",
                thankYou = "Thank you for your business.",
                taxId = "BIN / Tax ID",
                serviceFallback = "Service",
                paymentRef = { number -> "Please reference invoice $number on payment." },
                pageOf = { current, total -> "Page $current of $total" },
            )

            private fun bengali() = Labels(
                invoice = "ইনভয়েস",
                draft = "খসড়া",
                billedTo = "যার নামে",
                issued = "ইস্যুর তারিখ",
                dueDate = "শেষ তারিখ",
                description = "বিবরণ",
                qty = "পরিমাণ",
                rate = "দর",
                taxCol = "কর",
                amount = "মোট",
                subtotal = "উপমোট",
                discount = "ছাড়",
                tax = "কর",
                total = "সর্বমোট",
                amountPaid = "পরিশোধিত",
                balanceDue = "বাকি",
                payment = "পেমেন্ট",
                paymentLine = "ব্যাংক ট্রান্সফার বা মোবাইল ওয়ালেট (বিকাশ, নগদ)।",
                notes = "নোট",
                thankYou = "আপনার সঙ্গে ব্যবসা করে আনন্দিত।",
                taxId = "বিন / কর আইডি",
                serviceFallback = "সেবা",
                paymentRef = { number -> "পেমেন্টের সময় ইনভয়েস $number উল্লেখ করুন।" },
                pageOf = { current, total -> "পৃষ্ঠা $current / $total" },
            )
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842

        const val MARGIN_LEFT = 42f
        const val MARGIN_RIGHT = 42f
        const val MARGIN_TOP = 48f

        /** Letterhead logo height in points; width follows the wordmark's own aspect ratio. */
        const val LOGO_HEIGHT = 40f

        const val COL_DESC = 46f
        const val COL_QTY = 330f
        const val COL_RATE = 410f
        const val COL_TAX = 460f
        const val COL_AMOUNT = 553f

        const val TOTALS_WIDTH = 195f
        const val STATUS_PILL_WIDTH = 66f
        const val STATUS_PILL_HEIGHT = 18f

        const val FIRST_PAGE_CAPACITY = 7
        const val PAGE_CAPACITY = 16
        const val MAX_NOTE_LINES = 4

        const val DEFAULT_BUSINESS = "Rayluxeo Studio"

        val AMOUNT_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }

        val COLOR_BG = Color.WHITE
        val COLOR_CARBON = Color.rgb(20, 20, 20)
        val COLOR_MUTED = Color.rgb(120, 120, 114)
        val COLOR_CHAMPAGNE = Color.rgb(168, 133, 76)
        val COLOR_CHAMPAGNE_DEEP = Color.rgb(140, 107, 54)
        val COLOR_CHAMPAGNE_WASH = Color.rgb(247, 243, 234)
        val COLOR_TABLE_HEADER = Color.rgb(247, 246, 242)
        val COLOR_HAIRLINE = Color.rgb(228, 226, 220)
    }
}
