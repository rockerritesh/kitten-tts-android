package com.kittenml.tts.scoring

import kotlin.math.roundToInt

/** Shared helpers for turning a [0,1] quality signal into an IELTS band. */
object BandUtil {

    /** Maps a [0,1] quality to an IELTS band in [3.5, 9.0], rounded to 0.5. */
    fun qualityToBand(quality: Float): Float {
        val raw = 3.5f + quality.coerceIn(0f, 1f) * 5.5f
        return roundToHalf(raw)
    }

    fun roundToHalf(value: Float): Float = (value * 2f).roundToInt() / 2f
}
