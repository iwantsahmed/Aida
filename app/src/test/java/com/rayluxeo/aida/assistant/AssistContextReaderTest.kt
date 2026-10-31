package com.rayluxeo.aida.assistant

import android.text.InputType
import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.core.common.SpokenLanguage
import org.junit.Test

/**
 * The screen-reading half of Mira's assistant role, tested where it is testable.
 *
 * `AssistStructure`, its `ViewNode` and `AssistContent` are final framework classes with no public
 * constructors, so [AssistContextReaderImpl.read] itself can only be exercised on a device. What
 * is checked here is everything that decides what Mira keeps and what she says about it — which is
 * where a mistake is a privacy leak rather than a rendering bug.
 */
class AssistContextReaderTest {

    // -------------------------------------------------------------- flattening

    @Test
    fun `visible text comes out in reading order`() {
        val tree = node(
            children = listOf(
                node(text = "Prices"),
                node(
                    children = listOf(
                        node(text = "Basic — 400"),
                        node(text = "Pro — 900"),
                    ),
                ),
                node(text = "Updated today"),
            ),
        )

        assertThat(flattenAssistText(listOf(tree)))
            .isEqualTo("Prices\nBasic — 400\nPro — 900\nUpdated today")
    }

    @Test
    fun `every window is read, in the order the platform listed them`() {
        val first = node(text = "Behind")
        val second = node(text = "In front")

        assertThat(flattenAssistText(listOf(first, second))).isEqualTo("Behind\nIn front")
    }

    @Test
    fun `an invisible branch takes its children with it`() {
        val tree = node(
            children = listOf(
                node(text = "Shown"),
                node(
                    text = "Collapsed section",
                    isVisible = false,
                    children = listOf(node(text = "Hidden detail")),
                ),
            ),
        )

        assertThat(flattenAssistText(listOf(tree))).isEqualTo("Shown")
    }

    @Test
    fun `a view that blocked assist is not read, and neither is anything inside it`() {
        val tree = node(
            children = listOf(
                node(text = "Shown"),
                node(
                    text = "Private pane",
                    isAssistBlocked = true,
                    children = listOf(node(text = "Inside the private pane")),
                ),
            ),
        )

        assertThat(flattenAssistText(listOf(tree))).isEqualTo("Shown")
    }

    @Test
    fun `a password field never reaches the snapshot`() {
        val tree = node(
            children = listOf(
                node(text = "Sign in"),
                node(text = "abu@example.com"),
                node(
                    text = "hunter2",
                    contentDescription = "Password",
                    isPassword = true,
                    children = listOf(node(text = "hunter2")),
                ),
            ),
        )

        val flattened = flattenAssistText(listOf(tree))

        assertThat(flattened).isEqualTo("Sign in\nabu@example.com")
        assertThat(flattened).doesNotContain("hunter2")
    }

    @Test
    fun `a container with no bounds of its own still yields its children`() {
        val tree = node(
            text = "Container label nobody can see",
            hasBounds = false,
            children = listOf(node(text = "Real content")),
        )

        assertThat(flattenAssistText(listOf(tree))).isEqualTo("Real content")
    }

    @Test
    fun `a content description stands in only where there is no text`() {
        val tree = node(
            children = listOf(
                node(text = "Save", contentDescription = "Save the invoice"),
                node(contentDescription = "Attach a file"),
            ),
        )

        assertThat(flattenAssistText(listOf(tree))).isEqualTo("Save\nAttach a file")
    }

    @Test
    fun `a line's own newlines collapse and an immediate repeat is dropped`() {
        val tree = node(
            children = listOf(
                node(text = "  Two   lines\nin one view  "),
                node(text = "Two lines in one view"),
                node(text = "Something else"),
            ),
        )

        assertThat(flattenAssistText(listOf(tree)))
            .isEqualTo("Two lines in one view\nSomething else")
    }

    @Test
    fun `an empty node contributes nothing rather than a blank line`() {
        val tree = node(
            children = listOf(
                node(text = "First"),
                node(text = "   "),
                node(),
                node(text = "Second"),
            ),
        )

        assertThat(flattenAssistText(listOf(tree))).isEqualTo("First\nSecond")
    }

    // -------------------------------------------------------------- the caps

    @Test
    fun `one enormous view is cut to exactly the cap`() {
        val tree = node(text = "a".repeat(MAX_SNAPSHOT_CHARS + 1_000))

        val flattened = flattenAssistText(listOf(tree))

        assertThat(flattened).hasLength(MAX_SNAPSHOT_CHARS)
        assertThat(flattened).isEqualTo("a".repeat(MAX_SNAPSHOT_CHARS))
    }

    @Test
    fun `a long page never exceeds the cap however many views carry it`() {
        val tree = node(children = List(200) { node(text = "line $it ".repeat(12)) })

        assertThat(flattenAssistText(listOf(tree)).length).isAtMost(MAX_SNAPSHOT_CHARS)
    }

    @Test
    fun `the walk stops at the node budget`() {
        val padding = List(MAX_NODES_VISITED) { node() }
        val beyondTheBudget = node(text = "past the budget")

        assertThat(flattenAssistText(listOf(node(children = padding + beyondTheBudget))))
            .isEmpty()
    }

    @Test
    fun `a tree inside the node budget is read to the end`() {
        val padding = List(MAX_NODES_VISITED - 2) { node() }
        val last = node(text = "inside the budget")

        assertThat(flattenAssistText(listOf(node(children = padding + last))))
            .isEqualTo("inside the budget")
    }

    // -------------------------------------------------------------- absence

    @Test
    fun `a screen that shared nothing is an empty snapshot, not a failure`() {
        assertThat(flattenAssistText(emptyList())).isEmpty()

        val secured = AssistSnapshot(appPackage = "com.example.bank", appLabel = "Bank")

        assertThat(secured.isEmpty).isTrue()
        assertThat(secured.text).isEmpty()
    }

    @Test
    fun `a secured window that arrives with no readable views is still empty`() {
        val tree = node(children = listOf(node(isVisible = false, children = listOf(node(text = "Balance")))))

        assertThat(AssistSnapshot(text = flattenAssistText(listOf(tree))).isEmpty).isTrue()
    }

    // -------------------------------------------------------------- password input types

    @Test
    fun `every password variation the platform defines is recognised`() {
        assertThat(
            isPasswordInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD),
        ).isTrue()
        assertThat(
            isPasswordInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD),
        ).isTrue()
        assertThat(
            isPasswordInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD),
        ).isTrue()
        assertThat(
            isPasswordInput(InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD),
        ).isTrue()
    }

    @Test
    fun `an ordinary field is not mistaken for a password`() {
        assertThat(isPasswordInput(InputType.TYPE_CLASS_TEXT)).isFalse()
        assertThat(
            isPasswordInput(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS),
        ).isFalse()
        assertThat(isPasswordInput(InputType.TYPE_CLASS_NUMBER)).isFalse()
        assertThat(isPasswordInput(InputType.TYPE_NULL)).isFalse()
    }

    @Test
    fun `an autofill password hint is enough on its own`() {
        assertThat(hasPasswordAutofillHint(arrayOf("password"))).isTrue()
        assertThat(hasPasswordAutofillHint(arrayOf("username", "newPassword"))).isTrue()
        assertThat(hasPasswordAutofillHint(arrayOf("emailAddress"))).isFalse()
        assertThat(hasPasswordAutofillHint(null)).isFalse()
    }

    // -------------------------------------------------------------- the title

    @Test
    fun `a component name is not a title`() {
        assertThat(humanTitle("com.example.app/com.example.app.MainActivity", "com.example.app")).isNull()
        assertThat(humanTitle("com.android.chrome/org.chromium.Main", "com.google.android.apps.nbu")).isNull()
    }

    @Test
    fun `a real title survives, slash and all`() {
        assertThat(humanTitle("Inbox — 5 unread", "com.example.mail")).isEqualTo("Inbox — 5 unread")
        assertThat(humanTitle("AC/DC — Wikipedia", "org.wikipedia")).isEqualTo("AC/DC — Wikipedia")
    }

    @Test
    fun `an absent or blank title is an absence`() {
        assertThat(humanTitle(null, "com.example.app")).isNull()
        assertThat(humanTitle("   ", "com.example.app")).isNull()
    }

    @Test
    fun `a title long enough to be content is cut back to a line`() {
        assertThat(humanTitle("t".repeat(400), null)).hasLength(MAX_TITLE_CHARS)
    }

    // -------------------------------------------------------------- the disclosure

    @Test
    fun `nothing is offered when the cloud is unavailable`() {
        val offer = OutboundConfirmation().offer(
            question = "what is this page about",
            snapshot = snapshot(text = "Prices\nBasic — 400"),
            cloudAvailable = false,
        )

        assertThat(offer).isEqualTo(OutboundOffer.KeepOnDevice)
    }

    @Test
    fun `nothing is offered when the platform shared nothing`() {
        val offer = OutboundConfirmation().offer(
            question = "what is this page about",
            snapshot = AssistSnapshot(appPackage = "com.example.bank", appLabel = "Bank"),
            cloudAvailable = true,
        )

        assertThat(offer).isEqualTo(OutboundOffer.KeepOnDevice)
    }

    @Test
    fun `the disclosed count is the payload's own length`() {
        val offer = OutboundConfirmation().offer(
            question = "  what is this page about  ",
            snapshot = snapshot(text = "Prices\nBasic — 400"),
            cloudAvailable = true,
        )

        val request = (offer as OutboundOffer.Ask).request

        assertThat(request.disclosure.characterCount).isEqualTo(request.text.length)
        assertThat(request.text).contains("Question: what is this page about")
        assertThat(request.text).contains("Basic — 400")
    }

    @Test
    fun `the disclosure names the package when no label could be resolved`() {
        val offer = OutboundConfirmation().offer(
            question = "read this",
            snapshot = AssistSnapshot(appPackage = "com.example.reader", text = "Chapter one"),
            cloudAvailable = true,
        )

        assertThat((offer as OutboundOffer.Ask).request.disclosure.appLabel).isEqualTo("com.example.reader")
    }

    @Test
    fun `screen text is disclosed as absent when only a title arrived`() {
        val offer = OutboundConfirmation().offer(
            question = "what am I looking at",
            snapshot = AssistSnapshot(appPackage = "com.example.app", appLabel = "Example", title = "Statement"),
            cloudAvailable = true,
        )

        val request = (offer as OutboundOffer.Ask).request

        assertThat(request.disclosure.includesScreenText).isFalse()
        assertThat(request.disclosure.characterCount).isEqualTo(request.text.length)
    }

    @Test
    fun `a screenshot is never disclosed as included, because none is ever sent`() {
        val offer = OutboundConfirmation().offer(
            question = "read this",
            snapshot = snapshot(text = "Chapter one").copy(hasScreenshot = true),
            cloudAvailable = true,
        )

        assertThat((offer as OutboundOffer.Ask).request.disclosure.includesScreenshot).isFalse()
    }

    @Test
    fun `the payload carries nothing the disclosure did not name`() {
        val offer = OutboundConfirmation().offer(
            question = "summarise",
            snapshot = snapshot(text = "Chapter one"),
            cloudAvailable = true,
        )

        val text = (offer as OutboundOffer.Ask).request.text

        assertThat(text.lines()).containsExactly(
            "App: Example",
            "Title: Statement",
            "Screen:",
            "Chapter one",
            "Question: summarise",
        ).inOrder()
    }

    // -------------------------------------------------------------- the wording

    @Test
    fun `the confirmation speaks both languages`() {
        val english = outboundWording(SpokenLanguage.ENGLISH)
        val bengali = outboundWording(SpokenLanguage.BENGALI)

        assertThat(english.question).isNotEqualTo(bengali.question)
        assertThat(bengali.keepOnDevice).isNotEmpty()
        assertThat(bengali.perRequest).isNotEmpty()
    }

    @Test
    fun `a Bengali count is written in Bengali digits`() {
        assertThat(outboundWording(SpokenLanguage.BENGALI).characters(1240)).isEqualTo("১২৪০ অক্ষর")
        assertThat(outboundWording(SpokenLanguage.ENGLISH).characters(1240)).isEqualTo("1240 characters")
    }
}

private fun snapshot(text: String): AssistSnapshot = AssistSnapshot(
    appPackage = "com.example.app",
    appLabel = "Example",
    title = "Statement",
    text = text,
)

private fun node(
    text: String? = null,
    contentDescription: String? = null,
    isVisible: Boolean = true,
    hasBounds: Boolean = true,
    isPassword: Boolean = false,
    isAssistBlocked: Boolean = false,
    children: List<FakeAssistNode> = emptyList(),
): FakeAssistNode = FakeAssistNode(
    text = text,
    contentDescription = contentDescription,
    isVisible = isVisible,
    hasBounds = hasBounds,
    isPassword = isPassword,
    isAssistBlocked = isAssistBlocked,
    children = children,
)

/** A tree Mira can be handed without a device, so the rules above can be checked rather than hoped. */
private class FakeAssistNode(
    override val text: String?,
    override val contentDescription: String?,
    override val isVisible: Boolean,
    override val hasBounds: Boolean,
    override val isPassword: Boolean,
    override val isAssistBlocked: Boolean,
    private val children: List<FakeAssistNode>,
) : AssistNodeView {
    override val childCount: Int get() = children.size
    override fun childAt(index: Int): AssistNodeView? = children.getOrNull(index)
}
