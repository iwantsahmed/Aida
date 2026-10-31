package com.rayluxeo.aida.ui.vault

import com.rayluxeo.aida.domain.model.VaultCategory

/**
 * The vault in Bengali.
 *
 * Nothing here is a format string with a value baked into it: [deleteTitle] takes the entry's own
 * title, which is Abu's text and stays exactly as he wrote it, whichever script that is in.
 */
internal object BengaliVaultWording : VaultWording {

    override val vaultTitle: String = "ভল্ট"

    override val locked: String = "বন্ধ"

    override val unlocked: String = "খোলা"

    override val addEntry: String = "নতুন এন্ট্রি"

    override val dismiss: String = "সরান"

    override val lockedHeadline: String = "ভল্ট বন্ধ আছে।"

    override val lockedBody: String =
        "আঙুলের ছাপ, মুখ বা ফোনের পিন দিয়ে না খোলা পর্যন্ত এখানকার কিছুই দেখানো হবে না।"

    override val unlock: String = "খুলুন"

    override val unlocking: String = "আপনার অপেক্ষায়"

    override val setUpScreenLock: String = "স্ক্রিন লক সেট করুন"

    override val promptTitle: String = "ভল্ট খুলুন"

    override val promptSubtitle: String = "এখানকার সবকিছু এই ফোনেই থাকে।"

    override val nothingStored: String =
        "এখনো কিছু রাখা হয়নি। এখানে যা রাখবেন তা এই ফোনেই এনক্রিপ্ট হয়ে থাকে, কোথাও যায় না।"

    override val addFirstEntry: String = "একটি যোগ করুন"

    override val copy: String = "কপি"

    override val delete: String = "মুছুন"

    override val keepIt: String = "থাক"

    override fun deleteTitle(title: String): String = "\"$title\" মুছে ফেলবেন?"

    override val deleteMessage: String =
        "এনক্রিপ্ট করা এন্ট্রিটি এই ফোন থেকে মুছে যাবে। কোনো ব্যাকআপ নেই, ফেরানোও যাবে না।"

    override val newEntry: String = "নতুন ভল্ট এন্ট্রি"

    override val entryTitleLabel: String = "শিরোনাম"

    override val entryTitlePlaceholder: String = "জিমেইল রিকভারি কোড"

    override val entryTitleNote: String =
        "শিরোনামটি খোলা অবস্থায় রাখা হয়, যাতে ভল্ট না খুলেই তালিকা পড়া যায়। গোপন তথ্যটি নিচের " +
            "ঘরে রাখুন।"

    override val secretLabel: String = "গোপন তথ্য"

    override val categoryLabel: String = "শ্রেণি"

    override val save: String = "ভল্টে সংরক্ষণ"

    override val saving: String = "সংরক্ষণ হচ্ছে"

    override fun category(category: VaultCategory): String = when (category) {
        VaultCategory.NOTE -> "নোট"
        VaultCategory.CREDENTIAL -> "পরিচয়পত্র"
        VaultCategory.DOCUMENT_REF -> "নথি"
        VaultCategory.FINANCIAL -> "আর্থিক"
        VaultCategory.FILE -> "ফাইল"
    }

    override fun lockReason(reason: VaultLockReason): String = when (reason) {
        VaultLockReason.NOT_RECOGNISED -> "চেনা গেল না। আবার চেষ্টা করুন, বা পিন ব্যবহার করুন।"
        VaultLockReason.CANCELLED -> "ভল্ট বন্ধই রইল।"
        VaultLockReason.LOCKED_OUT ->
            "অনেকবার চেষ্টা হয়েছে। একটু অপেক্ষা করুন, বা ফোনের পিন দিয়ে খুলুন।"
        VaultLockReason.NONE_ENROLLED ->
            "এই ফোনে আঙুলের ছাপ, মুখ বা পিন কিছুই সেট করা নেই। এর একটি না থাকলে ভল্ট খুলবে না।"
        VaultLockReason.NO_WINDOW -> "ভল্ট কেবল আইদার নিজের উইন্ডো থেকেই খোলা যায়।"
        VaultLockReason.SENSOR_BUSY -> "আঙুলের ছাপের সেন্সরটি ব্যস্ত — একটু পরে আবার চেষ্টা করুন।"
        VaultLockReason.CANNOT_VERIFY -> "এই ফোন এখন আপনাকে যাচাই করতে পারছে না।"
        VaultLockReason.ENROLMENT_UNAVAILABLE ->
            "আইদা স্ক্রিন লকের সেটিংস খুলতে পারেনি। সিস্টেম সেটিংস থেকে লক সেট করে ফিরে আসুন।"
    }

    override fun problem(problem: VaultProblem): String = when (problem) {
        VaultProblem.CANNOT_DECRYPT ->
            "এই এন্ট্রিটি ডিক্রিপ্ট করা গেল না। এটি এমন একটি কী দিয়ে লেখা, যা এই ফোনে আর নেই।"
        VaultProblem.GONE -> "এই এন্ট্রিটি আর ভল্টে নেই।"
        VaultProblem.INCOMPLETE -> "শিরোনাম ও গোপন তথ্য — দুটোই দরকার।"
        VaultProblem.STORAGE -> "ভল্টে লেখা গেল না। আরেকবার চেষ্টা করুন।"
        VaultProblem.UNKNOWN -> "ভল্টে কিছু একটা ভুল হয়েছে। আবার চেষ্টা করুন।"
    }
}
