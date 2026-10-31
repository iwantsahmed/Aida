package com.rayluxeo.aida.ui.notes

/**
 * How long a line about what just happened stays up before it clears itself.
 *
 * Long enough to read and to undo. It is declared once because the list and the editor raise the
 * same kind of notice for the same reasons, and a timer written into one screen and forgotten in
 * the other is how a confirmation on the editor sat there until Abu closed the note.
 */
internal const val NOTE_NOTICE_MILLIS = 6_000L
