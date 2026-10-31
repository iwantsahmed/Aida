package com.rayluxeo.aida.ui.mira

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What Abu wanted the Mira tab to do the moment he arrived on it.
 *
 * The Mira tab is reached by switching tabs, not by a route that can carry an argument, so a
 * request made elsewhere — "ask Mira about this document" from Files, the Talk shortcut from the
 * launcher — has nowhere to land on the way in. This is that landing place.
 */
sealed interface MiraEntryIntent {

    /** Open Mira with the composer already holding this question, for Abu to read and send. */
    data class Ask(val prompt: String) : MiraEntryIntent

    /** Open Mira already listening, as if Abu had pressed the mark himself. */
    data object StartListening : MiraEntryIntent
}

/**
 * A one-shot hand-off from whoever opens the Mira tab to the Mira screen itself.
 *
 * Application-scoped on purpose: the request is often made before the Mira screen — and its view
 * model — exists, so the intent is held as the latest value rather than emitted into the void. The
 * screen reads it when it comes up and calls [consume], which clears it so a later return to the
 * tab does not replay a stale request. Pure presentation glue: it holds no domain state, reaches
 * no repository, and starts nothing by itself.
 */
@Singleton
class MiraEntryCoordinator @Inject constructor() {

    private val _pending = MutableStateFlow<MiraEntryIntent?>(null)

    /** The request waiting to be honoured, or null when there is nothing to do. */
    val pending: StateFlow<MiraEntryIntent?> = _pending.asStateFlow()

    /** Record what the next arrival on the Mira tab should do. The latest request wins. */
    fun request(intent: MiraEntryIntent) {
        _pending.value = intent
    }

    /** Clear the request once the Mira screen has acted on it. */
    fun consume() {
        _pending.value = null
    }
}
