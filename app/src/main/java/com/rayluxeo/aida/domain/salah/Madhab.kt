package com.rayluxeo.aida.domain.salah

/**
 * The juristic rule for when Asr begins, which is the one prayer whose time is a matter of
 * school rather than astronomy alone. [SHAFI] (also Maliki and Hanbali) starts Asr when an
 * object's shadow equals its own length; [HANAFI] when it equals twice its length, which falls
 * later in the afternoon.
 */
enum class Madhab(val asrShadowFactor: Int) {
    SHAFI(asrShadowFactor = 1),
    HANAFI(asrShadowFactor = 2),
}
