package com.rayluxeo.aida.domain.ink

import com.rayluxeo.aida.core.common.AppResult

/**
 * How a drawing is written down and read back.
 *
 * A port rather than a method on [InkDrawing] for the reason every codec in Mira is one: `domain`
 * owns what a stroke means, an outer layer owns the bytes. It also keeps the format in one place
 * that can be versioned, because a drawing Abu made today has to still open in a year.
 *
 * Failure is a value on the way in, never on the way out: a file that cannot be parsed must come
 * back as a refusal so one damaged page cannot take the notes list down with it.
 */
interface InkCodec {

    fun encode(drawing: InkDrawing): String

    fun decode(stored: String): AppResult<InkDrawing>
}

/**
 * Turns strokes into an image.
 *
 * Separate from the canvas that draws on screen, and that separation is deliberate: the screen
 * renders at whatever size the phone is, while an export has to be the page's own size so the
 * writing keeps its proportions wherever it is opened. One renderer doing both would have to be
 * told which it was doing every time.
 *
 * Implemented outside `domain` because it needs a bitmap, which is Android.
 */
interface InkRasterizer {

    /**
     * Renders [drawing] to a PNG inside Mira's own files directory.
     *
     * @return the path relative to that directory, ready to register as a `FileRecord` — never an
     *   absolute path, which would not survive a restore onto a new phone.
     */
    suspend fun renderPng(drawing: InkDrawing, relativePath: String): AppResult<Long>
}

/**
 * Reading handwriting back as text, when the phone can do it at all.
 *
 * This is the platform's own stylus handwriting, which the keyboard performs **on the device** —
 * free, offline, and without a single added dependency. On the S24 Ultra that is Samsung Keyboard;
 * `isAvailable` is what says so at runtime rather than Mira assuming it from the model name.
 *
 * **It recognises writing as it happens, not afterwards.** `startConnectionlessStylusHandwriting`
 * attaches to a live stylus session; there is no platform call that takes a finished bitmap and
 * returns its text, which `javap` over the API 37 SDK confirms. That is the single most important
 * fact about this port, because it decides the whole feature's shape: recognition is a way of
 * WRITING into a note, not an action performed on a note that already exists. A saved page of ink
 * can only be read back by something that does true OCR, and Mira has exactly one of those —
 * Gemini, which is optional, off by default, and never used without Abu confirming it.
 *
 * Nothing here may ever gate saving. A page of Abu's handwriting is the note; text pulled out of
 * it is a convenience, and §12's rule against a feature that pretends to work applies twice as
 * hard to one that would otherwise lose his writing.
 */
interface HandwritingRecognizer {

    /** False on a phone whose keyboard does not offer it, which is ordinary and not an error. */
    fun isAvailable(): Boolean
}

/** What came back from a live handwriting session. */
sealed interface HandwritingResult {

    data class Recognised(val text: String) : HandwritingResult

    /** Abu wrote, and the recogniser made nothing of it. Not a failure — usually a scribble. */
    data object NoTextRecognised : HandwritingResult

    data class Unavailable(val reason: HandwritingUnavailable) : HandwritingResult
}

/**
 * Why handwriting could not be read.
 *
 * Each one is something different for Abu: a keyboard that cannot do it at all is permanent and
 * he should stop being offered it; a session that would not start is worth one more try.
 */
enum class HandwritingUnavailable {
    /** No installed keyboard offers stylus handwriting. */
    NO_RECOGNISER,

    /** The keyboard has it, but refused this session — the platform's `ERROR_UNSUPPORTED`. */
    REFUSED,

    /** The platform reported a failure it did not explain. */
    FAILED,
}
