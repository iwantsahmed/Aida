package com.rayluxeo.aida.ui.licenses

import com.rayluxeo.aida.core.common.SpokenLanguage

/** The groups the acknowledgements screen sorts its dependencies into. */
internal enum class LicenseSection { FRAMEWORK, LANGUAGE, TYPE_AND_ICONS }

/**
 * Every word the acknowledgements screen puts in front of Abu, in one language: the title, the one
 * intro line, the section headers, the "view licence" action and the title of the sheet that shows
 * the bundled font licence in full.
 *
 * The component names and the licence names are deliberately absent: they are proper names
 * ("Jetpack Compose", "SIL Open Font License 1.1") and read the same in either language, so they
 * live beside the dependency list rather than being translated here.
 */
internal interface LicensesWording {

    val screenTitle: String
    val intro: String
    val viewLicense: String
    val fontSheetTitle: String

    fun sectionLabel(section: LicenseSection): String
}

internal object EnglishLicensesWording : LicensesWording {

    override val screenTitle = "Open-source licences"
    override val intro =
        "Mira is built entirely on free, open-source work. These are the projects she stands on, " +
            "and the licences that keep them free to use."
    override val viewLicense = "View licence"
    override val fontSheetTitle = "Noto Serif Bengali licence"

    override fun sectionLabel(section: LicenseSection) = when (section) {
        LicenseSection.FRAMEWORK -> "Framework & libraries"
        LicenseSection.LANGUAGE -> "Language & tooling"
        LicenseSection.TYPE_AND_ICONS -> "Typeface & icons"
    }
}

internal object BengaliLicensesWording : LicensesWording {

    override val screenTitle = "ওপেন-সোর্স লাইসেন্স"
    override val intro =
        "আইদা সম্পূর্ণভাবে বিনামূল্যের, ওপেন-সোর্স কাজের উপর তৈরি। এখানে সেই প্রকল্পগুলো এবং তাদের " +
            "লাইসেন্স দেওয়া হলো যা এগুলো ব্যবহারের স্বাধীনতা ধরে রাখে।"
    override val viewLicense = "লাইসেন্স দেখুন"
    override val fontSheetTitle = "Noto Serif Bengali লাইসেন্স"

    override fun sectionLabel(section: LicenseSection) = when (section) {
        LicenseSection.FRAMEWORK -> "ফ্রেমওয়ার্ক ও লাইব্রেরি"
        LicenseSection.LANGUAGE -> "ভাষা ও টুলিং"
        LicenseSection.TYPE_AND_ICONS -> "টাইপফেস ও আইকন"
    }
}

internal fun licensesWording(language: SpokenLanguage): LicensesWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliLicensesWording
    SpokenLanguage.ENGLISH -> EnglishLicensesWording
}
