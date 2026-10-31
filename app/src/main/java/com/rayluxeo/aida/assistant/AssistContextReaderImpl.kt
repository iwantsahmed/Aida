package com.rayluxeo.aida.assistant

import android.app.assist.AssistContent
import android.app.assist.AssistStructure
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.text.InputType
import android.view.View
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * How much of the screen Mira is willing to carry away from one assist invocation.
 *
 * The walk happens on the main thread while the session window is coming up, and an assist
 * structure from a long article or a chat history can run to tens of thousands of characters.
 * Four thousand is more than the on-device engine ever matches against and still leaves the
 * session opening without a visible stall.
 */
internal const val MAX_SNAPSHOT_CHARS = 4_000

/**
 * A second, independent bound on the walk.
 *
 * The character cap alone does not stop a tree of ten thousand empty containers: that tree costs
 * its whole traversal and yields nothing. This one bounds the work rather than the output.
 */
internal const val MAX_NODES_VISITED = 3_000

/** A window title is a line, not a paragraph. Anything longer is a view's content, mislabelled. */
internal const val MAX_TITLE_CHARS = 120

/**
 * One node of the assist tree, reduced to the fields flattening actually reads.
 *
 * It exists so the rules below can be tested. `AssistStructure.ViewNode` is final, has no public
 * constructor and cannot be built in a local unit test, so what gets skipped and where the cap
 * falls would otherwise only be exercisable on a device — which is exactly the logic that must
 * not be wrong, because it decides whether a password field reaches Mira.
 */
internal interface AssistNodeView {
    val text: String?
    val contentDescription: String?
    val isVisible: Boolean

    /** False when the node has no laid-out area of its own. */
    val hasBounds: Boolean

    val isPassword: Boolean

    /** The view asked the platform to keep it out of assist. */
    val isAssistBlocked: Boolean

    val childCount: Int
    fun childAt(index: Int): AssistNodeView?
}

/**
 * Turns the platform's assist payload into the one shape Mira reasons about.
 *
 * Everything here comes from what Android handed the assistant. Mira has no accessibility service,
 * no media projection and no screenshot API of her own, and she must never grow one: an app that
 * sets `FLAG_SECURE`, or an assist setting Abu turned off, arrives as an absence, and an absence
 * is reported as an empty snapshot rather than worked around or raised as an error.
 *
 * [AssistSnapshot.hasScreenshot] is left false here on purpose. A screenshot arrives on a separate
 * session callback, so only the session knows whether one came, and it copies the snapshot with
 * that answer once it does.
 */
class AssistContextReaderImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AssistContextReader {

    override fun read(structure: AssistStructure?, content: AssistContent?): AssistSnapshot {
        val appPackage = packageOf(structure, content)
        return AssistSnapshot(
            appPackage = appPackage,
            appLabel = appPackage?.let(::labelOf),
            title = humanTitle(windowTitleOf(structure), appPackage),
            webUri = webUriOf(content),
            text = flattenAssistText(rootsOf(structure)),
        )
    }

    /**
     * The activity component is the platform's own answer to "whose screen was this", which is why
     * it is preferred over anything the app chose to put in its own [AssistContent].
     */
    private fun packageOf(structure: AssistStructure?, content: AssistContent?): String? =
        structure?.fromPlatform { activityComponent?.packageName }
            ?: content?.intent?.component?.packageName
            ?: content?.intent?.`package`

    private fun webUriOf(content: AssistContent?): Uri? = content?.webUri

    private fun windowTitleOf(structure: AssistStructure?): String? =
        structure?.fromPlatform {
            if (windowNodeCount > 0) getWindowNodeAt(0).title?.toString() else null
        }

    private fun rootsOf(structure: AssistStructure?): List<AssistNodeView> =
        structure?.fromPlatform {
            (0 until windowNodeCount).mapNotNull { index ->
                getWindowNodeAt(index).rootViewNode?.let(::FrameworkAssistNode)
            }
        }.orEmpty()

    /**
     * Resolving a label needs the package to be visible to Mira, and package visibility can refuse.
     * When it does, the caller falls back to the package name, which still names the app plainly
     * enough for Abu to recognise it — a disclosure that cannot say which app must not be shown
     * at all, but one that says `com.android.chrome` is still true.
     */
    private fun labelOf(packageName: String): String? = try {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        context.packageManager.getApplicationLabel(info).toString().trim().takeIf { it.isNotEmpty() }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }
}

/**
 * Reading an [AssistStructure] is a deserialisation of another process's view tree, and it throws
 * when that data did not survive — the app died, or the structure arrived already released. None
 * of that is a fault Abu can act on, and none of it is worth taking the assistant session down
 * for, so it collapses to the same absence as a screen that shared nothing.
 */
private inline fun <T> AssistStructure.fromPlatform(block: AssistStructure.() -> T): T? = try {
    block()
} catch (_: RuntimeException) {
    null
}

private class FrameworkAssistNode(private val node: AssistStructure.ViewNode) : AssistNodeView {
    override val text: String? get() = node.text?.toString()
    override val contentDescription: String? get() = node.contentDescription?.toString()
    override val isVisible: Boolean get() = node.visibility == View.VISIBLE
    override val hasBounds: Boolean get() = node.width > 0 && node.height > 0
    override val isAssistBlocked: Boolean get() = node.isAssistBlocked
    override val childCount: Int get() = node.childCount

    override val isPassword: Boolean
        get() = isPasswordInput(node.inputType) || hasPasswordAutofillHint(node.autofillHints)

    override fun childAt(index: Int): AssistNodeView? =
        node.getChildAt(index)?.let(::FrameworkAssistNode)
}

/**
 * A password field reaches the assistant on some builds, complete with its contents, and nothing
 * downstream would know what it was looking at. Every variation the platform defines is checked
 * rather than only the common one, because a web login form and a PIN pad use different ones.
 */
internal fun isPasswordInput(inputType: Int): Boolean {
    val variation = inputType and InputType.TYPE_MASK_VARIATION
    return when (inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_TEXT ->
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        else -> false
    }
}

/** The second half of the same guard: a field the app declared to autofill as a password. */
internal fun hasPasswordAutofillHint(hints: Array<String>?): Boolean =
    hints?.any { it.contains("password", ignoreCase = true) } == true

/**
 * Flattens the visible text of [roots] into reading order, applying every rule that decides what
 * Mira is allowed to keep.
 *
 * Invisible, assist-blocked and password nodes prune their whole subtree: a child of a view that
 * is not on screen is not on screen either, and a password field's descendants are its own
 * decoration. A node with no bounds only loses its own text — a container can legitimately report
 * no area of its own while the children inside it are laid out and readable.
 */
internal fun flattenAssistText(roots: List<AssistNodeView>): String {
    val flattening = Flattening()
    for (root in roots) {
        flattening.visit(root)
        if (flattening.isFull) break
    }
    return flattening.result()
}

private val WHITESPACE_RUN = Regex("\\s+")

private class Flattening {
    private val lines = StringBuilder()
    private var visited = 0
    private var previousLine: String? = null

    var isFull = false
        private set

    fun result(): String = lines.toString()

    fun visit(node: AssistNodeView) {
        if (isFull || visited >= MAX_NODES_VISITED) return
        visited++
        if (node.isAssistBlocked || !node.isVisible || node.isPassword) return
        if (node.hasBounds && !append(node.text)) {
            append(node.contentDescription)
        }
        for (index in 0 until node.childCount) {
            val child = node.childAt(index) ?: continue
            visit(child)
            if (isFull) return
        }
    }

    /**
     * A line's own newlines are collapsed so one paragraph stays one line, and a line identical to
     * the one before it is dropped — a text view and the container reporting it both carry the
     * same words often enough to double a whole screen.
     */
    private fun append(candidate: String?): Boolean {
        val line = candidate?.replace(WHITESPACE_RUN, " ")?.trim().orEmpty()
        if (line.isEmpty() || line == previousLine) return false
        val separator = if (lines.isEmpty()) 0 else 1
        val room = MAX_SNAPSHOT_CHARS - lines.length - separator
        if (room <= 0) {
            isFull = true
            return false
        }
        if (separator == 1) lines.append('\n')
        if (line.length <= room) {
            lines.append(line)
        } else {
            lines.append(line, 0, room)
            isFull = true
        }
        previousLine = line
        return true
    }
}

/**
 * A window's title is very often its activity's component name, which reads as gibberish in a
 * disclosure and tells Abu nothing he did not already know from the app name beside it.
 *
 * The test is deliberately shape-based rather than exact: the component reported by the structure
 * and the one naming the window disagree whenever an app runs its screens in another process.
 */
internal fun humanTitle(raw: String?, appPackage: String?): String? {
    val trimmed = raw?.replace(WHITESPACE_RUN, " ")?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    if (trimmed.contains('/')) {
        val head = trimmed.substringBefore('/')
        if (head == appPackage || head.count { it == '.' } >= 2) return null
    }
    return trimmed.take(MAX_TITLE_CHARS)
}
