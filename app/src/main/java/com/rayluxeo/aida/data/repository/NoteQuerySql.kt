package com.rayluxeo.aida.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.rayluxeo.aida.domain.model.NoteFilter
import com.rayluxeo.aida.domain.model.NoteQuery
import com.rayluxeo.aida.domain.model.NoteSort

/**
 * A statement and the values to bind to it, kept apart so the shaping can be tested without a
 * database. [SimpleSQLiteQuery] hides its arguments once built, and an argument list that has
 * silently slipped out of step with its placeholders is exactly the bug worth a test.
 *
 * The same shape as [TaskSql] and deliberately not shared with it: the two clusters version their
 * SQL independently, and one type owned by two areas is the thing that acquires a parameter for
 * the other one's sake.
 */
internal data class NoteSql(val sql: String, val args: List<Any>) {
    fun asRoomQuery(): SupportSQLiteQuery = SimpleSQLiteQuery(sql, args.toTypedArray())
}

/**
 * Turns a [NoteQuery] into SQL for `NoteDao.observeByQuery`.
 *
 * Every list of notes Mira can draw — all of them, the pinned ones, the archive, the bin, the
 * recent few on the hub, a client's notes, a tag's notes, the handwriting — is one query object
 * with different values, assembled here in one place. Four rules hold in every branch, and each
 * one is a defect that has to be impossible rather than remembered:
 *
 * - A binned note is invisible everywhere except [NoteFilter.Deleted].
 * - An archived note is invisible everywhere except [NoteFilter.Archived]. Filing a note away is
 *   not deleting it, and it must not come back in "my notes" as if nothing had happened.
 * - Pinned notes come first whatever Abu sorted by. Pinning is a statement about position, and a
 *   sort that could bury a pinned note would make pinning meaningless. [NoteFilter.Recent] is the
 *   one exception and says why below.
 * - Free text runs through `note_fts`, with the live-and-not-archived filters on the CONTENT
 *   table, because the index is external-content and keeps shadowing rows the list hides.
 *
 * Handwriting is the one condition this builder cannot carry, and [NoteFilter.Ink] says why.
 */
internal object NoteQuerySql {

    /** How many notes [NoteFilter.Recent] takes — what a hub row and the widget show. */
    const val RECENT_LIMIT: Int = 20

    fun rows(query: NoteQuery): NoteSql {
        val where = whereClause(query)
        val limit = if (query.filter == NoteFilter.Recent) " LIMIT $RECENT_LIMIT" else ""
        return NoteSql(
            sql = "SELECT notes.* FROM notes${fromJoins(query)} WHERE ${where.sql} " +
                "ORDER BY ${orderClause(query)}$limit",
            args = where.args,
        )
    }

    /**
     * Every live note, unordered.
     *
     * What the tag chips are the vocabulary of, and it reuses [LIVE] rather than spelling the rule
     * out a second time: a chip row built on a different idea of "my notes" than the list beside
     * it is the mismatch Abu would read as a filter that does nothing.
     */
    fun liveRows(): NoteSql =
        NoteSql(sql = "SELECT notes.* FROM notes WHERE $LIVE", args = emptyList())

    /**
     * The free-text part of [query] as FTS4 MATCH syntax, or null when nothing searchable remains.
     *
     * Whether to join the index and whether to bind a match are the same decision, and they are
     * made here once so the two halves of the statement cannot disagree about it.
     */
    private fun matchOrNull(query: NoteQuery): String? = query.text?.let(::ftsMatchOrNull)

    private fun fromJoins(query: NoteQuery): String =
        if (matchOrNull(query) == null) "" else " JOIN note_fts ON note_fts.rowid = notes.id"

    private fun whereClause(query: NoteQuery): NoteSql {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // The FTS predicate comes first because its placeholder is the first one in the statement.
        // `note_fts` is never aliased: the operand of a MATCH is a hidden column named after the
        // table, so `f MATCH ?` with `note_fts AS f` is a different column and does not compile.
        // The soft-delete and archive filters below sit on `notes`, never on the index, which is
        // external-content and still shadows both.
        matchOrNull(query)?.let {
            clauses += "note_fts MATCH ?"
            args.add(it)
        }

        when (val filter = query.filter) {
            NoteFilter.All -> clauses += LIVE
            NoteFilter.Pinned -> {
                clauses += LIVE
                clauses += "notes.isPinned = 1"
            }
            NoteFilter.Archived -> {
                clauses += NOT_DELETED
                clauses += "notes.archivedAtEpochMillis IS NOT NULL"
            }
            NoteFilter.Deleted -> clauses += "notes.deletedAtEpochMillis IS NOT NULL"
            NoteFilter.Recent -> clauses += LIVE
            // Handwriting is narrowed by `NoteRepositoryImpl.withInkOnly`, not here. `ink_pages`
            // is not one of the tables `NoteDao.observeByQuery` observes, so a condition over it
            // would answer correctly the first time and then never notice a page being written;
            // the repository re-reads it on every emission instead. A caller that takes this
            // statement and runs it without that step gets every live note.
            NoteFilter.Ink -> clauses += LIVE
            is NoteFilter.ForClient -> {
                clauses += LIVE
                clauses += "notes.clientId = ?"
                args.add(filter.clientId)
            }
            is NoteFilter.ForProject -> {
                clauses += LIVE
                clauses += "notes.projectId = ?"
                args.add(filter.projectId)
            }
            is NoteFilter.ForTask -> {
                clauses += LIVE
                clauses += "notes.taskId = ?"
                args.add(filter.taskId)
            }
            // The tag itself is folded into the set below rather than being a clause of its own.
            is NoteFilter.ForTag -> clauses += LIVE
        }

        val required = requiredTagIds(query)
        if (required.isNotEmpty()) {
            // Every named tag, not any of them: a second chip narrows the list, it does not widen
            // it. Counting distinct tags rather than rows keeps that true whatever shape the join
            // takes later, which is how the task builder counts them too.
            clauses += "notes.id IN (SELECT noteId FROM note_tags WHERE tagId IN " +
                "(${placeholders(required.size)}) GROUP BY noteId HAVING COUNT(DISTINCT tagId) = ?)"
            args.addAll(required)
            args.add(required.size)
        }

        return NoteSql(sql = clauses.joinToString(separator = " AND "), args = args)
    }

    /**
     * Every tag a note has to carry to be in this list: the chips Abu has tapped, plus the one a
     * [NoteFilter.ForTag] list is scoped to.
     *
     * One set rather than two clauses. A list of one tag is not a different question from a list
     * of two, and the separate statement it used to have was a second place for the rule to drift
     * — the pattern §7.12 exists to forbid. Sorted so a statement is the same for the same query
     * whatever order the chips were tapped in.
     */
    private fun requiredTagIds(query: NoteQuery): List<Long> {
        val scoped = (query.filter as? NoteFilter.ForTag)?.tagId
        return (query.tagIds + setOfNotNull(scoped)).sorted()
    }

    /**
     * Pinned notes float, then whatever Abu chose, then the newest id.
     *
     * The tie-break runs newest-first to match the fixed queries in `NoteDao`, so a note does not
     * change places when a screen switches between a filter that uses this builder and one that
     * does not. Untitled notes sort last whichever way a title sort runs — reversing a list should
     * not drag the notes with no title to the top of it.
     */
    private fun orderClause(query: NoteQuery): String {
        // Recent is a fixed window rather than a list Abu sorts, so it takes neither the pin rule
        // nor the sort: a LIMIT applied after pinning holds nothing recent once he has twenty
        // pinned notes, and a title sort would quietly make "recent" mean "alphabetically first".
        if (query.filter == NoteFilter.Recent) return RECENT_ORDER

        val direction = if (query.ascending) "ASC" else "DESC"
        val body = when (query.sort) {
            NoteSort.UPDATED -> "notes.updatedAtEpochMillis $direction"
            NoteSort.CREATED -> "notes.createdAtEpochMillis $direction"
            NoteSort.TITLE ->
                "notes.title IS NULL ASC, notes.title COLLATE NOCASE $direction, " +
                    "notes.updatedAtEpochMillis DESC"
        }
        return "notes.isPinned DESC, $body, notes.id DESC"
    }

    private fun placeholders(count: Int): String = List(count) { "?" }.joinToString(separator = ", ")

    private const val NOT_DELETED = "notes.deletedAtEpochMillis IS NULL"

    /** What "my notes" means: not in the bin, and not filed away either. */
    private const val LIVE = "$NOT_DELETED AND notes.archivedAtEpochMillis IS NULL"

    private const val RECENT_ORDER = "notes.updatedAtEpochMillis DESC, notes.id DESC"
}
