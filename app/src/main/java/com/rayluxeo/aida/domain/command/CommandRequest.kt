package com.rayluxeo.aida.domain.command

import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.common.TextScript
import com.rayluxeo.aida.domain.model.InputSource

/**
 * A normalized utterance on its way to becoming an [AppCommand].
 *
 * [raw] is exactly what Abu said or typed; [normalized] is the canonical Latin-script,
 * ASCII-digit form the intent matcher works on. Both are kept so Mira can quote him back.
 */
data class CommandRequest(
    val raw: String,
    val normalized: String,
    val language: SpokenLanguage,
    val script: TextScript,
    val source: InputSource,
)
