package com.rayluxeo.aida.assistant.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import com.rayluxeo.aida.assistant.SharedPayload
import com.rayluxeo.aida.core.common.DispatcherProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

/**
 * Why nothing usable came out of a share.
 *
 * Each one leads to a different sentence, which is the point: "the share was empty" and "Mira
 * cannot do anything with a video" are different facts about the same failed gesture, and
 * collapsing them into one apology tells Abu nothing he can act on.
 */
enum class UnsupportedShare {
    /** The intent carried no text and no readable item at all. */
    NOTHING_SHARED,

    /** Text arrived, but it was blank once trimmed. */
    EMPTY_TEXT,

    /** Items arrived, but Android would not hand any of them over. */
    UNREADABLE_ITEM,

    /** Items arrived and Mira has nothing she can do with that kind of file. */
    UNSUPPORTED_TYPE,
}

/**
 * What another app handed over, resolved into the one shape Mira reasons about, or the honest
 * reason it could not be.
 *
 * [names] carries the file names the provider reported, in the same order as
 * [SharedPayload.Documents.uris], and is empty for a text or link share. They are read once,
 * while the grant is alive, because a name is the only part of a shared file Mira can use today
 * and going back for it later would find a revoked Uri.
 */
sealed interface SharedIntake {
    data class Ready(val payload: SharedPayload, val names: List<String> = emptyList()) : SharedIntake

    data class Unsupported(
        val reason: UnsupportedShare,
        /** What the sender called it, for the sentence that has to name it. Null when unknown. */
        val declaredType: String? = null,
    ) : SharedIntake
}

/**
 * Turns an incoming share into a [SharedIntake].
 *
 * A port rather than a concrete class for the same reason [com.rayluxeo.aida.assistant.AssistContextReader]
 * is one: everything Android-shaped about an incoming share stops here, and what travels on is
 * Mira's own vocabulary.
 */
interface SharedPayloadReader {
    /**
     * Reads [intent] now. A shared `Uri` is a grant to this task only, so everything this needs
     * from the content resolver is taken while the grant is alive and nothing is kept to be
     * opened later.
     *
     * It suspends because a provider that is not local — a cloud drive, a scanner app — can take
     * long enough over a type and a name to be an ANR if it were asked on the main thread.
     */
    suspend fun read(intent: Intent): SharedIntake
}

/**
 * The Android side: pulls the extras out of the intent, asks the resolver what it will admit to,
 * and hands the decisions themselves to [SharedText] and [SharedItems].
 *
 * No persistable grant is taken. Mira never needs the bytes after the sheet closes — she keeps
 * the words Abu asks her to keep, not his files — and asking for a standing grant on a file she
 * will not open again would be a permission held for nothing.
 */
@Singleton
class IntentSharedPayloadReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: DispatcherProvider,
) : SharedPayloadReader {

    override suspend fun read(intent: Intent): SharedIntake = when (intent.action) {
        Intent.ACTION_PROCESS_TEXT -> SharedText.classify(
            text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
            subject = null,
        )

        Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> readSend(intent)

        else -> SharedIntake.Unsupported(UnsupportedShare.NOTHING_SHARED)
    }

    /**
     * Text wins over items when both are present. A browser share carries the page title as
     * `EXTRA_TEXT` and sometimes a thumbnail as a stream, and the words are the part Mira can
     * actually do something with.
     */
    private suspend fun readSend(intent: Intent): SharedIntake {
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) {
            return SharedText.classify(
                text = text,
                subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
            )
        }

        val uris = urisIn(intent)
        if (uris.isEmpty()) {
            // Text that was present but blank is a different fact from nothing having arrived.
            return if (text != null) {
                SharedIntake.Unsupported(UnsupportedShare.EMPTY_TEXT)
            } else {
                SharedIntake.Unsupported(UnsupportedShare.NOTHING_SHARED)
            }
        }

        return withContext(dispatchers.io) { describe(uris, intent.type) }
    }

    /** Two resolver round trips per item — its type and its name — so it runs off the main thread. */
    private fun describe(uris: List<Uri>, declared: String?): SharedIntake {
        val types = uris.map { typeOf(it) ?: declared.orEmpty() }
        return when (val verdict = SharedItems.judge(types)) {
            is ItemVerdict.Unusable ->
                SharedIntake.Unsupported(verdict.reason, verdict.declaredType ?: declared)

            is ItemVerdict.Usable -> {
                val kept = verdict.indices.map { uris[it] }
                SharedIntake.Ready(
                    payload = SharedPayload.Documents(kept, verdict.mimeType),
                    names = kept.map { nameOf(it).orEmpty() },
                )
            }
        }
    }

    /**
     * `ClipData` first: the system fills it from `EXTRA_STREAM` precisely so the read grant has
     * something to attach to, and it is the same list for a single share and a multiple one.
     */
    private fun urisIn(intent: Intent): List<Uri> {
        val clip = intent.clipData
        if (clip != null && clip.itemCount > 0) {
            val fromClip = (0 until clip.itemCount).mapNotNull { runCatching { clip.getItemAt(it).uri }.getOrNull() }
            if (fromClip.isNotEmpty()) return fromClip
        }
        return if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        } else {
            listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
    }

    /**
     * Every resolver call is wrapped. A share whose Uri was revoked between the sender building
     * the intent and Mira opening it is an ordinary Tuesday, not an exceptional condition, and it
     * arrives here as a null rather than as a thrown [SecurityException].
     */
    private fun typeOf(uri: Uri): String? =
        runCatching { context.contentResolver.getType(uri) }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun nameOf(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
            }
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.takeIf { it.isNotBlank() }
}

/**
 * Whether a run of shared text is a link or prose, decided without an [Intent] so it can be
 * argued with in a test.
 *
 * The distinction is not cosmetic. A link is the one payload Mira could usefully reach the
 * network for, so it is the one that has to be recognised as a link before anything is offered
 * for it.
 */
internal object SharedText {

    fun classify(text: String?, subject: String?): SharedIntake {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return SharedIntake.Unsupported(UnsupportedShare.EMPTY_TEXT)

        val url = soleLinkIn(trimmed) ?: return SharedIntake.Ready(SharedPayload.Text(trimmed))
        return SharedIntake.Ready(SharedPayload.Link(url = url, title = titleFor(trimmed, subject, url)))
    }

    /**
     * The one address in the text, or null.
     *
     * "One" is load-bearing twice over. A paragraph with several links in it is prose Abu wants
     * kept, not a link he wants followed; and a paragraph wrapped round a single link is still
     * prose, which is why what remains once the address is taken out has to be short enough to
     * read as a heading rather than as the content.
     */
    fun soleLinkIn(text: String): String? {
        val matches = LINK.findAll(text).map { it.value.trimEnd(*TRAILING) }.filter { it.isNotEmpty() }.toList()
        val only = matches.singleOrNull() ?: return null
        return only.takeIf { text.replace(it, "").trim().length <= HEADING_LIMIT }
    }

    /**
     * What to call the link: the sender's own subject when it gave one, otherwise whatever words
     * were wrapped round the address. Never the address itself — a title that repeats the URL
     * reads as though Mira knew nothing about it, which is exactly the case it is trying to hide.
     */
    private fun titleFor(text: String, subject: String?, url: String): String? {
        val given = subject?.trim()?.takeIf { it.isNotEmpty() && !it.equals(url, ignoreCase = true) }
        if (given != null) return given
        return text.replace(url, "").trim().trim(*TRAILING).takeIf { it.isNotEmpty() }
    }

    /**
     * The host, as words, for a link that arrived with no title.
     *
     * It is the only part of an address that survives being spoken: the normalizer splits on
     * punctuation, so a whole URL reaches a note as "https developer android com guide" while a
     * host reaches it as "developer android com". Neither is the address — that is what the
     * caveat on the sheet says — but one of them is readable.
     */
    fun hostOf(url: String): String =
        url.substringAfter("://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('@')
            .substringBefore(':')
            .removePrefix("www.")

    /**
     * A scheme, or the "www." that stands in for one. Anything looser matches a sentence with a
     * full stop in it, and a note titled "example" is worse than no link at all.
     */
    private val LINK = Regex("""(?i)(?:https?://|www\.)\S+""")

    /** Punctuation that ends the sentence the address sits in rather than belonging to it. */
    private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}', '"', '\'', '»', '’')

    private const val HEADING_LIMIT = 200
}

/** What [SharedItems] made of a list of shared items. */
internal sealed interface ItemVerdict {
    /** [indices] point back at the shared list, so the caller keeps only the items that survived. */
    data class Usable(val mimeType: String, val indices: List<Int>) : ItemVerdict

    data class Unusable(val reason: UnsupportedShare, val declaredType: String?) : ItemVerdict
}

/**
 * Which shared items Mira will take, decided on media types alone so it can be tested.
 *
 * She cannot read a file's contents today, and taking one anyway to do nothing with it is the
 * defect this whole area exists to avoid. What she takes is what she can honestly talk about: a
 * picture, a document, a page of text — things with a name and a kind Abu can ask her to write a
 * line about. An archive, an application, a song or a video is refused by name instead.
 */
internal object SharedItems {

    fun judge(types: List<String>): ItemVerdict {
        if (types.isEmpty()) return ItemVerdict.Unusable(UnsupportedShare.NOTHING_SHARED, null)

        val declared = commonType(types.filter { it.isNotBlank() })
        val usable = types.indices.filter { isSupported(types[it]) }
        return when {
            usable.isNotEmpty() -> ItemVerdict.Usable(commonType(usable.map { types[it] }), usable)
            types.all { it.isBlank() } -> ItemVerdict.Unusable(UnsupportedShare.UNREADABLE_ITEM, null)
            else -> ItemVerdict.Unusable(UnsupportedShare.UNSUPPORTED_TYPE, declared)
        }
    }

    fun isSupported(type: String): Boolean {
        val normalized = type.substringBefore(';').trim().lowercase()
        if (normalized.isEmpty()) return false
        if (normalized.startsWith("image/") || normalized.startsWith("text/")) return true
        return normalized in DOCUMENT_TYPES
    }

    /**
     * The narrowest type that covers the lot: the type itself when they agree, the family when
     * only that agrees, and [WILDCARD] when even that does not.
     */
    fun commonType(types: List<String>): String {
        val cleaned = types.map { it.substringBefore(';').trim().lowercase() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return WILDCARD
        cleaned.distinct().singleOrNull()?.let { return it }
        val families = cleaned.map { it.substringBefore('/') }.distinct()
        return families.singleOrNull()?.let { "$it/*" } ?: WILDCARD
    }

    const val WILDCARD = "*/*"

    /**
     * The document types worth appearing in a share sheet for. Kept as an explicit list rather
     * than as a wildcard over the whole application family, because that family is mostly
     * archives and installers and Mira has nothing to say about either.
     */
    private val DOCUMENT_TYPES = setOf(
        "application/pdf",
        "application/rtf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
    )
}
