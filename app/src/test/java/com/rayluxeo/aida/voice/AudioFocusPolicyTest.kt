package com.rayluxeo.aida.voice

import android.media.AudioAttributes
import android.media.AudioManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The decisions [MiraAudioFocusManagerImpl] makes before it touches the framework.
 *
 * Requesting and abandoning focus needs a real `AudioManager` and belongs in an instrumented test.
 * What can be pinned down here is the part that was got wrong first: listening has to take the
 * audio exclusively, because a podcast ducked to a murmur under an open microphone is still what
 * the recognizer hears.
 */
class AudioFocusPolicyTest {

    @Test
    fun `listening pauses other audio rather than ducking it`() {
        assertThat(AudioFocusPolicy.gainFor(AudioFocusPurpose.LISTENING))
            .isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
    }

    @Test
    fun `speaking and tones let other audio stay on underneath`() {
        assertThat(AudioFocusPolicy.gainFor(AudioFocusPurpose.SPEAKING))
            .isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        assertThat(AudioFocusPolicy.gainFor(AudioFocusPurpose.EARCON))
            .isEqualTo(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
    }

    @Test
    fun `listening asks for something different from speaking`() {
        assertThat(AudioFocusPolicy.gainFor(AudioFocusPurpose.LISTENING))
            .isNotEqualTo(AudioFocusPolicy.gainFor(AudioFocusPurpose.SPEAKING))
    }

    @Test
    fun `speech is routed as assistant speech and tones as sonification`() {
        for (purpose in listOf(AudioFocusPurpose.LISTENING, AudioFocusPurpose.SPEAKING)) {
            assertThat(AudioFocusPolicy.usageFor(purpose)).isEqualTo(AudioAttributes.USAGE_ASSISTANT)
            assertThat(AudioFocusPolicy.contentTypeFor(purpose))
                .isEqualTo(AudioAttributes.CONTENT_TYPE_SPEECH)
        }

        assertThat(AudioFocusPolicy.usageFor(AudioFocusPurpose.EARCON))
            .isEqualTo(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        assertThat(AudioFocusPolicy.contentTypeFor(AudioFocusPurpose.EARCON))
            .isEqualTo(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    }

    @Test
    fun `only a tone is willing to go ahead without focus`() {
        assertThat(AudioFocusPolicy.proceedsWithoutFocus(AudioFocusPurpose.EARCON)).isTrue()
        assertThat(AudioFocusPolicy.proceedsWithoutFocus(AudioFocusPurpose.LISTENING)).isFalse()
        assertThat(AudioFocusPolicy.proceedsWithoutFocus(AudioFocusPurpose.SPEAKING)).isFalse()
    }

    @Test
    fun `Mira stops for a call but not for a notification`() {
        assertThat(AudioFocusPolicy.isLoss(AudioManager.AUDIOFOCUS_LOSS)).isTrue()
        assertThat(AudioFocusPolicy.isLoss(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)).isTrue()

        assertThat(AudioFocusPolicy.isLoss(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)).isFalse()
        assertThat(AudioFocusPolicy.isLoss(AudioManager.AUDIOFOCUS_GAIN)).isFalse()
        assertThat(AudioFocusPolicy.isLoss(AudioManager.AUDIOFOCUS_NONE)).isFalse()
    }
}
