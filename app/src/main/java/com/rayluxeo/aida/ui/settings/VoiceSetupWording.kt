package com.rayluxeo.aida.ui.settings

import android.speech.tts.Voice
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.bengaliNumerals
import com.rayluxeo.aida.voice.TtsVoiceInfo
import kotlin.math.roundToInt

/**
 * Every word the voice setup screen says to Abu, in the language he chose.
 *
 * The screen was English-only, which made it one of the places where Mira could tell him in a
 * language he had not picked that his own phone could not speak his. Same phrasebook shape as
 * [AssistantWording] and [PlacesWording]: one interface, one object per language, one selector.
 *
 * Nothing here names, ranks or labels a voice by gender. `android.speech.tts.Voice` reports none,
 * so every word a row carries is one the engine actually said (AGENTS.md §4.3).
 *
 * Bengali is the register Abu speaks: Android's own words — engine, voice, text-to-speech — are
 * kept and written in Bengali script rather than replaced with coinages he would have to decode.
 * Numbers go through [bengaliNumerals], because a Latin digit in a Bengali line reads as a
 * different voice.
 */
internal interface VoiceSetupWording {

    // The frame: bar, back, and the one dismissible notice.
    val title: String
    val subtitle: String
    val back: String
    val dismiss: String

    // The phone with nothing to speak with at all.
    val noEngines: String
    val openTextToSpeechSettings: String

    // The list itself.
    val intro: String
    val engineSection: String
    val systemDefault: String
    val systemDefaultDetail: String
    val englishVoices: String
    val bengaliVoices: String
    val englishMissing: String
    val bengaliMissing: String

    // Rate and pitch, and the two ways to hear them.
    val speedAndPitch: String
    val speechRate: String
    val pitch: String
    val hearEnglish: String
    val hearBengali: String

    // Whether she answers out loud at all.
    val spokenReplies: String
    val speakHerAnswers: String
    val speakHerAnswersDetail: String

    /** What the engine said about one voice, and nothing Mira has guessed. */
    fun attributes(voice: TtsVoiceInfo): String

    /** A rate or a pitch as a ratio: "1.2×". */
    fun multiplier(value: Float): String

    // When something did not work. Never a raw exception.
    val previewFailed: String
    val noTextToSpeechSettingsScreen: String
}

internal object EnglishVoiceSetupWording : VoiceSetupWording {

    override val title = "Voice setup"
    override val subtitle = "Choose by ear"
    override val back = "Back"
    override val dismiss = "Dismiss"

    override val noEngines =
        "This phone reports no text-to-speech engine, so Aida cannot speak yet. She still answers " +
            "on screen."
    override val openTextToSpeechSettings = "Open text-to-speech settings"

    override val intro = "Tap a voice to hear it. The one you tap is the one Aida speaks with."
    override val engineSection = "Engine"
    override val systemDefault = "System default"
    override val systemDefaultDetail = "Whichever engine Android is set to use"
    override val englishVoices = "English voices"
    override val bengaliVoices = "Bengali voices"
    override val englishMissing =
        "This engine offers no English voice. Aida will answer on screen until one is installed."
    override val bengaliMissing =
        "This engine has no Bengali voice installed. Aida will still answer in Bengali on screen, " +
            "but she cannot read it out until one is added."

    override val speedAndPitch = "Speed and pitch"
    override val speechRate = "Speech rate"
    override val pitch = "Pitch"
    override val hearEnglish = "Hear English"
    override val hearBengali = "Hear বাংলা"

    override val spokenReplies = "Spoken replies"
    override val speakHerAnswers = "Speak her answers"
    override val speakHerAnswersDetail =
        "Off means Aida answers on screen only. Samples here still play."

    override fun attributes(voice: TtsVoiceInfo): String = buildList {
        add(voice.localeTag)
        add(
            when {
                voice.quality >= Voice.QUALITY_VERY_HIGH -> "very high quality"
                voice.quality >= Voice.QUALITY_HIGH -> "high quality"
                voice.quality >= Voice.QUALITY_NORMAL -> "normal quality"
                voice.quality >= Voice.QUALITY_LOW -> "low quality"
                else -> "very low quality"
            },
        )
        add(
            when {
                voice.latency <= Voice.LATENCY_VERY_LOW -> "instant"
                voice.latency <= Voice.LATENCY_LOW -> "quick"
                voice.latency <= Voice.LATENCY_NORMAL -> "normal delay"
                voice.latency <= Voice.LATENCY_HIGH -> "slow"
                else -> "very slow"
            },
        )
        if (voice.requiresNetwork) add("needs the network")
        if (!voice.isInstalled) add("not downloaded yet")
    }.joinToString(ATTRIBUTE_SEPARATOR)

    override fun multiplier(value: Float) = ratio(value)

    override val previewFailed =
        "That voice would not play. It may still be downloading, or it may need the network."
    override val noTextToSpeechSettingsScreen =
        "This phone has no text-to-speech settings screen Aida can open."
}

internal object BengaliVoiceSetupWording : VoiceSetupWording {

    override val title = "ভয়েস সেটআপ"
    override val subtitle = "শুনে বেছে নিন"
    override val back = "পেছনে যান"
    override val dismiss = "বন্ধ করুন"

    override val noEngines =
        "এই ফোনে কোনো text-to-speech ইঞ্জিন নেই, তাই আইদা এখনও কথা বলতে পারছে না। সে স্ক্রিনে " +
            "উত্তর দিয়েই যাবে।"
    override val openTextToSpeechSettings = "text-to-speech সেটিংস খুলুন"

    override val intro = "শুনতে হলে একটি ভয়েসে ট্যাপ করুন। যেটিতে ট্যাপ করবেন, আইদা সেই ভয়েসেই কথা বলবে।"
    override val engineSection = "ইঞ্জিন"
    override val systemDefault = "সিস্টেমের ডিফল্ট"
    override val systemDefaultDetail = "অ্যান্ড্রয়েড যে ইঞ্জিনটি ব্যবহার করতে বলা আছে"
    override val englishVoices = "English ভয়েস"
    override val bengaliVoices = "বাংলা ভয়েস"
    override val englishMissing =
        "এই ইঞ্জিনে কোনো English ভয়েস নেই। একটি ইনস্টল না হওয়া পর্যন্ত আইদা স্ক্রিনেই উত্তর দেবে।"
    override val bengaliMissing =
        "এই ইঞ্জিনে কোনো বাংলা ভয়েস ইনস্টল করা নেই। আইদা স্ক্রিনে বাংলাতেই উত্তর দেবে, তবে একটি " +
            "যোগ না করা পর্যন্ত সে তা পড়ে শোনাতে পারবে না।"

    override val speedAndPitch = "গতি আর পিচ"
    override val speechRate = "কথার গতি"
    override val pitch = "পিচ"
    override val hearEnglish = "English শুনুন"
    override val hearBengali = "বাংলা শুনুন"

    override val spokenReplies = "মুখে উত্তর"
    override val speakHerAnswers = "উত্তরগুলো পড়ে শোনাবে"
    override val speakHerAnswersDetail =
        "বন্ধ থাকলে আইদা শুধু স্ক্রিনেই উত্তর দেবে। এখানকার নমুনাগুলো তখনও বাজবে।"

    override fun attributes(voice: TtsVoiceInfo): String = buildList {
        add(voice.localeTag)
        add(
            when {
                voice.quality >= Voice.QUALITY_VERY_HIGH -> "খুব উঁচু মানের"
                voice.quality >= Voice.QUALITY_HIGH -> "উঁচু মানের"
                voice.quality >= Voice.QUALITY_NORMAL -> "সাধারণ মানের"
                voice.quality >= Voice.QUALITY_LOW -> "নিচু মানের"
                else -> "খুব নিচু মানের"
            },
        )
        add(
            when {
                voice.latency <= Voice.LATENCY_VERY_LOW -> "সঙ্গে সঙ্গে"
                voice.latency <= Voice.LATENCY_LOW -> "দ্রুত"
                voice.latency <= Voice.LATENCY_NORMAL -> "স্বাভাবিক দেরি"
                voice.latency <= Voice.LATENCY_HIGH -> "ধীর"
                else -> "খুব ধীর"
            },
        )
        if (voice.requiresNetwork) add("নেটওয়ার্ক লাগে")
        if (!voice.isInstalled) add("এখনও নামানো হয়নি")
    }.joinToString(ATTRIBUTE_SEPARATOR).let(::bengaliNumerals)

    override fun multiplier(value: Float) = bengaliNumerals(ratio(value))

    override val previewFailed =
        "ভয়েসটি বাজল না। হয়তো এখনও নামছে, নয়তো এর জন্য নেটওয়ার্ক লাগে।"
    override val noTextToSpeechSettingsScreen =
        "এই ফোনে text-to-speech-এর এমন কোনো সেটিংস স্ক্রিন নেই যা আইদা খুলতে পারে।"
}

internal fun voiceSetupWording(language: SpokenLanguage): VoiceSetupWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishVoiceSetupWording
    SpokenLanguage.BENGALI -> BengaliVoiceSetupWording
}

/** The middle dot both languages separate a voice's facts with. */
private const val ATTRIBUTE_SEPARATOR = " · "

/**
 * One decimal place and a multiplication sign, in one copy for both languages.
 *
 * Deliberately locale-free: a decimal comma in a ratio reads as a typo. The Bengali object
 * converts the digits afterwards, which is the only part of it that differs.
 */
private fun ratio(value: Float): String = "${(value * 10).roundToInt() / 10f}×"
