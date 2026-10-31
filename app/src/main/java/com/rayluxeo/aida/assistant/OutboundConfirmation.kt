package com.rayluxeo.aida.assistant

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.ui.response.bengaliNumerals
import javax.inject.Inject

/**
 * Exactly what would leave the device, and the disclosure that describes it.
 *
 * [disclosure] is derived from [text] rather than supplied beside it, so the count Abu is shown
 * cannot drift from the payload actually sent. Whatever sends this must send [text] as the whole
 * of Abu's material; scaffolding an engine adds around it is the engine's own words, not his.
 */
data class OutboundRequest(
    val text: String,
    val disclosure: OutboundDisclosure,
)

/**
 * Whether Mira may even ask the question.
 *
 * A cloud option Abu has not enabled is not offered, because dangling one in front of him at the
 * moment he wants an answer is how a private assistant talks someone into switching it on.
 */
sealed interface OutboundOffer {

    /**
     * Nothing leaves. Either the cloud is off, keyless or unreachable, or the screen shared
     * nothing worth sending — and in both cases Mira answers from what is already on the device
     * or says plainly that she cannot.
     */
    data object KeepOnDevice : OutboundOffer

    /** Abu is shown the disclosure, and [request] is sent only if he says yes. */
    data class Ask(val request: OutboundRequest) : OutboundOffer
}

/**
 * The gate in front of "Ask Aida about this screen".
 *
 * Every rule that matters lives here rather than in the UI, because a confirmation whose wording
 * and whose payload are assembled in two places is a confirmation that can describe something
 * other than what it sends:
 *
 * - The default is do not send. There is no timeout, no default-yes and no remembered answer;
 *   the offer is built fresh per request and carries no identity of its own to remember it by.
 * - The disclosure names the app, because "send this screen" means nothing without knowing which.
 * - Nothing is offered at all when the cloud is unavailable, or when the platform shared nothing.
 */
class OutboundConfirmation @Inject constructor() {

    /**
     * @param question what Abu asked, in his own words.
     * @param snapshot what Android was willing to share about the screen.
     * @param cloudAvailable the engine's own answer, asked at this moment rather than read from a
     *   settings screen: Gemini enabled, a key present and the network usable.
     */
    fun offer(
        question: String,
        snapshot: AssistSnapshot,
        cloudAvailable: Boolean,
    ): OutboundOffer {
        if (!cloudAvailable || snapshot.isEmpty) return OutboundOffer.KeepOnDevice
        val appName = snapshot.appLabel ?: snapshot.appPackage
        val text = compose(question, appName, snapshot)
        return OutboundOffer.Ask(
            OutboundRequest(
                text = text,
                disclosure = OutboundDisclosure(
                    appLabel = appName,
                    includesScreenText = snapshot.text.isNotBlank(),
                    // Mira has no path that sends an image: CommandEngine proposes a command from
                    // an utterance and takes nothing else. "No screenshot" is still part of what
                    // Abu is agreeing to, and this is the line that changes the day that is false.
                    includesScreenshot = false,
                    characterCount = text.length,
                ),
            ),
        )
    }

    /**
     * The field names here are read by a model, not by Abu, so they are not UI copy and are not
     * translated. What Abu reads is the disclosure, and that is bilingual.
     */
    private fun compose(question: String, appName: String?, snapshot: AssistSnapshot): String = buildString {
        appName?.let { appendLine("App: $it") }
        snapshot.title?.takeIf { it.isNotBlank() }?.let { appendLine("Title: $it") }
        snapshot.webUri?.let { appendLine("URL: $it") }
        if (snapshot.text.isNotBlank()) {
            appendLine("Screen:")
            appendLine(snapshot.text)
        }
        append("Question: ${question.trim()}")
    }
}

/**
 * Every word the outbound confirmation says, in the language Abu chose.
 *
 * It sits beside the disclosure rather than in the card, because "1240 characters" is the
 * disclosure's arithmetic put into words. A card free to assemble that sentence itself would be
 * free to soften it, and a softened count is not a disclosure.
 */
internal interface OutboundWording {
    val question: String
    val appLine: String
    val unknownApp: String
    val screenTextLine: String
    val screenshotLine: String
    val included: String
    val notIncluded: String
    val perRequest: String
    val send: String
    val keepOnDevice: String
    fun characters(count: Int): String
}

internal object EnglishOutboundWording : OutboundWording {
    override val question = "Send this screen to Gemini?"
    override val appLine = "App"
    override val unknownApp = "Unnamed app"
    override val screenTextLine = "Screen text"
    override val screenshotLine = "Screenshot"
    override val included = "Included"
    override val notIncluded = "Not included"
    override val perRequest = "Asked every time. Aida does not remember this answer."
    override val send = "Send"
    override val keepOnDevice = "Keep on this phone"
    override fun characters(count: Int): String = "$count characters"
}

internal object BengaliOutboundWording : OutboundWording {
    override val question = "এই স্ক্রিনটি Gemini-তে পাঠাব?"
    override val appLine = "অ্যাপ"
    override val unknownApp = "নাম নেই এমন অ্যাপ"
    override val screenTextLine = "স্ক্রিনের লেখা"
    override val screenshotLine = "স্ক্রিনশট"
    override val included = "যাবে"
    override val notIncluded = "যাবে না"
    override val perRequest = "প্রতিবার জিজ্ঞেস করা হবে। আইদা এই উত্তর মনে রাখে না।"
    override val send = "পাঠান"
    override val keepOnDevice = "ফোনেই থাক"
    override fun characters(count: Int): String = "${bengaliNumerals(count.toString())} অক্ষর"
}

internal fun outboundWording(language: SpokenLanguage): OutboundWording = when (language) {
    SpokenLanguage.ENGLISH -> EnglishOutboundWording
    SpokenLanguage.BENGALI -> BengaliOutboundWording
}
