package com.rayluxeo.aida.ui.navigation

/**
 * The shell in Bengali. Bengali script throughout, so a Bengali hub is not an English tab strip
 * over a Bengali list.
 *
 * Two words stay in Latin on purpose. "BigDecimal" is the name of a type and naming it is the
 * whole point of that line — transliterating it would turn a promise about arithmetic into a word
 * nobody could look up. The area names Abu already uses in English ("ইনভয়েস", "অটোমেশন") are
 * written in Bengali script rather than translated, because that is how he says them.
 *
 * Nothing here counts anything, so no digit crosses into this file.
 */
internal object BengaliShellWording : ShellWording {

    override fun tabLabel(tab: MiraTab): String = when (tab) {
        MiraTab.HOME -> "হোম"
        MiraTab.MIRA -> "আইদা"
        MiraTab.TASKS -> "কাজ"
        MiraTab.VAULT -> "ভল্ট"
    }

    override val back: String = "ফিরে যান"

    override val notBuiltYet: String = "আইদার এই অংশটি এখনও তৈরি হয়নি।"

    override val whatItWillDo: String = "এটি কী করবে"

    override val askMira: String = "বরং আইদাকে জিজ্ঞাসা করুন"

    override val askMiraHint: String =
        "নিজের ভাষায় বলুন। এখনও না পারলে আইদা স্পষ্ট করেই বলে দেবে।"

    override val appLock: AppLockWording = AppLockWording(
        title = "আইদা লক করা আছে",
        subtitle = "এগিয়ে যেতে আঙুলের ছাপ বা স্ক্রিন লক দিয়ে খুলুন।",
        unlock = "খুলুন",
        promptTitle = "আইদা খুলুন",
        promptSubtitle = "আইদা খুলতে নিশ্চিত করুন এটি আপনিই।",
        lockedOut = "অনেকবার চেষ্টা হয়েছে। একটু পরে আবার করুন, বা স্ক্রিন লক ব্যবহার করুন।",
        cannotVerify = "আইদা আপনাকে যাচাই করতে পারেনি। আবার চেষ্টা করুন।",
        notEnrolled = "এই ফোনে এখনও কোনো আঙুলের ছাপ বা স্ক্রিন লক সেট করা নেই।",
        enroll = "স্ক্রিন লক সেট করুন",
    )

    override fun areaLockedTitle(areaName: String) = "$areaName লক করা আছে"

    override fun areaLockedSubtitle(areaName: String) =
        "$areaName খুলতে আপনার আঙুলের ছাপ বা স্ক্রিন লক দিয়ে আনলক করুন।"

    override val backupAreaName = "ব্যাকআপ"

    override fun areaTitle(area: MiraArea): String = when (area) {
        MiraArea.ALARMS -> "অ্যালার্ম"
        MiraArea.TIMERS -> "টাইমার"
        MiraArea.CALENDAR -> "ক্যালেন্ডার"
        MiraArea.SALAH -> "নামাজ"
        MiraArea.NOTES -> "নোট"
        MiraArea.PROJECTS -> "প্রকল্প"
        MiraArea.CLIENTS -> "ক্লায়েন্ট"
        MiraArea.INVOICES -> "ইনভয়েস"
        MiraArea.MONEY -> "টাকা-পয়সা"
        MiraArea.REPORTS -> "রিপোর্ট"
        MiraArea.FILES -> "ফাইল"
        MiraArea.AUTOMATIONS -> "অটোমেশন"
        MiraArea.MEMORY -> "স্মৃতি"
        MiraArea.SETTINGS -> "সেটিংস"
    }

    override fun areaSummary(area: MiraArea): String = when (area) {
        MiraArea.ALARMS -> "যে সময়ে আবুকে জাগাতে হবে, আর কোন দিনগুলোতে।"
        MiraArea.TIMERS -> "কাউন্টডাউন, একসঙ্গে কয়েকটি, ঘড়ি থেকে পড়া।"
        MiraArea.CALENDAR -> "আবুর সূচি, ফোনের নিজের ক্যালেন্ডার থেকে পড়া।"
        MiraArea.SALAH -> "পাঁচ ওয়াক্ত নামাজ, হিসাব সহ — সময় এই ফোনেই গণনা করা।"
        MiraArea.NOTES -> "যা কিছু লিখে রাখার মতো, সেভ করার সঙ্গে সঙ্গেই খোঁজা যায়।"
        MiraArea.PROJECTS -> "চলমান কাজ, আর প্রতিটি অংশ কীসের অপেক্ষায়।"
        MiraArea.CLIENTS -> "আবু যাদের সঙ্গে কাজ করেন, আর প্রতিটি নামের পেছনের ইতিহাস।"
        MiraArea.INVOICES -> "কোটেশন আর ইনভয়েস, হিসাব একেবারে নিখুঁত।"
        MiraArea.MONEY -> "আয়, খরচ আর তার যোগফল — হিসাব করা, আন্দাজ নয়।"
        MiraArea.REPORTS -> "যেকোনো সময়ের আয়, খরচ আর ব্যবসা — চার্ট আর PDF এক্সপোর্টসহ।"
        MiraArea.FILES -> "যে কাগজপত্র আইদা আবার খুঁজে দিতে পারে, ক্যামেরায় স্ক্যান করাগুলোও।"
        MiraArea.AUTOMATIONS -> "আইদা নিজে থেকে যা করে, সময় ধরে বা কোনো ঘটনায়।"
        MiraArea.MEMORY -> "আইদাকে যা মনে রাখতে বলা হয়েছে, তার বাইরে কিছু নয়।"
        MiraArea.SETTINGS -> "ভাষা, কণ্ঠ, রিমাইন্ডার, জেমিনাই আর গোপনীয়তা।"
    }

    override fun areaCapabilities(area: MiraArea): List<String> = when (area) {
        MiraArea.PROJECTS, MiraArea.ALARMS, MiraArea.TIMERS, MiraArea.NOTES, MiraArea.SETTINGS, MiraArea.INVOICES, MiraArea.MONEY, MiraArea.REPORTS, MiraArea.FILES, MiraArea.MEMORY, MiraArea.AUTOMATIONS, MiraArea.CALENDAR, MiraArea.SALAH, MiraArea.CLIENTS -> emptyList()
    }
}
