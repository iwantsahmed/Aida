package com.rayluxeo.aida.voice

import com.rayluxeo.aida.core.design.component.MiraAvatarState

/**
 * The face Mira wears at each point in a voice turn.
 *
 * One mapping, read by every surface that shows her mark — the Mira screen, Home and the floating
 * overlay — so the sphere never means one thing in one place and something else in another. The
 * states line up one to one; this exists so there is a single place they do.
 */
fun MiraVoiceState.toAvatarState(): MiraAvatarState = when (this) {
    MiraVoiceState.IDLE -> MiraAvatarState.IDLE
    MiraVoiceState.LISTENING -> MiraAvatarState.LISTENING
    MiraVoiceState.PROCESSING -> MiraAvatarState.PROCESSING
    MiraVoiceState.EXECUTING -> MiraAvatarState.EXECUTING
    MiraVoiceState.SPEAKING -> MiraAvatarState.SPEAKING
    MiraVoiceState.SUCCESS -> MiraAvatarState.SUCCESS
    MiraVoiceState.ERROR -> MiraAvatarState.ERROR
}
