package com.rayluxeo.aida.domain.model

/**
 * How much Mira says around an answer.
 *
 * This is the shape of a reply, not its content: the figure, the fact or the outcome is the same
 * in every style, and only the framing around it changes. [CONCISE] drops the lead-in and gives the
 * answer bare; [BALANCED] is the default, a short natural sentence; [DETAILED] adds the one line of
 * context that makes the answer easier to act on. The style never invents information and never
 * changes a number — those come from the deterministic engines (§4, §8).
 */
enum class ConversationStyle {
    CONCISE,
    BALANCED,
    DETAILED,
}
