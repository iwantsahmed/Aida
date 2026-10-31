package com.rayluxeo.aida.data.ink

import com.rayluxeo.aida.core.common.AppResult
import com.rayluxeo.aida.core.common.MiraErrorCode
import com.rayluxeo.aida.core.common.failure
import com.rayluxeo.aida.domain.ink.InkCodec
import com.rayluxeo.aida.domain.ink.InkDrawing
import com.rayluxeo.aida.domain.ink.InkPoint
import com.rayluxeo.aida.domain.ink.InkStroke
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The stored form of a page of handwriting: JSON, versioned, and laid out in parallel arrays.
 *
 * **Parallel arrays, not an object per point.** A page is tens of thousands of samples, and an
 * object per sample repeats the key names — `{"x":…,"y":…,"p":…}` — once for every one of
 * them. The keys then outweigh the numbers and a page that should be a few hundred kilobytes
 * becomes several megabytes on Abu's phone for no extra information. One array per channel
 * carries the same data with the names written once per stroke.
 *
 * **Quantised on the way out.** Coordinates are stored to two decimal places, pressure and tilt to
 * three. A hundredth of a pixel is below anything that can be shown: an anti-aliased edge's finest
 * distinction is one step of coverage out of 256, roughly four thousandths of a pixel of apparent
 * position, so the rounding is invisible on the canvas and in an exported PNG alike while cutting
 * the digits per sample roughly in half. Rounding is legitimate for exactly that reason and would
 * not be at a coarser step, where it would show as a stroke no longer sitting where Abu drew it.
 *
 * **Absent channels are omitted.** A digitiser with no pressure sensor reports 1.0 for every
 * sample, and a drawing carrying no tilt or no timing says so by leaving the array out entirely
 * rather than storing a page of identical numbers.
 *
 * **Timings are deltas.** Each `dt` entry is the gap since the previous sample, the first being
 * the stroke's own start. They are whole milliseconds, so the running total is exact and cannot
 * drift, and a gap takes two or three digits where an absolute millisecond count takes six.
 *
 * [decode] never throws. The bytes come off the disk, where a truncated write or a half-restored
 * backup is a real possibility, and one damaged page must come back as a refusal rather than take
 * the notes list down with it. It does not re-check for infinities and NaNs: strict JSON has no
 * way to write them and the parser refuses them outright, so decoding validates only what the
 * format itself cannot rule out — the version, the page size, and the shape of each stroke.
 */
@Singleton
class JsonInkCodec @Inject constructor() : InkCodec {

    override fun encode(drawing: InkDrawing): String {
        val stored = StoredDrawing(
            v = INK_FORMAT_VERSION,
            w = drawing.widthPx,
            h = drawing.heightPx,
            s = drawing.strokes.map { it.stored() },
        )
        return inkJson.encodeToString(StoredDrawing.serializer(), stored)
    }

    override fun decode(stored: String): AppResult<InkDrawing> {
        val parsed = try {
            inkJson.decodeFromString(StoredDrawing.serializer(), stored)
        } catch (damaged: SerializationException) {
            // Covers a file that is not JSON at all, one that is missing a field, and a number the
            // format cannot hold — an infinity written into a coordinate, for instance.
            return failure(MiraErrorCode.VALIDATION, "This is not a stored drawing", damaged)
        }
        if (parsed.v != INK_FORMAT_VERSION) {
            return failure(
                MiraErrorCode.VALIDATION,
                "Ink format version ${parsed.v} was written by a different build of Mira",
            )
        }
        if (parsed.w <= 0 || parsed.h <= 0) {
            return failure(
                MiraErrorCode.VALIDATION,
                "A page of ${parsed.w} by ${parsed.h} pixels is not a page",
            )
        }
        val strokes = ArrayList<InkStroke>(parsed.s.size)
        parsed.s.forEachIndexed { index, stroke ->
            when (val restored = stroke.restored(index)) {
                is AppResult.Failure -> return restored
                is AppResult.Success -> strokes += restored.value
            }
        }
        return AppResult.Success(
            InkDrawing(widthPx = parsed.w, heightPx = parsed.h, strokes = strokes),
        )
    }
}

/**
 * Bumped whenever the stored shape changes. A format with no version number is one that can never
 * change without silently misreading everything written before it, so [JsonInkCodec.decode]
 * refuses a number it was not built to read instead of guessing at the bytes.
 */
private const val INK_FORMAT_VERSION = 1

/** A hundredth of a pixel — see [JsonInkCodec] for why that is fine and why coarser is not. */
private const val COORDINATE_STEP = 100.0

/** A thousandth: of a normalised pressure, and of a radian of tilt. */
private const val SENSOR_STEP = 1_000.0

/**
 * Used only if a drawing reaches [JsonInkCodec.encode] with a nib width that cannot be drawn.
 * Encoding has no failure channel and a non-finite number would throw inside serialization, losing
 * the page Abu was saving — the one outcome this whole feature exists to prevent.
 */
private const val FALLBACK_NIB_PX = 1f

private val inkJson = Json {
    encodeDefaults = false
    ignoreUnknownKeys = true
}

/** Short keys, because they repeat once per stroke and the file is Mira's own. */
@Serializable
private data class StoredDrawing(
    val v: Int,
    val w: Int,
    val h: Int,
    val s: List<StoredStroke>,
)

@Serializable
private data class StoredStroke(
    val w: Float,
    val x: List<Float>,
    val y: List<Float>,
    val p: List<Float>? = null,
    val t: List<Float?>? = null,
    val dt: List<Long>? = null,
)

private fun InkStroke.stored(): StoredStroke {
    val carriesPressure = points.any { it.pressure != InkPoint.FULL_PRESSURE }
    val carriesTilt = points.any { it.tiltRadians != null }
    val carriesTiming = points.any { it.elapsedMillis != 0L }
    var previousMillis = 0L
    return StoredStroke(
        // Not quantised: one number per stroke saves nothing, and the nib width is chosen by the
        // canvas rather than sampled, so there is no digitiser noise in it to throw away.
        w = if (widthPx.isFinite() && widthPx > 0f) widthPx else FALLBACK_NIB_PX,
        x = points.map { it.x.quantised(COORDINATE_STEP) },
        y = points.map { it.y.quantised(COORDINATE_STEP) },
        p = if (carriesPressure) points.map { it.pressure.quantised(SENSOR_STEP) } else null,
        t = if (carriesTilt) {
            points.map { point -> point.tiltRadians?.quantised(SENSOR_STEP) }
        } else {
            null
        },
        dt = if (carriesTiming) {
            points.map { point ->
                val gap = point.elapsedMillis - previousMillis
                previousMillis = point.elapsedMillis
                gap
            }
        } else {
            null
        },
    )
}

/**
 * Rebuilds one stroke, refusing every shape [InkStroke] would refuse.
 *
 * [InkStroke] rejects an empty point list by throwing, which is right for code Mira writes and
 * wrong for bytes read off a disk, so each of those cases is caught here and returned as a value.
 */
private fun StoredStroke.restored(index: Int): AppResult<InkStroke> {
    if (w <= 0f) {
        return malformed("stroke $index has a nib width of $w")
    }
    if (x.isEmpty()) {
        return malformed("stroke $index has no points")
    }
    if (y.size != x.size) {
        return malformed("stroke $index has ${x.size} x values and ${y.size} y values")
    }
    val pressures = p
    if (pressures != null && pressures.size != x.size) {
        return malformed("stroke $index has ${x.size} points and ${pressures.size} pressures")
    }
    val tilts = t
    if (tilts != null && tilts.size != x.size) {
        return malformed("stroke $index has ${x.size} points and ${tilts.size} tilts")
    }
    val gaps = dt
    if (gaps != null && gaps.size != x.size) {
        return malformed("stroke $index has ${x.size} points and ${gaps.size} timings")
    }
    var elapsed = 0L
    val points = ArrayList<InkPoint>(x.size)
    for (sample in x.indices) {
        val pressure = pressures?.get(sample) ?: InkPoint.FULL_PRESSURE
        val tilt = tilts?.get(sample)
        elapsed += gaps?.get(sample) ?: 0L
        points += InkPoint(
            x = x[sample],
            y = y[sample],
            pressure = pressure,
            tiltRadians = tilt,
            elapsedMillis = elapsed,
        )
    }
    return AppResult.Success(InkStroke(widthPx = w, points = points))
}

private fun malformed(detail: String): AppResult.Failure =
    failure(MiraErrorCode.VALIDATION, "Stored drawing is damaged: $detail")

/**
 * Rounds to a fixed step, and answers zero for a sample that is not a finite number at all.
 *
 * The zero is the load-bearing half. [JsonInkCodec.encode] has no failure channel, strict JSON
 * cannot write a NaN or an infinity, and a serializer throwing here would take the whole page Abu
 * was saving with it — so a sample the digitiser could not describe becomes a mark at the origin,
 * which is wrong and visible, rather than a file that never arrives at all.
 *
 * The arithmetic goes through `Double` because a `Float` multiplied by the step loses the digits
 * the step exists to keep. It is deliberately not a claim about a coordinate far off the page:
 * `roundToLong` saturates, so an absurd value still comes back as a mark somewhere else. Nothing
 * a `MotionEvent` can report gets anywhere near that.
 */
private fun Float.quantised(step: Double): Float =
    if (isFinite()) ((toDouble() * step).roundToLong() / step).toFloat() else 0f
