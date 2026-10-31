package com.rayluxeo.aida.ai.nlu

import java.time.DayOfWeek

/**
 * The tokens the rest of the NLU pipeline is allowed to match on.
 *
 * They are spelled as English words only because something had to be picked; they are
 * language-neutral identifiers. "kal", "আগামীকাল" and "tomorrow" all arrive here as
 * [TOMORROW], which is what lets one lexicon serve four ways of writing the same request.
 *
 * Because every constant is spelled as the ordinary English word, plain English is already
 * canonical and needs no table entry at all. Only a form that differs — "reminder", "add",
 * "lac" — has to be listed.
 */
object CanonicalToken {

    // ------------------------------------------------------------------ when

    const val TODAY = "today"
    const val TOMORROW = "tomorrow"
    const val DAY_AFTER = "day_after"
    const val TONIGHT = "tonight"

    const val NEXT = "next"
    const val THIS = "this"
    const val WEEK = "week"
    const val MONTH = "month"

    /** The "5 তারিখ" marker: the number in front of it is a day of the month, not an hour. */
    const val DATE = "date"

    const val MORNING = "morning"
    const val NOON = "noon"
    const val AFTERNOON = "afternoon"
    const val EVENING = "evening"
    const val NIGHT = "night"

    const val HOUR = "hour"
    const val MINUTE = "minute"
    const val LATER = "later"

    // ----------------------------------------------------------------- money

    const val TAKA = "taka"
    const val THOUSAND = "thousand"
    const val LAKH = "lakh"
    const val CRORE = "crore"
    const val MILLION = "million"

    /** Bengali says half, one-and-a-half and two-and-a-half as single words. */
    const val HALF = "half"
    const val ONE_AND_HALF = "one_and_half"
    const val TWO_AND_HALF = "two_and_half"

    // --------------------------------------------------------------- grammar

    const val AT = "at"
    const val TO = "to"
    const val DO = "do"
    const val GIVE = "give"
    const val ME = "me"
    const val MY = "my"

    // ------------------------------------------------------------- the verbs

    const val CREATE = "create"
    const val UPDATE = "update"
    const val REMIND = "remind"

    /** Storing a fact for later, as against [REMIND], which fires at a moment. */
    const val REMEMBER = "remember"
    const val SEARCH = "search"
    const val CALCULATE = "calculate"
    const val DONE = "done"
    const val BRIEF = "brief"

    // ------------------------------------------------- what the request is about

    const val TASK = "task"
    const val NOTE = "note"
    const val MEMORY = "memory"
    const val CLIENT = "client"
    const val INVOICE = "invoice"
    const val PAYMENT = "payment"
    const val EXPENSE = "expense"
    const val INCOME = "income"
    const val ALARM = "alarm"
    const val TIMER = "timer"
    const val CALENDAR = "calendar"
    const val MEETING = "meeting"
    const val CONTACT = "contact"
    const val CALL = "call"
    const val MESSAGE = "message"

    // ----------------------------------------------------------------- salah

    /** The generic word for an obligatory prayer — "namaz", "salah". */
    const val NAMAZ = "namaz"
    const val FAJR = "fajr"
    const val DHUHR = "dhuhr"
    const val ASR = "asr"
    const val MAGHRIB = "maghrib"
    const val ISHA = "isha"
    const val TAHAJJUD = "tahajjud"

    /** The verb for having prayed — "prayed", "porechi", "adai". */
    const val PRAYED = "prayed"

    // -------------------------------------------------------------- weekdays

    const val MONDAY = "monday"
    const val TUESDAY = "tuesday"
    const val WEDNESDAY = "wednesday"
    const val THURSDAY = "thursday"
    const val FRIDAY = "friday"
    const val SATURDAY = "saturday"
    const val SUNDAY = "sunday"
}

/**
 * One canonical token and every surface form Abu might type for it.
 *
 * [variants] and [english] both fold to [canonical]; they are separate lists because only
 * [variants] is evidence that Abu is writing Bengali. "Create a note for the task tomorrow"
 * is an English sentence, and counting its ordinary English words as Bengali would answer it
 * in the wrong language. [variants] alone also seeds the consonant-skeleton fold in
 * [VocabularyIndex], so no English spelling can claim a skeleton a Bengali word needs.
 */
data class VocabularyEntry(
    val canonical: String,
    /** Bengali-script and Banglish spellings. Matched exactly, and folded fuzzily by skeleton. */
    val variants: List<String> = emptyList(),
    /** English words that differ from [canonical]. Matched exactly, never fuzzily. */
    val english: List<String> = emptyList(),
    /**
     * True for suffixes that only mean what they say when they follow a number. Bengali "ta"
     * is the hour marker in "10 ta" and the definite article in "kaj ta"; reading the second
     * one as a time would turn "the task" into "at the task". "50 k" is the same shape.
     */
    val requiresNumericPredecessor: Boolean = false,
    /**
     * True for words that only mean a quantity when a quantity word follows them. "der lakh"
     * is 150,000, but a bare "der" is the possessive plural ending Abu types as its own word.
     */
    val requiresQuantitySuccessor: Boolean = false,
)

/**
 * The Banglish and Bengali vocabulary, as a table rather than a pile of when-branches so
 * that adding a spelling is a one-line change Abu's future self can make without reading code.
 *
 * Phrases come first and are matched before the table is consulted word by word, because
 * "mone koriye" only means "remind" as a pair — "mone" alone means "in mind".
 */
object Vocabulary {

    /** Multi-word forms, matched against the whitespace-joined token stream before single tokens. */
    val phrases: List<VocabularyEntry> = listOf(
        // "মনে করিও" is the short imperative Abu actually says, and it is a different word from
        // "মনে করিয়ে" rather than a spelling of it: the causative loses its "য়ে" and no fuzzy
        // fold can reach it, because Bengali script yields no consonant skeleton at all.
        VocabularyEntry(
            CanonicalToken.REMIND,
            listOf(
                "mone koriye",
                "mone korie",
                "mone koriye dao",
                "mone korio",
                "mone koriyo",
                "mone korabe",
                "মনে করিয়ে",
                "মনে করিয়ে দিও",
                "মনে করিয়ে দাও",
                "মনে করিও",
                "মনে করিয়ো",
                "মনে করাবে",
            ),
        ),
        VocabularyEntry(
            CanonicalToken.REMEMBER,
            listOf(
                "mone rakho",
                "mone rekho",
                "mone rakhen",
                "মনে রাখো",
                "মনে রেখো",
            ),
        ),
        VocabularyEntry(
            CanonicalToken.DONE,
            listOf(
                "hoye geche",
                "hoye gache",
                "hoye gelo",
                "হয়ে গেছে",
                "শেষ হয়েছে",
            ),
        ),
        VocabularyEntry(
            CanonicalToken.DAY_AFTER,
            variants = listOf("porshu din", "পরশু দিন"),
            english = listOf("day after tomorrow"),
        ),
        // "আগামীকাল" written with a space is still one word; without this it would read as
        // "next tomorrow" and land a day late.
        VocabularyEntry(
            CanonicalToken.TOMORROW,
            listOf("agami kal", "agami kaal", "আগামী কাল"),
        ),
        VocabularyEntry(
            CanonicalToken.TONIGHT,
            listOf("aj rate", "aj raat", "ajke raat", "ajke rate", "আজ রাতে", "আজকে রাতে"),
        ),
        VocabularyEntry(
            CanonicalToken.BRIEF,
            variants = listOf("ajker plan", "aajker plan", "আজকের প্ল্যান", "দিনের সারাংশ"),
            english = listOf("daily brief", "days brief"),
        ),
        VocabularyEntry(
            CanonicalToken.CREATE,
            listOf("toiri koro", "toiri kore", "তৈরি করো"),
        ),
    )

    /** Single-token forms. */
    val words: List<VocabularyEntry> = listOf(
        // Relative days. Bengali "kal" is both yesterday and tomorrow; in a reminder or task
        // the future reading is the only sensible one, and v0.1 records nothing about the past,
        // so the backward reading is deliberately out of scope here.
        VocabularyEntry(
            CanonicalToken.TOMORROW,
            listOf(
                "kal", "kaal", "kall", "kalke", "kaalke", "agamikal", "agamikaal", "agamical",
                "আগামীকাল", "কাল", "কালকে",
            ),
        ),
        VocabularyEntry(
            CanonicalToken.TODAY,
            listOf("aaj", "aj", "ajke", "aajke", "ajker", "aajker", "আজ", "আজকে", "আজকের"),
        ),
        VocabularyEntry(
            CanonicalToken.DAY_AFTER,
            listOf("porshu", "porsu", "parshu", "পরশু", "পরশুদিন"),
        ),
        VocabularyEntry(
            CanonicalToken.TONIGHT,
            listOf("aajrate", "ajrate", "আজরাতে"),
        ),

        // Larger spans, for "আগামী সপ্তাহ", "আগামী মাস" and "মাসের ৫ তারিখ".
        VocabularyEntry(
            CanonicalToken.NEXT,
            listOf("agami", "agamee", "aagami", "samner", "আগামী", "সামনের"),
        ),
        VocabularyEntry(
            CanonicalToken.THIS,
            listOf("ei", "এই"),
        ),
        VocabularyEntry(
            CanonicalToken.WEEK,
            variants = listOf("soptah", "sopta", "shoptah", "saptah", "সপ্তাহ", "সপ্তাহে", "সপ্তাহের"),
            english = listOf("weeks"),
        ),
        VocabularyEntry(
            CanonicalToken.MONTH,
            variants = listOf("mash", "mas", "mase", "mashe", "masher", "maser", "মাস", "মাসে", "মাসের"),
            english = listOf("months"),
        ),
        VocabularyEntry(
            CanonicalToken.DATE,
            listOf("tarik", "tarikh", "tarikhe", "তারিখ", "তারিখে"),
        ),

        // Day parts.
        VocabularyEntry(
            CanonicalToken.MORNING,
            listOf("shokal", "sokal", "skal", "shokale", "sokale", "সকাল", "সকালে", "ভোর"),
        ),
        VocabularyEntry(
            CanonicalToken.NOON,
            listOf("dupur", "dupure", "dupor", "দুপুর", "দুপুরে"),
        ),
        VocabularyEntry(
            CanonicalToken.AFTERNOON,
            listOf("bikel", "bikal", "bikele", "bikale", "বিকেল", "বিকাল", "বিকেলে"),
        ),
        VocabularyEntry(
            CanonicalToken.EVENING,
            listOf("shondha", "sondha", "sondhya", "shondhya", "shondhay", "সন্ধ্যা", "সন্ধ্যায়"),
        ),
        VocabularyEntry(
            CanonicalToken.NIGHT,
            listOf("raat", "rat", "raate", "rate", "রাত", "রাতে"),
        ),

        // Durations.
        VocabularyEntry(
            CanonicalToken.HOUR,
            listOf("ghonta", "ghanta", "ghontay", "ghontar", "ঘন্টা", "ঘণ্টা", "ঘন্টায়"),
        ),
        VocabularyEntry(
            CanonicalToken.MINUTE,
            listOf("minit", "minite", "মিনিট", "মিনিটে"),
        ),
        VocabularyEntry(
            CanonicalToken.LATER,
            listOf("pore", "por", "porey", "পরে", "পর"),
        ),

        // Money. The multipliers are the load-bearing part: Bengali counts in লাখ and কোটি,
        // and "der lakh" is a number Abu says far more often than "one hundred fifty thousand".
        VocabularyEntry(
            CanonicalToken.TAKA,
            variants = listOf("taka", "takar", "takay", "tk", "৳", "টাকা", "টাকার", "টাকায়"),
            english = listOf("bdt"),
        ),
        VocabularyEntry(
            CanonicalToken.THOUSAND,
            listOf("hajar", "hazar", "hajare", "hazaar", "হাজার", "হাজারে"),
        ),
        // "50k" is how Abu writes it in either language, so it argues for neither.
        VocabularyEntry(
            CanonicalToken.THOUSAND,
            english = listOf("k"),
            requiresNumericPredecessor = true,
        ),
        VocabularyEntry(
            CanonicalToken.LAKH,
            variants = listOf("lakh", "lakkho", "লাখ", "লাখে", "লক্ষ"),
            english = listOf("lac", "lacs", "lakhs"),
        ),
        VocabularyEntry(
            CanonicalToken.CRORE,
            variants = listOf("koti", "kuti", "কোটি"),
            english = listOf("crore", "crores"),
        ),
        VocabularyEntry(
            CanonicalToken.MILLION,
            variants = listOf("মিলিয়ন"),
            english = listOf("millions"),
        ),
        VocabularyEntry(
            CanonicalToken.HALF,
            listOf("adha", "adhek", "ordhek", "আধা", "আধ", "অর্ধেক"),
        ),
        VocabularyEntry(
            CanonicalToken.ONE_AND_HALF,
            listOf("dedh", "দেড়"),
        ),
        VocabularyEntry(
            CanonicalToken.ONE_AND_HALF,
            listOf("der", "deor"),
            requiresQuantitySuccessor = true,
        ),
        VocabularyEntry(
            CanonicalToken.TWO_AND_HALF,
            listOf("arai", "aarai", "আড়াই"),
        ),

        // Function words. Bengali marks the hour with a "-ta/-tay" suffix and the object with
        // "ke"; folding them to "at" and "to" lets one set of patterns read both languages.
        VocabularyEntry(
            CanonicalToken.AT,
            listOf("tay", "tey", "টায়"),
        ),
        VocabularyEntry(
            CanonicalToken.AT,
            listOf("ta", "te", "tar", "টা", "টার", "তে"),
            requiresNumericPredecessor = true,
        ),
        VocabularyEntry(
            CanonicalToken.TO,
            listOf("ke", "kake", "কে", "রে"),
        ),
        VocabularyEntry(
            CanonicalToken.DO,
            // "করে" earns its place: the Banglish "kore" already folds here and is then dropped as
            // a request word, so without its Bengali twin "নোট করে রাখো" keeps a verb that
            // "note kore rakho" loses. Mira must read both scripts the same way.
            listOf(
                "korar", "kora", "korte", "korbo", "kore",
                "করার", "করা", "করতে", "করবো", "করে",
            ),
        ),
        VocabularyEntry(
            CanonicalToken.GIVE,
            listOf("dao", "dio", "den", "deben", "dis", "দাও", "দিও", "দেন", "দেবেন"),
        ),
        VocabularyEntry(
            CanonicalToken.ME,
            listOf("amake", "আমাকে"),
        ),
        VocabularyEntry(
            CanonicalToken.MY,
            listOf("amar", "amader", "আমার"),
        ),

        // Intent-bearing words.
        VocabularyEntry(
            CanonicalToken.CREATE,
            variants = listOf("banao", "banaw", "banai", "toiri", "notun", "বানাও", "তৈরি", "নতুন"),
            english = listOf("add", "new", "make"),
        ),
        VocabularyEntry(
            CanonicalToken.UPDATE,
            variants = listOf("bodlao", "bodlai", "paltao", "poriborton", "বদলাও", "পাল্টাও", "পরিবর্তন"),
            english = listOf("change", "edit", "modify", "rename"),
        ),
        VocabularyEntry(
            CanonicalToken.REMIND,
            variants = listOf("riminder", "rimindar", "rimaindar", "রিমাইন্ডার"),
            english = listOf("reminder", "reminders"),
        ),
        VocabularyEntry(
            CanonicalToken.TASK,
            variants = listOf("kaj", "kaaj", "kajta", "কাজ", "কাজটা", "কাজগুলো"),
            english = listOf("tasks", "todo", "todos"),
        ),
        VocabularyEntry(
            CanonicalToken.SEARCH,
            variants = listOf("khojo", "khuje", "khoj", "khujo", "খুঁজে", "খোঁজো", "খুঁজো", "খোঁজ"),
            english = listOf("find"),
        ),
        VocabularyEntry(
            CanonicalToken.CALCULATE,
            listOf("hisab", "hishab", "hiseb", "hisheb", "koto", "kato", "হিসাব", "হিসেব", "কত"),
        ),
        VocabularyEntry(
            CanonicalToken.DONE,
            listOf("sesh", "shesh", "hoyeche", "hoyche", "শেষ", "হয়েছে"),
        ),

        // What a request is about. Bengali borrows most of these words from English and writes
        // them in Bengali script, so the script spellings matter more than the Banglish ones.
        VocabularyEntry(
            CanonicalToken.NOTE,
            variants = listOf("নোট", "নোটটা", "নোটে"),
            english = listOf("notes"),
        ),
        VocabularyEntry(
            CanonicalToken.MEMORY,
            variants = listOf("মেমরি", "স্মৃতি"),
            english = listOf("memories"),
        ),
        VocabularyEntry(
            CanonicalToken.CLIENT,
            variants = listOf("klaent", "khodder", "ক্লায়েন্ট", "ক্লায়েন্টের", "খদ্দের"),
            english = listOf("clients", "customer", "customers"),
        ),
        VocabularyEntry(
            CanonicalToken.INVOICE,
            variants = listOf("ইনভয়েস", "চালান"),
            english = listOf("invoices"),
        ),
        VocabularyEntry(
            CanonicalToken.PAYMENT,
            variants = listOf("peyment", "poyment", "পেমেন্ট", "পরিশোধ"),
            english = listOf("payments", "paid"),
        ),
        VocabularyEntry(
            CanonicalToken.EXPENSE,
            variants = listOf("khoroch", "khorocha", "khorch", "খরচ", "খরচা"),
            english = listOf("expenses", "spend", "spent", "cost"),
        ),
        VocabularyEntry(
            CanonicalToken.INCOME,
            variants = listOf("ay", "rojgar", "upojon", "আয়", "রোজগার", "উপার্জন"),
            english = listOf("earned", "earning", "revenue"),
        ),
        VocabularyEntry(
            CanonicalToken.ALARM,
            variants = listOf("elarm", "alaram", "এলার্ম", "অ্যালার্ম"),
            english = listOf("alarms"),
        ),
        VocabularyEntry(
            CanonicalToken.TIMER,
            variants = listOf("টাইমার"),
            english = listOf("timers"),
        ),
        VocabularyEntry(
            CanonicalToken.CALENDAR,
            variants = listOf("kalendar", "ক্যালেন্ডার", "ক্যালেন্ডারে"),
            english = listOf("calendars"),
        ),
        VocabularyEntry(
            CanonicalToken.MEETING,
            variants = listOf("miting", "meting", "boithok", "মিটিং", "মিটিংয়ে", "বৈঠক"),
            english = listOf("meetings"),
        ),
        VocabularyEntry(
            CanonicalToken.CONTACT,
            variants = listOf("kontakt", "কন্টাক্ট"),
            english = listOf("contacts"),
        ),
        VocabularyEntry(
            CanonicalToken.CALL,
            variants = listOf("কল", "কলের"),
            english = listOf("calls"),
        ),
        VocabularyEntry(
            CanonicalToken.MESSAGE,
            variants = listOf("mesej", "মেসেজ", "মেসেজে", "খুদেবার্তা"),
            english = listOf("messages", "sms", "msg"),
        ),

        // Salah. The generic prayer word, the five prayers by name, Tahajjud, and the verb for
        // having prayed. English "prayer"/"prayers" fold onto the generic word too, so "all five
        // prayers" reads the same as "panch waqt namaz". Dhuhr deliberately does NOT list "দুপুর"
        // or "dupur": those are already NOON (a clock reading Abu uses far more often than the
        // prayer), and a prayer context turns the noon day-part into Dhuhr in the slot parser
        // instead, so the one surface form keeps its one meaning (§7.12).
        VocabularyEntry(
            CanonicalToken.NAMAZ,
            variants = listOf("namaz", "namaj", "namaaz", "salah", "নামাজ", "নামাজের", "নামাজে", "নামায", "সালাত", "সালাহ"),
            english = listOf("prayer", "prayers"),
        ),
        VocabularyEntry(
            CanonicalToken.FAJR,
            variants = listOf("fajr", "fojor", "fozor", "fazar", "ফজর", "ফজরের", "ফজরে"),
        ),
        VocabularyEntry(
            CanonicalToken.DHUHR,
            variants = listOf("dhuhr", "zuhr", "zohor", "johor", "juhr", "zohr", "যোহর", "জোহর", "যুহর", "জুহর"),
        ),
        VocabularyEntry(
            CanonicalToken.ASR,
            variants = listOf("asr", "asor", "আসর", "আসরের", "আসরে"),
        ),
        VocabularyEntry(
            CanonicalToken.MAGHRIB,
            variants = listOf("maghrib", "magrib", "মাগরিব", "মাগ্রিব", "মাগরিবের"),
        ),
        VocabularyEntry(
            CanonicalToken.ISHA,
            variants = listOf("isha", "esha", "এশা", "এশার"),
        ),
        VocabularyEntry(
            CanonicalToken.TAHAJJUD,
            variants = listOf("tahajjud", "tahajjod", "তাহাজ্জুদ", "তাহাজ্জুদের"),
        ),
        VocabularyEntry(
            CanonicalToken.PRAYED,
            variants = listOf("porechi", "poreci", "poreichi", "porlam", "adai", "পড়েছি", "পড়লাম", "আদায়"),
        ),

        // Weekdays.
        VocabularyEntry(CanonicalToken.SATURDAY, listOf("shonibar", "sonibar", "শনিবার")),
        VocabularyEntry(CanonicalToken.SUNDAY, listOf("robibar", "rabibar", "রবিবার")),
        VocabularyEntry(CanonicalToken.MONDAY, listOf("shombar", "sombar", "somobar", "সোমবার")),
        VocabularyEntry(CanonicalToken.TUESDAY, listOf("mongolbar", "mangalbar", "মঙ্গলবার")),
        VocabularyEntry(CanonicalToken.WEDNESDAY, listOf("budhbar", "budbar", "বুধবার")),
        VocabularyEntry(
            CanonicalToken.THURSDAY,
            listOf("brihoshpotibar", "brihospotibar", "bishudbar", "বৃহস্পতিবার"),
        ),
        VocabularyEntry(CanonicalToken.FRIDAY, listOf("shukrobar", "sukrobar", "jumabar", "শুক্রবার")),
    )

    /**
     * English words the consonant-skeleton fold must not touch.
     *
     * A consonant frame is a coarse thing: "send" and "shondha" both reduce to "snd", "skill"
     * and "shokal" to "skl", "nothing" and "notun" to "ntn". These are the collisions that
     * running an English dictionary through [VocabularyIndex.skeletonOf] turns up among words
     * Abu would plausibly type. Each one is listed here rather than giving up the family it
     * collides with, because the fold is what catches the spellings nobody has thought of yet.
     *
     * A word only needs to be here if some Bengali spelling claims its frame — an English word
     * that collides with nothing is left alone anyway, and a canonical token is never re-folded.
     */
    val englishShield: Set<String> = setOf(
        "badly",
        "giant",
        "meant",
        "measure",
        "mint",
        "miser",
        "motion",
        "mount",
        "nation",
        "nothing",
        "notion",
        "parse",
        "pilot",
        "plate",
        "plot",
        "polite",
        "praise",
        "press",
        "purse",
        "sand",
        "send",
        "seminar",
        "skill",
        "sound",
        "spit",
        "spite",
        "spot",
        "spout",
        "trek",
    )

    /**
     * Which weekday each canonical token names, and every spelling that reaches it.
     *
     * Derived from [words] rather than written out a second time. Two parsers read a weekday —
     * [RelativeDateParser] to answer "which Friday", [RecurrenceExpressionParser] to answer "how
     * often" — and AGENTS.md §7.12 records what happens when one grammar is kept in two
     * hand-maintained lists: the narrower copy drifts and then answers confidently with the
     * wrong thing.
     */
    val weekdays: Map<String, DayOfWeek> = buildMap {
        val named = mapOf(
            CanonicalToken.MONDAY to DayOfWeek.MONDAY,
            CanonicalToken.TUESDAY to DayOfWeek.TUESDAY,
            CanonicalToken.WEDNESDAY to DayOfWeek.WEDNESDAY,
            CanonicalToken.THURSDAY to DayOfWeek.THURSDAY,
            CanonicalToken.FRIDAY to DayOfWeek.FRIDAY,
            CanonicalToken.SATURDAY to DayOfWeek.SATURDAY,
            CanonicalToken.SUNDAY to DayOfWeek.SUNDAY,
        )
        for (entry in words) {
            val day = named[entry.canonical] ?: continue
            put(entry.canonical, day)
            for (form in entry.variants + entry.english) put(form, day)
        }
    }

    /**
     * Every single-token spelling that folds to [canonical], the canonical token included.
     *
     * A parser that needs one of these families — the week words, the month words — reads it
     * from here instead of keeping a copy. Same rule as [weekdays], same reason.
     */
    fun formsOf(canonical: String): Set<String> = forms[canonical] ?: setOf(canonical)

    private val forms: Map<String, Set<String>> = buildMap<String, MutableSet<String>> {
        for (entry in words) {
            val family = getOrPut(entry.canonical) { mutableSetOf(entry.canonical) }
            family += entry.variants
            family += entry.english
        }
    }
}

/** One token after folding, plus whether a Bengali or Banglish form is what produced it. */
internal data class FoldedToken(
    val token: String,
    /**
     * True only for a [VocabularyEntry.variants] match. An English synonym folding to the same
     * canonical token says nothing about which language Abu is writing in.
     */
    val bengaliEvidence: Boolean,
)

/**
 * [Vocabulary] turned into the lookups the pipeline actually reads, built once.
 *
 * It is one object rather than a copy inside each caller because the fold and the language
 * decision have to agree about what counts as a Bengali word — AGENTS.md §7.12 records what
 * happened the last time two hand-maintained copies of one grammar drifted apart.
 *
 * Romanized Bengali has no orthography — Abu may write "shokal", "sokal" or "skal" in three
 * consecutive sentences and mean the same thing — so matching listed spellings exactly is not
 * enough. Anything the table misses is folded a second way, by consonant skeleton: vowels
 * carry almost none of the identity of a romanized Bengali word, while the consonant frame
 * survives every spelling. "shokal", "sokal" and "skal" all reduce to "skl".
 *
 * The skeleton fold is deliberately conservative. A skeleton claimed by two different
 * canonical tokens (as "kaj" and "khuje" both claim "kj") is dropped rather than guessed at,
 * a frame shorter than [MIN_SKELETON] is not used at all — "lakh" and "like" share one — and
 * the caller can protect tokens, names typically, from the fold entirely.
 */
internal object VocabularyIndex {

    /**
     * Folds a whole token stream. [protectedTokens] are exempt from the skeleton fold, which is
     * how "Ahmed" survives a sentence full of Bengali; they are still matched against the table.
     */
    fun fold(tokens: List<String>, protectedTokens: Set<String> = emptySet()): List<FoldedToken> {
        val folded = mutableListOf<FoldedToken>()
        var index = 0
        while (index < tokens.size) {
            val phrase = matchPhrase(tokens, index)
            if (phrase != null) {
                folded += phrase.folded
                index += phrase.length
                continue
            }
            folded += foldWord(
                token = tokens[index],
                previous = tokens.getOrNull(index - 1),
                next = tokens.getOrNull(index + 1),
                protectedTokens = protectedTokens,
            )
            index++
        }
        return folded
    }

    /**
     * Folds one token with no sentence around it. Forms that need a neighbour to mean anything —
     * the "ta" hour marker, the "k" of "50 k", the "der" of "der lakh" — are left alone here,
     * because out of context they are as likely to be a particle as a number.
     */
    fun foldOne(token: String): String =
        EXACT_INDEX[token] ?: ENGLISH_INDEX[token] ?: FAMILY_INDEX[skeletonOf(token)] ?: token

    /**
     * The consonant frame of a romanized word: digraphs folded to one letter, repeated letters
     * collapsed, then the first character plus every remaining consonant. Bengali-script input
     * has no Latin consonants and correctly yields an empty skeleton — script that is already
     * unambiguous does not need fuzzy matching.
     */
    fun skeletonOf(token: String): String {
        val letters = token.lowercase().filter { it in 'a'..'z' }
        if (letters.isEmpty()) return ""
        val collapsed = collapseRepeats(foldDigraphs(letters))
        return buildString(collapsed.length) {
            append(collapsed[0])
            for (position in 1 until collapsed.length) {
                val character = collapsed[position]
                if (character !in VOWELS) append(character)
            }
        }
    }

    private fun foldWord(
        token: String,
        previous: String?,
        next: String?,
        protectedTokens: Set<String>,
    ): FoldedToken {
        NUMERIC_SUFFIX_INDEX[token]?.let { folded ->
            // Left alone rather than fuzzily matched: an article that is not marking an hour is
            // still a particle, and the title builder knows to drop it.
            return if (isNumber(previous)) folded else FoldedToken(token, bengaliEvidence = false)
        }
        QUANTITY_PREFIX_INDEX[token]?.let { folded ->
            return if (isQuantity(next)) folded else FoldedToken(token, bengaliEvidence = false)
        }
        EXACT_INDEX[token]?.let { return FoldedToken(it, bengaliEvidence = true) }
        ENGLISH_INDEX[token]?.let { return FoldedToken(it, bengaliEvidence = false) }
        if (token in protectedTokens || token in Vocabulary.englishShield) {
            return FoldedToken(token, bengaliEvidence = false)
        }
        val family = FAMILY_INDEX[skeletonOf(token)]
        return if (family != null) {
            FoldedToken(family, bengaliEvidence = true)
        } else {
            FoldedToken(token, bengaliEvidence = false)
        }
    }

    private fun matchPhrase(tokens: List<String>, start: Int): PhraseMatch? {
        val longest = minOf(LONGEST_PHRASE, tokens.size - start)
        for (length in longest downTo 2) {
            val candidate = tokens.subList(start, start + length).joinToString(separator = " ")
            val folded = PHRASE_INDEX[candidate] ?: continue
            return PhraseMatch(folded, length)
        }
        return null
    }

    /** A clock reading and a decimal are both numbers here: "10:30 tay", "1.5 lakh". */
    private fun isNumber(token: String?): Boolean =
        !token.isNullOrEmpty() &&
            token.any(BengaliNumerals::isAsciiDigit) &&
            token.all { BengaliNumerals.isAsciiDigit(it) || it == '.' || it == ':' }

    private fun isQuantity(token: String?): Boolean =
        token != null && foldOne(token) in QUANTITY_TOKENS

    private data class PhraseMatch(val folded: FoldedToken, val length: Int)

    private const val VOWELS = "aeiouyw"

    /**
     * Two consonants are not enough to identify a word once money is in the vocabulary:
     * "lakh" and "like" share the frame "lk", and folding an English word onto a Bengali
     * number would be worse than failing to fold a spelling nobody has typed yet.
     */
    private const val MIN_SKELETON = 3

    private val DIGRAPHS = listOf(
        "sh" to 's', "ch" to 'c', "kh" to 'k', "gh" to 'g', "th" to 't',
        "dh" to 'd', "ph" to 'f', "bh" to 'b', "jh" to 'j', "zh" to 'j', "ng" to 'n',
    )

    /** The words that make a bare "der" a number rather than a possessive ending. */
    private val QUANTITY_TOKENS = setOf(
        CanonicalToken.THOUSAND,
        CanonicalToken.LAKH,
        CanonicalToken.CRORE,
        CanonicalToken.MILLION,
        CanonicalToken.TAKA,
        CanonicalToken.HOUR,
        CanonicalToken.MINUTE,
    )

    private val contextFreeWords: List<VocabularyEntry> =
        Vocabulary.words.filter { !it.requiresNumericPredecessor && !it.requiresQuantitySuccessor }

    private val EXACT_INDEX: Map<String, String> = buildMap {
        for (entry in contextFreeWords) {
            for (variant in entry.variants) put(variant, entry.canonical)
        }
    }

    /**
     * Every canonical token maps to itself, which is what makes plain English safe: "tomorrow"
     * and "taimar" share the frame "tmr", and without this a perfectly ordinary English word
     * could be folded onto a Bengali one by a fuzzy match it never asked for.
     */
    private val ENGLISH_INDEX: Map<String, String> = buildMap {
        for (entry in Vocabulary.words + Vocabulary.phrases) put(entry.canonical, entry.canonical)
        for (entry in contextFreeWords) {
            for (variant in entry.english) put(variant, entry.canonical)
        }
    }

    /** Surface forms that only carry their meaning directly after a number. */
    private val NUMERIC_SUFFIX_INDEX: Map<String, FoldedToken> =
        indexed(Vocabulary.words.filter { it.requiresNumericPredecessor })

    /** Surface forms that only carry their meaning directly before a quantity word. */
    private val QUANTITY_PREFIX_INDEX: Map<String, FoldedToken> =
        indexed(Vocabulary.words.filter { it.requiresQuantitySuccessor })

    private val PHRASE_INDEX: Map<String, FoldedToken> = indexed(Vocabulary.phrases)

    /** Keeps each surface form's canonical token and whether it was Bengali that produced it. */
    private fun indexed(entries: List<VocabularyEntry>): Map<String, FoldedToken> = buildMap {
        for (entry in entries) {
            for (variant in entry.variants) {
                put(variant, FoldedToken(entry.canonical, bengaliEvidence = true))
            }
            for (variant in entry.english) {
                put(variant, FoldedToken(entry.canonical, bengaliEvidence = false))
            }
        }
    }

    private val LONGEST_PHRASE: Int =
        PHRASE_INDEX.keys.maxOf { phrase -> phrase.count { it == ' ' } + 1 }

    /** Skeletons claimed by exactly one canonical token. Contested skeletons are dropped. */
    private val FAMILY_INDEX: Map<String, String> = buildMap {
        val claims = mutableMapOf<String, MutableSet<String>>()
        for (entry in contextFreeWords) {
            for (variant in entry.variants) {
                val skeleton = skeletonOf(variant)
                if (skeleton.length < MIN_SKELETON) continue
                claims.getOrPut(skeleton) { mutableSetOf() }.add(entry.canonical)
            }
        }
        for ((skeleton, canonicals) in claims) {
            if (canonicals.size == 1) put(skeleton, canonicals.first())
        }
    }

    private fun foldDigraphs(letters: String): String = buildString(letters.length) {
        var position = 0
        while (position < letters.length) {
            val pair = if (position + 1 < letters.length) letters.substring(position, position + 2) else null
            val digraph = DIGRAPHS.firstOrNull { it.first == pair }
            if (digraph != null) {
                append(digraph.second)
                position += 2
            } else {
                append(letters[position])
                position++
            }
        }
    }

    private fun collapseRepeats(letters: String): String = buildString(letters.length) {
        for (character in letters) {
            if (isEmpty() || last() != character) append(character)
        }
    }
}
