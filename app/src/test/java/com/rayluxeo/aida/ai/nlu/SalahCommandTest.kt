package com.rayluxeo.aida.ai.nlu

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.LanguageMode
import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.CommandRequest
import com.rayluxeo.aida.domain.model.InputSource
import com.rayluxeo.aida.domain.salah.Prayer
import org.junit.Test

/**
 * The Salah commands exercised through the real local pipeline — normalizer, classifier, slot
 * parser — wired by hand (no Hilt, §7.4). Running the normalizer for real is what lets a Bengali
 * utterance like "আসর পড়েছি" be asserted the way Abu types it: it is folded to the same canonical
 * tokens an English or Banglish phrasing produces before the classifier ever sees it.
 */
class SalahCommandTest {

    private val clock = FixedMiraClock()
    private val normalizer = InputNormalizer(LanguageHintDetector(), BanglishNormalizer())
    private val classifier = CommandClassifier()
    private val slots = CommandSlotParser(
        dateTime = MiraDateTimeParser(clock),
        times = TimeExpressionParser(clock),
        currency = CurrencyExpressionParser(BanglaNumberParser(), EnglishNumberParser()),
        titles = TitleExtractor(),
        clock = clock,
    )

    private data class Interpreted(val intent: MiraIntent, val command: AppCommand)

    private fun interpret(raw: String): Interpreted? {
        val normalized = normalizer.normalize(raw, LanguageMode.AUTO)
        val request = CommandRequest(
            raw = raw,
            normalized = normalized.normalized,
            language = normalized.language,
            script = normalized.script,
            source = InputSource.TEXT,
        )
        val scan = slots.scan(request.normalized)
        val classification = classifier.classify(request.normalized, scan.signals) ?: return null
        val command = slots.fill(classification.intent, request, scan)?.command ?: return null
        return Interpreted(classification.intent, command)
    }

    // ---------------------------------------------------------------- marking a prayer

    @Test
    fun `I prayed Asr marks Asr as prayed`() {
        val result = interpret("I prayed Asr")
        assertThat(result?.intent).isEqualTo(MiraIntent.MARK_PRAYER_PRAYED)
        assertThat(result?.command).isEqualTo(AppCommand.MarkPrayerPrayed(Prayer.ASR))
    }

    @Test
    fun `Bengali asor porechi marks Asr as prayed`() {
        val result = interpret("আসর পড়েছি")
        assertThat(result?.intent).isEqualTo(MiraIntent.MARK_PRAYER_PRAYED)
        assertThat(result?.command).isEqualTo(AppCommand.MarkPrayerPrayed(Prayer.ASR))
    }

    @Test
    fun `Banglish Fajr porechi marks Fajr as prayed`() {
        val result = interpret("Fajr porechi")
        assertThat(result?.intent).isEqualTo(MiraIntent.MARK_PRAYER_PRAYED)
        assertThat((result?.command as? AppCommand.MarkPrayerPrayed)?.prayer).isEqualTo(Prayer.FAJR)
    }

    // ---------------------------------------------------------------- queries

    @Test
    fun `next salah kokhon asks for the next prayer`() {
        val result = interpret("next salah kokhon")
        assertThat(result?.intent).isEqualTo(MiraIntent.QUERY_NEXT_PRAYER)
        assertThat(result?.command).isEqualTo(AppCommand.QueryNextPrayer)
    }

    @Test
    fun `Bengali kokhon namaz asks for the next prayer`() {
        assertThat(interpret("কখন নামাজ")?.intent).isEqualTo(MiraIntent.QUERY_NEXT_PRAYER)
    }

    @Test
    fun `tomorrow Fajr time asks for that prayer's time`() {
        val result = interpret("tomorrow Fajr time ki")
        assertThat(result?.intent).isEqualTo(MiraIntent.QUERY_PRAYER_TIME)
        assertThat((result?.command as? AppCommand.QueryPrayerTime)?.prayer).isEqualTo(Prayer.FAJR)
    }

    @Test
    fun `how long until Maghrib asks for the time remaining`() {
        val result = interpret("how long until maghrib")
        assertThat(result?.intent).isEqualTo(MiraIntent.QUERY_PRAYER_TIME_REMAINING)
        assertThat((result?.command as? AppCommand.QueryPrayerTimeRemaining)?.prayer)
            .isEqualTo(Prayer.MAGHRIB)
    }

    @Test
    fun `a monthly Tahajjud count is a tracking query, not a log`() {
        val result = interpret("এই মাসে কয়দিন Tahajjud পড়েছি")
        assertThat(result?.intent).isEqualTo(MiraIntent.QUERY_PRAYER_TRACKING)
        assertThat(result?.command).isInstanceOf(AppCommand.QueryPrayerTracking::class.java)
    }

    @Test
    fun `today's count is a tracking query over a single day`() {
        val result = interpret("আজকে কয় ওয়াক্ত নামাজ পড়েছি")
        assertThat(result?.intent).isEqualTo(MiraIntent.QUERY_PRAYER_TRACKING)
        val command = result?.command as? AppCommand.QueryPrayerTracking
        assertThat(command?.from).isEqualTo(clock.today())
        assertThat(command?.to).isEqualTo(clock.today().plusDays(1))
    }

    // ---------------------------------------------------------------- the Tahajjud reminder

    @Test
    fun `remind me for Tahajjud sets the Tahajjud reminder`() {
        val result = interpret("remind me for tahajjud tonight")
        assertThat(result?.intent).isEqualTo(MiraIntent.SET_TAHAJJUD_REMINDER)
        assertThat(result?.command).isEqualTo(AppCommand.SetTahajjudReminder)
    }

    /** A plain reminder with no Tahajjud in it is still an ordinary reminder. */
    @Test
    fun `an ordinary reminder is not taken for the Tahajjud reminder`() {
        assertThat(interpret("remind me tomorrow at 10 to call Ahmed")?.intent)
            .isEqualTo(MiraIntent.CREATE_REMINDER)
    }
}
