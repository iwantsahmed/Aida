package com.rayluxeo.aida.ai.local

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.ai.nlu.BanglaNumberParser
import com.rayluxeo.aida.ai.nlu.BanglishNormalizer
import com.rayluxeo.aida.ai.nlu.CommandClassifier
import com.rayluxeo.aida.ai.nlu.CommandSlotParser
import com.rayluxeo.aida.ai.nlu.CurrencyExpressionParser
import com.rayluxeo.aida.ai.nlu.EnglishNumberParser
import com.rayluxeo.aida.ai.nlu.FixedMiraClock
import com.rayluxeo.aida.ai.nlu.InputNormalizer
import com.rayluxeo.aida.ai.nlu.LanguageHintDetector
import com.rayluxeo.aida.ai.nlu.MiraDateTimeParser
import com.rayluxeo.aida.ai.nlu.TimeExpressionParser
import com.rayluxeo.aida.ai.nlu.TitleExtractor
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.command.EngineResult
import com.rayluxeo.aida.domain.model.InputSource
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The two sentences the reminder brief names, end to end.
 *
 * They look alike and are not the same request: "Set a 25 minute timer" is a countdown, and
 * "২০ মিনিট পরে আমাকে মনে করিও" is a reminder twenty minutes from now. Both hang on the same span
 * of time, which is exactly why one can be made to produce the other by accident — the duration
 * parser finds "25 minute" in the first and "২০ মিনিট" in the second, and only the verb tells
 * them apart. Each is asserted as a whole command rather than as an intent, so a slot filled from
 * the wrong half of the sentence fails here too.
 */
class SpokenTimerAndReminderTest {

    private val clock = FixedMiraClock()
    private val normalizer = InputNormalizer(LanguageHintDetector(), BanglishNormalizer())
    private val engine = LocalCommandEngine(
        classifier = CommandClassifier(),
        slots = CommandSlotParser(
            dateTime = MiraDateTimeParser(clock),
            times = TimeExpressionParser(clock),
            currency = CurrencyExpressionParser(BanglaNumberParser(), EnglishNumberParser()),
            titles = TitleExtractor(),
        ),
    )

    @Test
    fun `set a 25 minute timer is a timer`() = runTest {
        assertThat(commandOf("Set a 25 minute timer."))
            .isEqualTo(AppCommand.CreateTimer("Set a 25 minute timer", Duration.ofMinutes(25)))
    }

    /**
     * Twenty minutes from ten o'clock, not a twenty-minute countdown. The sentence names no
     * subject at all, so Abu's own words stand in for the title — the alternative is Mira
     * answering "I did not follow" to a request she understood perfectly.
     */
    @Test
    fun `remind me in 20 minutes is a reminder, in Bengali and in Banglish`() = runTest {
        val twentyPast = FixedMiraClock.dhaka(2026, 3, 10, 10, 20)

        assertThat(commandOf("২০ মিনিট পরে আমাকে মনে করিও."))
            .isEqualTo(AppCommand.CreateReminder("২০ মিনিট পরে আমাকে মনে করিও", twentyPast))
        assertThat(commandOf("20 minit pore amake mone koriyo"))
            .isEqualTo(AppCommand.CreateReminder("20 minit pore amake mone koriyo", twentyPast))
    }

    /** Naming the subject keeps it: the fallback is a last resort, not the usual path. */
    @Test
    fun `a reminder that does name its subject still keeps the subject`() = runTest {
        assertThat(commandOf("Remind me in 20 minutes to drink water")).isEqualTo(
            AppCommand.CreateReminder("drink water", FixedMiraClock.dhaka(2026, 3, 10, 10, 20)),
        )
    }

    private suspend fun commandOf(raw: String): AppCommand {
        val text = normalizer.normalize(raw, LanguageMode.AUTO)
        val result = engine.interpret(
            CommandRequest(
                raw = text.raw,
                normalized = text.normalized,
                language = text.language,
                script = text.script,
                source = InputSource.VOICE,
            )
        )
        assertThat(result).isInstanceOf(EngineResult.Interpreted::class.java)
        return (result as EngineResult.Interpreted).command
    }
}
