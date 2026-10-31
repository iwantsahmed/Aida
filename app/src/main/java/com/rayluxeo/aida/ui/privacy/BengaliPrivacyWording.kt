package com.rayluxeo.aida.ui.privacy

/**
 * The dashboard in Bengali. Bengali script throughout, so a Bengali screen is not an English
 * dashboard with Bengali notices bolted on.
 *
 * A few words stay in Latin on purpose. "Gemini" and "NLU" are names of the things themselves —
 * transliterating them would turn a precise claim about which engine runs where into a word Abu
 * could not look up. Nothing here counts anything, so no digit crosses into this file: "100%"
 * becomes "সম্পূর্ণ", the same way the shell's phrasebook keeps numerals out.
 */
internal object BengaliPrivacyWording : PrivacyWording {

    override val dashboardTitle: String = "গোপনীয়তা ও নিরাপত্তা"

    override val offlineArchitectureTitle: String = "অফলাইন-ফার্স্ট গোপনীয়তা কাঠামো"
    override val offlineArchitectureBody: String =
        "আইদা সম্পূর্ণ অফলাইন-ফার্স্ট হিসেবে কাজ করে। সব ডেটাবেস রেকর্ড, নোট, ভল্টের তথ্য আর " +
            "অডিও রেকর্ডিং কেবল এই ডিভাইসেই থাকে।"
    override val cloudDisabledChip: String = "ক্লাউড: বন্ধ"
    override val onDeviceNluChip: String = "ডিভাইসে NLU: সক্রিয়"

    override val cloudAiTitle: String = "ক্লাউড এআই (Gemini)"
    override val geminiEngineTitle: String = "Gemini এআই ইঞ্জিন"
    override val geminiActiveSubtitle: String = "ফ্রি কি দিয়ে সক্রিয়"
    override val geminiOffSubtitle: String = "বন্ধ করা আছে"

    override val appLockTitle: String = "অ্যাপ লক"
    override val appLockRowTitle: String = "আইদা খুলতে আনলক করতে হবে"
    override val appLockOnSubtitle: String = "প্রতিবার খোলার সময় আঙুলের ছাপ বা স্ক্রিন লক"
    override val appLockOffSubtitle: String = "ফোন হাতে পেলে যে কেউ আইদা খুলতে পারবে"

    override val destructiveActionsTitle: String = "তথ্য মুছে ফেলার কাজ"
    override val eraseAllButton: String = "সব স্থানীয় তথ্য মুছুন"
    override val eraseDialogTitle: String = "সব স্থানীয় তথ্য মুছে ফেলবেন?"
    override val eraseDialogMessage: String =
        "এটি এই ডিভাইস থেকে প্রতিটি কাজ, নোট, ভল্ট আইটেম, ইনভয়েস, ভয়েস নোট, স্ক্যান, ফাইল আর " +
            "পছন্দ স্থায়ীভাবে মুছে দেবে। কোনো ব্যাকআপ নেই, আর এটি আর ফেরানো যাবে না।"
    override val eraseDialogConfirm: String = "সব মুছে ফেলুন"
    override val eraseDialogCancel: String = "বাতিল"

    override val geminiDisabledNotice: String = "Gemini এআই বন্ধ করা হয়েছে"
    override val geminiKeyRequiredNotice: String = "সেটিংসে Gemini কি দিতে হবে"
    override val appLockOnNotice: String = "অ্যাপ লক চালু। পরেরবার খোলার সময় আইদা জিজ্ঞাসা করবে।"
    override val appLockOffNotice: String = "অ্যাপ লক বন্ধ"
    override val dataErasedNotice: String = "সব স্থানীয় তথ্য মুছে ফেলা হয়েছে"
    override val eraseFailedNotice: String = "স্থানীয় তথ্য মোছা শেষ করা যায়নি।"
}
