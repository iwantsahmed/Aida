package com.rayluxeo.aida.ui.onboarding

/**
 * The twelve panes of first-run, in the order Abu walks them.
 *
 * Declaration order is the flow: [OnboardingViewModel] moves by ordinal and stores that ordinal
 * in saved state, so the enum is what survives a rotation or a process death mid-setup. Nothing
 * else in the app reads these ordinals, so reordering only ever costs a half-finished run its
 * position — never stored data.
 */
enum class OnboardingStep {
    WELCOME,
    NAME,
    LANGUAGE,
    VOICE,
    MICROPHONE,
    NOTIFICATIONS,
    CALENDAR,
    CONTACTS,
    LOCATION,
    BIOMETRICS,
    ASSISTANT,
    PRIVACY,
    FINISH;

    val isFirst: Boolean get() = ordinal == 0

    val isLast: Boolean get() = ordinal == entries.lastIndex

    fun next(): OnboardingStep = if (isLast) this else entries[ordinal + 1]

    fun previous(): OnboardingStep = if (isFirst) this else entries[ordinal - 1]

    companion object {
        val count: Int get() = entries.size

        /** Restored indices are clamped: a saved position from an older build must not crash. */
        fun atIndex(index: Int): OnboardingStep = entries[index.coerceIn(0, entries.lastIndex)]
    }
}
