package com.rayluxeo.aida.data.repository

/**
 * FTS4 MATCH takes a query language, not a literal. Anything Abu dictates can carry apostrophes,
 * hyphens, colons or quotes that SQLite would read as operators and reject, so each token is
 * reduced to letters and digits and given a prefix wildcard — "ahm" then finds "Ahmed".
 *
 * Letters here are Unicode letters, so Bengali script survives the pass intact.
 *
 * Returns null when nothing searchable remains, which callers treat as "no query at all".
 */
internal fun ftsMatchOrNull(query: String): String? {
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    for (character in query) {
        if (character.isLetterOrDigit()) {
            token.append(character)
        } else if (token.isNotEmpty()) {
            tokens += token.toString()
            token.clear()
        }
    }
    if (token.isNotEmpty()) tokens += token.toString()
    return tokens.takeIf { it.isNotEmpty() }?.joinToString(separator = " ") { "$it*" }
}
