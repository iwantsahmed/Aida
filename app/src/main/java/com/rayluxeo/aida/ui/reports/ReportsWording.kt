package com.rayluxeo.aida.ui.reports

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.model.ExpenseCategory
import com.rayluxeo.aida.domain.model.Money
import com.rayluxeo.aida.domain.model.ProjectStatus
import com.rayluxeo.aida.domain.report.ReportMetric
import com.rayluxeo.aida.domain.report.ReportPeriod
import com.rayluxeo.aida.domain.report.ReportType
import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.ui.response.moneyFigure
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Every word the Reports screen puts in front of Abu, in one language: the periods, the twelve
 * report titles, the fixed metric/category/status labels a breakdown line can carry, and the way a
 * money figure and a date are written. Money and digits are rendered here rather than in the domain,
 * which carries only [Money] and counts (§4).
 */
internal interface ReportsWording {

    val screenTitle: String
    val choosePeriod: String
    val nothingYet: String
    val mixedCurrencies: String
    val exportPdf: String
    val exportCsv: String
    val exporting: String
    val exportReady: String
    val exportFailed: String
    val rangeApply: String
    val rangeCancel: String

    fun periodLabel(period: ReportPeriod): String
    fun reportTitle(type: ReportType): String
    fun metricLabel(metric: ReportMetric): String
    fun categoryLabel(category: ExpenseCategory): String
    fun statusLabel(status: ProjectStatus): String

    /** The resolved window, as "1–31 Oct" or "অক্টোবর ১–৩১". */
    fun rangeLabel(from: LocalDate, to: LocalDate): String

    /** A money figure with the taka sign and the language's own digits. */
    fun money(amount: Money): String

    /** A bare count in the language's own digits. */
    fun number(value: Int): String

    /** "3 items" — the count beside a headline that is a tally rather than a sum. */
    fun items(value: Int): String
}

internal object EnglishReportsWording : ReportsWording {

    override val screenTitle = "Reports"
    override val choosePeriod = "Period"
    override val nothingYet = "Nothing recorded for this period yet."
    override val mixedCurrencies = "Mixed currencies — shown separately below."
    override val exportPdf = "PDF"
    override val exportCsv = "CSV"
    override val exporting = "Preparing the report…"
    override val exportReady = "Report exported."
    override val exportFailed = "Couldn't export the report."
    override val rangeApply = "Apply"
    override val rangeCancel = "Cancel"

    override fun periodLabel(period: ReportPeriod) = when (period) {
        ReportPeriod.Today -> "Today"
        ReportPeriod.ThisWeek -> "This week"
        ReportPeriod.ThisMonth -> "This month"
        ReportPeriod.LastMonth -> "Last month"
        is ReportPeriod.Custom -> "Custom"
    }

    override fun reportTitle(type: ReportType) = when (type) {
        ReportType.INCOME -> "Income"
        ReportType.EXPENSES -> "Expenses"
        ReportType.NET_SUMMARY -> "Net business summary"
        ReportType.OUTSTANDING_INVOICES -> "Outstanding invoices"
        ReportType.PAYMENTS_RECEIVED -> "Payments received"
        ReportType.CLIENT_REVENUE -> "Client revenue"
        ReportType.PROJECT_REVENUE -> "Project revenue"
        ReportType.EXPENSE_CATEGORIES -> "Expense categories"
        ReportType.TASK_COMPLETION -> "Task completion"
        ReportType.OVERDUE_TASKS -> "Overdue tasks"
        ReportType.SUBSCRIPTION_COSTS -> "Subscription costs"
        ReportType.PROJECT_STATUS -> "Project status"
    }

    override fun metricLabel(metric: ReportMetric) = when (metric) {
        ReportMetric.INCOME -> "Income"
        ReportMetric.EXPENSES -> "Expenses"
        ReportMetric.NET -> "Net"
        ReportMetric.PAYMENTS_RECEIVED -> "Payments received"
        ReportMetric.OUTSTANDING -> "Outstanding"
        ReportMetric.TASKS_COMPLETED -> "Completed"
        ReportMetric.TASKS_OPENED -> "Opened"
        ReportMetric.TASKS_OVERDUE -> "Overdue"
        ReportMetric.SUBSCRIPTIONS_MONTHLY -> "Per month"
    }

    override fun categoryLabel(category: ExpenseCategory) = when (category) {
        ExpenseCategory.SOFTWARE -> "Software"
        ExpenseCategory.HARDWARE -> "Hardware"
        ExpenseCategory.TRAVEL -> "Travel"
        ExpenseCategory.FOOD -> "Food"
        ExpenseCategory.RENT -> "Rent"
        ExpenseCategory.UTILITIES -> "Utilities"
        ExpenseCategory.MARKETING -> "Marketing"
        ExpenseCategory.PROFESSIONAL_FEES -> "Professional fees"
        ExpenseCategory.TAX -> "Tax"
        ExpenseCategory.OTHER -> "Other"
    }

    override fun statusLabel(status: ProjectStatus) = when (status) {
        ProjectStatus.PLANNED -> "Planned"
        ProjectStatus.ACTIVE -> "Active"
        ProjectStatus.ON_HOLD -> "On hold"
        ProjectStatus.COMPLETED -> "Completed"
        ProjectStatus.CANCELLED -> "Cancelled"
    }

    override fun rangeLabel(from: LocalDate, to: LocalDate): String {
        val month = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
        return if (from.month == to.month && from.year == to.year) {
            "${from.dayOfMonth}–${to.dayOfMonth} ${to.format(month)}"
        } else {
            "${from.dayOfMonth} ${from.format(month)} – ${to.dayOfMonth} ${to.format(month)}"
        }
    }

    override fun money(amount: Money) = "${symbolOf(amount)}${moneyFigure(amount)}"

    override fun number(value: Int) = value.toString()

    override fun items(value: Int) = if (value == 1) "1 item" else "$value items"
}

internal object BengaliReportsWording : ReportsWording {

    override val screenTitle = "রিপোর্ট"
    override val choosePeriod = "সময়কাল"
    override val nothingYet = "এই সময়ের জন্য এখনও কিছু রেকর্ড করা হয়নি।"
    override val mixedCurrencies = "একাধিক মুদ্রা — নিচে আলাদা করে দেখানো হলো।"
    override val exportPdf = "PDF"
    override val exportCsv = "CSV"
    override val exporting = "রিপোর্ট তৈরি হচ্ছে…"
    override val exportReady = "রিপোর্ট এক্সপোর্ট হয়েছে।"
    override val exportFailed = "রিপোর্ট এক্সপোর্ট করা যায়নি।"
    override val rangeApply = "প্রয়োগ করুন"
    override val rangeCancel = "বাতিল"

    override fun periodLabel(period: ReportPeriod) = when (period) {
        ReportPeriod.Today -> "আজ"
        ReportPeriod.ThisWeek -> "এই সপ্তাহ"
        ReportPeriod.ThisMonth -> "এই মাস"
        ReportPeriod.LastMonth -> "গত মাস"
        is ReportPeriod.Custom -> "নির্দিষ্ট সময়"
    }

    override fun reportTitle(type: ReportType) = when (type) {
        ReportType.INCOME -> "আয়"
        ReportType.EXPENSES -> "খরচ"
        ReportType.NET_SUMMARY -> "ব্যবসার সারসংক্ষেপ"
        ReportType.OUTSTANDING_INVOICES -> "বকেয়া ইনভয়েস"
        ReportType.PAYMENTS_RECEIVED -> "প্রাপ্ত পেমেন্ট"
        ReportType.CLIENT_REVENUE -> "ক্লায়েন্ট অনুযায়ী আয়"
        ReportType.PROJECT_REVENUE -> "প্রকল্প অনুযায়ী আয়"
        ReportType.EXPENSE_CATEGORIES -> "খরচের ধরন"
        ReportType.TASK_COMPLETION -> "কাজ সম্পন্ন"
        ReportType.OVERDUE_TASKS -> "সময় পেরোনো কাজ"
        ReportType.SUBSCRIPTION_COSTS -> "সাবস্ক্রিপশন খরচ"
        ReportType.PROJECT_STATUS -> "প্রকল্পের অবস্থা"
    }

    override fun metricLabel(metric: ReportMetric) = when (metric) {
        ReportMetric.INCOME -> "আয়"
        ReportMetric.EXPENSES -> "খরচ"
        ReportMetric.NET -> "নিট"
        ReportMetric.PAYMENTS_RECEIVED -> "প্রাপ্ত পেমেন্ট"
        ReportMetric.OUTSTANDING -> "বকেয়া"
        ReportMetric.TASKS_COMPLETED -> "সম্পন্ন"
        ReportMetric.TASKS_OPENED -> "শুরু হয়েছে"
        ReportMetric.TASKS_OVERDUE -> "সময় পেরিয়েছে"
        ReportMetric.SUBSCRIPTIONS_MONTHLY -> "মাসিক"
    }

    override fun categoryLabel(category: ExpenseCategory) = when (category) {
        ExpenseCategory.SOFTWARE -> "সফটওয়্যার"
        ExpenseCategory.HARDWARE -> "হার্ডওয়্যার"
        ExpenseCategory.TRAVEL -> "ভ্রমণ"
        ExpenseCategory.FOOD -> "খাবার"
        ExpenseCategory.RENT -> "ভাড়া"
        ExpenseCategory.UTILITIES -> "ইউটিলিটি"
        ExpenseCategory.MARKETING -> "মার্কেটিং"
        ExpenseCategory.PROFESSIONAL_FEES -> "পেশাদার ফি"
        ExpenseCategory.TAX -> "ট্যাক্স"
        ExpenseCategory.OTHER -> "অন্যান্য"
    }

    override fun statusLabel(status: ProjectStatus) = when (status) {
        ProjectStatus.PLANNED -> "পরিকল্পিত"
        ProjectStatus.ACTIVE -> "চলমান"
        ProjectStatus.ON_HOLD -> "স্থগিত"
        ProjectStatus.COMPLETED -> "সম্পন্ন"
        ProjectStatus.CANCELLED -> "বাতিল"
    }

    override fun rangeLabel(from: LocalDate, to: LocalDate): String {
        val month = DateTimeFormatter.ofPattern("MMMM", Locale.forLanguageTag("bn"))
        return if (from.month == to.month && from.year == to.year) {
            "${to.format(month)} ${bengaliNumerals(from.dayOfMonth.toString())}–${bengaliNumerals(to.dayOfMonth.toString())}"
        } else {
            "${bengaliNumerals(from.dayOfMonth.toString())} ${from.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("bn"))} – " +
                "${bengaliNumerals(to.dayOfMonth.toString())} ${to.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("bn"))}"
        }
    }

    override fun money(amount: Money) = "${symbolOf(amount)}${bengaliNumerals(moneyFigure(amount))}"

    override fun number(value: Int) = bengaliNumerals(value.toString())

    override fun items(value: Int) = "${bengaliNumerals(value.toString())}টি"
}

/** The taka sign for Abu's own currency, the ISO code for anything else. */
private fun symbolOf(amount: Money): String =
    if (amount.currency.currencyCode == "BDT") "৳" else "${amount.currency.currencyCode} "

internal fun reportsWording(language: SpokenLanguage): ReportsWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliReportsWording
    SpokenLanguage.ENGLISH -> EnglishReportsWording
}
