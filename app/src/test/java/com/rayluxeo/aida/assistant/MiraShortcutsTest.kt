package com.rayluxeo.aida.assistant

import com.google.common.truth.Truth.assertThat
import com.rayluxeo.aida.assistant.MiraShortcuts.Entry
import com.rayluxeo.aida.assistant.MiraShortcuts.WidgetControl
import org.junit.Test

/**
 * The half of Mira's outside entry points that can be decided without a launcher.
 *
 * Publishing needs a `Context` and a shortcut service, and a widget needs a host, so what is
 * checked here is the data underneath both: which entries survive a platform cap smaller than the
 * set, that no two entries collide, and that the widget's three buttons address three different
 * views with three different intents.
 *
 * A duplicate is the dangerous shape in each case. A repeated shortcut id is treated by the
 * platform as the same shortcut and the last one silently wins, costing Abu a whole entry point
 * with nothing anywhere saying so; a repeated view id or entry in [WidgetControl] gives two widget
 * buttons the same behaviour, and the one that stops working is the one he taps with the pen out.
 */
class MiraShortcutsTest {

    @Test
    fun `a launcher with room for everything is given the whole set, in rank order`() {
        assertThat(MiraShortcuts.published(Entry.entries.size)).containsExactlyElementsIn(Entry.entries).inOrder()
    }

    @Test
    fun `a platform cap above what Mira offers is not padded out with filler`() {
        assertThat(MiraShortcuts.published(Entry.entries.size + 10)).hasSize(Entry.entries.size)
    }

    @Test
    fun `were the platform cap ever to fall below the set, Today is the first entry given up`() {
        val kept = MiraShortcuts.published(Entry.entries.size - 1)

        assertThat(kept)
            .containsExactly(Entry.TALK, Entry.NEW_TASK, Entry.NEW_NOTE, Entry.NEW_INVOICE, Entry.QUICK_INK, Entry.SCAN_RECEIPT)
            .inOrder()
    }

    @Test
    fun `a launcher with one slot keeps the one that reaches Mira herself`() {
        assertThat(MiraShortcuts.published(1)).containsExactly(Entry.TALK)
    }

    @Test
    fun `a launcher that allows no shortcuts is published none rather than refused`() {
        assertThat(MiraShortcuts.published(0)).isEmpty()
    }

    @Test
    fun `a cap below zero is read as none rather than throwing`() {
        assertThat(MiraShortcuts.published(-3)).isEmpty()
    }

    @Test
    fun `every entry has its own id`() {
        val ids = Entry.entries.map { it.id }

        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `every entry has its own action`() {
        val actions = Entry.entries.map { it.action }

        assertThat(actions).containsNoDuplicates()
    }

    @Test
    fun `every action is namespaced to Mira, so nothing else on the phone can send one`() {
        Entry.entries.forEach { entry ->
            assertThat(entry.action).startsWith("com.rayluxeo.aida.action.")
        }
    }

    @Test
    fun `every widget button addresses a view of its own`() {
        val viewIds = WidgetControl.entries.map { it.viewId }

        assertThat(viewIds).containsNoDuplicates()
    }

    @Test
    fun `every widget button opens an entry point of its own`() {
        val entries = WidgetControl.entries.map { it.entry }

        assertThat(entries).containsNoDuplicates()
    }
}
