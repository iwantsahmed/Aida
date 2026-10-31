package com.rayluxeo.aida.domain.context

import com.rayluxeo.aida.domain.command.AppCommand
import com.rayluxeo.aida.domain.command.MiraResponse
import com.rayluxeo.aida.domain.model.Client
import com.rayluxeo.aida.domain.model.Invoice
import com.rayluxeo.aida.domain.model.Note
import com.rayluxeo.aida.domain.model.Project
import com.rayluxeo.aida.domain.model.Quote
import com.rayluxeo.aida.domain.model.Task
import javax.inject.Inject
import javax.inject.Singleton

data class ConversationContextState(
    val lastClient: Client? = null,
    val lastProject: Project? = null,
    val lastInvoice: Invoice? = null,
    val lastQuote: Quote? = null,
    val lastTask: Task? = null,
    val lastNote: Note? = null,
    val lastSearchQuery: String? = null,
    val activeScreen: String? = null,
    val recentCommand: AppCommand? = null,
    val recentResult: MiraResponse? = null,
    val candidateInvoices: List<Invoice> = emptyList(),
    val candidateClients: List<Client> = emptyList(),
)

/**
 * Tracks short-term conversational context across turns.
 *
 * Owned by the domain layer as pure Kotlin with no Android or presentation imports.
 * Maintains recent entities and disambiguates follow-up pronouns ("it", "him", "her",
 * "that", "this", Bengali "তাকে", "এটা", etc.) without guessing when multiple candidates exist.
 */
@Singleton
class ConversationContextManager @Inject constructor() {

    private var state = ConversationContextState()

    fun currentState(): ConversationContextState = state

    fun updateActiveScreen(screen: String) {
        state = state.copy(activeScreen = screen)
    }

    fun updateClient(client: Client?) {
        if (client != null) state = state.copy(lastClient = client)
    }

    fun updateProject(project: Project?) {
        if (project != null) state = state.copy(lastProject = project)
    }

    fun updateInvoice(invoice: Invoice?) {
        if (invoice != null) {
            state = state.copy(
                lastInvoice = invoice,
                candidateInvoices = emptyList(),
            )
        }
    }

    fun updateQuote(quote: Quote?) {
        if (quote != null) state = state.copy(lastQuote = quote)
    }

    fun updateTask(task: Task?) {
        if (task != null) state = state.copy(lastTask = task)
    }

    fun updateNote(note: Note?) {
        if (note != null) state = state.copy(lastNote = note)
    }

    fun updateSearchQuery(query: String?) {
        if (!query.isNullOrBlank()) state = state.copy(lastSearchQuery = query)
    }

    fun updateRecentCommandAndResult(command: AppCommand, result: MiraResponse) {
        state = state.copy(recentCommand = command, recentResult = result)
    }

    fun setCandidateInvoices(invoices: List<Invoice>) {
        state = state.copy(candidateInvoices = invoices)
    }

    fun clearCandidateInvoices() {
        state = state.copy(candidateInvoices = emptyList())
    }

    fun setCandidateClients(clients: List<Client>) {
        state = state.copy(candidateClients = clients)
    }

    fun clearCandidateClients() {
        state = state.copy(candidateClients = emptyList())
    }

    /**
     * Resolves pronouns and contextual follow-up references.
     *
     * Example:
     * Abu: "Ahmed এর invoice কত?"
     * Mira: "Ahmed has ৳45,000 outstanding." (updates lastClient = Ahmed, lastInvoice = INV-001)
     * Abu: "Send it to him."
     * Resolves to: "Send invoice INV-001 to Ahmed"
     *
     * If multiple invoices or clients could match, does not guess: leaves it for explicit
     * disambiguation or resolves candidate ordinal/number selection.
     */
    fun resolveContextualReference(utterance: String): String {
        val trimmed = utterance.trim()
        val lower = trimmed.lowercase()

        // 1. If we have candidate invoices waiting for selection:
        if (state.candidateInvoices.isNotEmpty()) {
            val matchedByNumber = state.candidateInvoices.firstOrNull { candidate ->
                val candNumLower = candidate.number.lowercase()
                lower.contains(candNumLower) ||
                    (candNumLower.startsWith("inv-") && lower.contains(candNumLower.removePrefix("inv-"))) ||
                    (candNumLower.startsWith("#") && lower.contains(candNumLower.removePrefix("#")))
            }

            val matchedByOrdinal = when {
                lower.contains("first") || lower.contains("1st") || lower.contains("প্রথম") || lower.contains("prothom") ->
                    state.candidateInvoices.getOrNull(0)
                lower.contains("second") || lower.contains("2nd") || lower.contains("দ্বিতীয়") || lower.contains("দ্বিতীয়") || lower.contains("ditio") ->
                    state.candidateInvoices.getOrNull(1)
                lower.contains("third") || lower.contains("3rd") || lower.contains("তৃতীয়") || lower.contains("তৃতীয়") || lower.contains("tritio") ->
                    state.candidateInvoices.getOrNull(2)
                else -> null
            }

            val chosen = matchedByNumber ?: matchedByOrdinal
            if (chosen != null) {
                updateInvoice(chosen)
                val clientName = state.lastClient?.name
                return if (clientName != null) {
                    "Send invoice ${chosen.number} to $clientName"
                } else {
                    "Send invoice ${chosen.number}"
                }
            }
        }

        // 2. Resolve "Send it to him" / "Send it" / Bengali "পাঠাও" / "তাকে পাঠাও"
        val isSendAction = lower.contains("send") ||
            lower.contains("পাঠাও") ||
            lower.contains("পাঠিয়ে দাও") ||
            lower.contains("পাঠিয়ে দাও") ||
            lower.contains("pathao") ||
            lower.contains("pathiye dao") ||
            lower.contains("share") ||
            lower.contains("deliver")

        val hasPronounRef = lower.contains(" it") ||
            lower.startsWith("it ") ||
            lower == "it" ||
            lower.contains("him") ||
            lower.contains("her") ||
            lower.contains("them") ||
            lower.contains("that") ||
            lower.contains("this") ||
            lower.contains("invoice") ||
            lower.contains("ইনভয়েস") ||
            lower.contains("তাকে") ||
            lower.contains("take") ||
            lower.contains("eta") ||
            lower.contains("oita") ||
            lower == "send it to him" ||
            lower == "send it" ||
            lower == "pathao" ||
            lower == "পাঠাও"

        if (isSendAction && (hasPronounRef || lower.split(" ").size <= 4)) {
            // If multiple invoices exist, we cannot guess!
            if (state.candidateInvoices.size > 1) {
                val clientName = state.lastClient?.name
                return if (clientName != null) {
                    "Which invoice for $clientName"
                } else {
                    "Which invoice"
                }
            }

            val invoiceNumber = state.lastInvoice?.number
            val clientName = state.lastClient?.name

            if (invoiceNumber != null && clientName != null) {
                return "Send invoice $invoiceNumber to $clientName"
            } else if (invoiceNumber != null) {
                return "Send invoice $invoiceNumber"
            }
        }

        // 3. Resolve "Call him" / "Text him"
        val isCallAction = lower.startsWith("call ") || lower.contains("কল করো") || lower.contains("call koro")
        val isTextAction = lower.startsWith("text ") || lower.startsWith("message ") || lower.contains("মেসেজ") || lower.contains("message dao")
        if (state.lastClient != null) {
            val clientName = state.lastClient!!.name
            if (isCallAction && (lower.contains("him") || lower.contains("her") || lower.contains("তাকে") || lower.contains("take"))) {
                return "Call $clientName"
            }
            if (isTextAction && (lower.contains("him") || lower.contains("her") || lower.contains("তাকে") || lower.contains("take"))) {
                val body = trimmed.substringAfter("him", "").substringAfter("her", "").substringAfter("তাকে", "").trim()
                return if (body.isNotEmpty()) "Text $clientName $body" else "Text $clientName"
            }
        }

        return utterance
    }

    fun clear() {
        state = ConversationContextState()
    }
}
