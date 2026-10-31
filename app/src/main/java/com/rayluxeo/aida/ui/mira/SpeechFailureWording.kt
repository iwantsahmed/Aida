package com.rayluxeo.aida.ui.mira

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.voice.SpeechFailure

/**
 * What Mira says when listening did not work.
 *
 * These are here rather than in `ui/response` because a speech failure is not a MiraResponse:
 * it happens before anything has been interpreted, so there is no command, no outcome and
 * nothing for a phrasebook entry to hang on. They are still written in both languages, because a
 * one-language sentence on a path Abu actually reads is a defect either way.
 *
 * Each line says what happened and what still works. None of them suggests trying the thing that
 * cannot succeed: the microphone being off is not a reason to say "try again".
 */
internal fun speechFailureWording(
    failure: SpeechFailure,
    language: SpokenLanguage,
): String = when (language) {
    SpokenLanguage.ENGLISH -> english(failure)
    SpokenLanguage.BENGALI -> bengali(failure)
}

private fun english(failure: SpeechFailure): String = when (failure) {
    SpeechFailure.NO_MATCH -> "I didn't catch that. Say it again, or type it."
    SpeechFailure.TIMEOUT -> "I didn't hear anything. Tap the orb and say it again."
    SpeechFailure.PERMISSION ->
        "The microphone is off. Turn it on to speak — typing works either way."
    SpeechFailure.NETWORK ->
        "Speech needed the network and couldn't reach it. Type instead — the rest works offline."
    SpeechFailure.BUSY -> "The speech service is busy. Try again in a moment."
    SpeechFailure.UNAVAILABLE ->
        "This phone's speech service isn't available. Typing still does everything."
    SpeechFailure.LANGUAGE_UNAVAILABLE ->
        "That language isn't installed for speech here. Type it, or say it in the other one."
    SpeechFailure.UNKNOWN -> "Listening stopped there. Try again, or type it."
}

private fun bengali(failure: SpeechFailure): String = when (failure) {
    SpeechFailure.NO_MATCH -> "ঠিক শুনতে পাইনি — আবার বলুন, বা লিখে দিন।"
    SpeechFailure.TIMEOUT -> "কিছুই শুনতে পাইনি — অর্ব চেপে আবার বলুন।"
    SpeechFailure.PERMISSION ->
        "মাইক্রোফোন বন্ধ আছে। বলতে হলে চালু করুন — লিখলে এমনিতেও সব চলে।"
    SpeechFailure.NETWORK ->
        "স্পিচের জন্য নেটওয়ার্ক দরকার ছিল, পায়নি। লিখে দিন — বাকি সব অফলাইনেই চলে।"
    SpeechFailure.BUSY -> "স্পিচ সার্ভিস এখন ব্যস্ত — একটু পরে আবার চেষ্টা করুন।"
    SpeechFailure.UNAVAILABLE ->
        "এই ফোনে স্পিচ সার্ভিস পাওয়া যাচ্ছে না। লিখে বললে সব কাজই হবে।"
    SpeechFailure.LANGUAGE_UNAVAILABLE ->
        "এই ভাষাটা এখানে স্পিচের জন্য ইনস্টল করা নেই। লিখে দিন, বা অন্য ভাষায় বলুন।"
    SpeechFailure.UNKNOWN -> "শোনা বন্ধ হয়ে গেল — আবার চেষ্টা করুন, বা লিখে দিন।"
}
