package com.rayluxeo.aida.ui.settings

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.domain.command.MiraCapability

/**
 * Every word the permissions screen says, in Abu's language. Each capability is named in his own
 * terms — what it lets Mira do — not by the Android permission string behind it.
 */
internal interface PermissionsWording {
    val title: String
    val back: String
    val intro: String
    val granted: String
    val notGranted: String
    val grant: String
    val manage: String

    fun name(capability: MiraCapability): String
    fun purpose(capability: MiraCapability): String
}

internal object EnglishPermissionsWording : PermissionsWording {
    override val title = "Permissions"
    override val back = "Back"
    override val intro = "What Aida may reach on this phone. Everything here is asked of Android, " +
        "and you can take any of it back at any time."
    override val granted = "Allowed"
    override val notGranted = "Not allowed"
    override val grant = "Allow"
    override val manage = "Manage in settings"

    override fun name(capability: MiraCapability) = when (capability) {
        MiraCapability.MICROPHONE -> "Microphone"
        MiraCapability.CONTACTS -> "Contacts"
        MiraCapability.CALENDAR -> "Calendar"
        MiraCapability.NOTIFICATIONS -> "Notifications"
        MiraCapability.EXACT_ALARMS -> "Exact alarms"
    }

    override fun purpose(capability: MiraCapability) = when (capability) {
        MiraCapability.MICROPHONE -> "To hear a spoken command. Recognition runs on the device."
        MiraCapability.CONTACTS -> "To find a person when you ask her to call or message them."
        MiraCapability.CALENDAR -> "To read your day and add the events you ask for."
        MiraCapability.NOTIFICATIONS -> "To raise a reminder, an alarm or a brief you asked for."
        MiraCapability.EXACT_ALARMS -> "So a reminder fires at the exact minute, not minutes late."
    }
}

internal object BengaliPermissionsWording : PermissionsWording {
    override val title = "অনুমতি"
    override val back = "পেছনে যান"
    override val intro = "এই ফোনে আইদা কী কী ব্যবহার করতে পারবে। সবকিছুই অ্যান্ড্রয়েডের কাছে চাওয়া হয়, " +
        "আর যেকোনো সময় আপনি তা ফিরিয়ে নিতে পারেন।"
    override val granted = "অনুমতি আছে"
    override val notGranted = "অনুমতি নেই"
    override val grant = "অনুমতি দিন"
    override val manage = "সেটিংসে ব্যবস্থাপনা করুন"

    override fun name(capability: MiraCapability) = when (capability) {
        MiraCapability.MICROPHONE -> "মাইক্রোফোন"
        MiraCapability.CONTACTS -> "পরিচিতি"
        MiraCapability.CALENDAR -> "ক্যালেন্ডার"
        MiraCapability.NOTIFICATIONS -> "নোটিফিকেশন"
        MiraCapability.EXACT_ALARMS -> "ঠিক সময়ের অ্যালার্ম"
    }

    override fun purpose(capability: MiraCapability) = when (capability) {
        MiraCapability.MICROPHONE -> "মুখের কমান্ড শুনতে। চিনে নেওয়ার কাজটা ফোনেই হয়।"
        MiraCapability.CONTACTS -> "কল বা মেসেজ করতে বললে মানুষটিকে খুঁজে পেতে।"
        MiraCapability.CALENDAR -> "আপনার দিনটা দেখতে আর আপনি যে ইভেন্ট বলেন তা যোগ করতে।"
        MiraCapability.NOTIFICATIONS -> "আপনার চাওয়া রিমাইন্ডার, অ্যালার্ম বা ব্রিফ দেখাতে।"
        MiraCapability.EXACT_ALARMS -> "যাতে রিমাইন্ডার ঠিক মিনিটে বাজে, কয়েক মিনিট দেরিতে নয়।"
    }
}

internal fun permissionsWording(language: SpokenLanguage): PermissionsWording = when (language) {
    SpokenLanguage.BENGALI -> BengaliPermissionsWording
    SpokenLanguage.ENGLISH -> EnglishPermissionsWording
}
