package com.kittenml.tts.scoring

import kotlin.math.sqrt

/** Objective speech-delivery metrics derived from the audio and transcript. */
data class FluencyMetrics(
    val durationSeconds: Float,
    val wordCount: Int,
    val wordsPerMinute: Float,
    /** Pauses longer than ~0.4 s detected in the audio. */
    val longPauses: Int,
    /** Fraction of the recording that contained speech (vs. silence). */
    val speechRatio: Float,
    /** Filler words detected ("um", "uh", "like", "you know", …). */
    val fillerCount: Int
)

/**
 * Analyses recorded speech for fluency signals. Pauses are found by scanning
 * the 16 kHz PCM for low-energy frames; fillers are matched in the transcript.
 * These feed the Fluency & Coherence band in [FreeSpeechScorer].
 */
object FluencyAnalyzer {

    private const val SAMPLE_RATE = 16000
    private const val FRAME_MS = 25
    private const val FRAME = SAMPLE_RATE * FRAME_MS / 1000          // 400 samples
    private const val SILENCE_RMS = 0.012f                          // empirical gate
    private const val LONG_PAUSE_MS = 400

    private val FILLERS = listOf(
        "um", "uh", "uhh", "umm", "er", "erm", "ah", "hmm",
        "like", "you know", "i mean", "sort of", "kind of", "basically", "actually"
    )

    fun analyze(audio: FloatArray, transcript: String): FluencyMetrics {
        val duration = audio.size.toFloat() / SAMPLE_RATE
        val words = transcript.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size
        val wpm = if (duration > 0.05f) wordCount / (duration / 60f) else 0f

        // Frame energy scan for pauses + speech ratio.
        var speechFrames = 0
        var totalFrames = 0
        var longPauses = 0
        var run = 0
        val pauseFrameThreshold = LONG_PAUSE_MS / FRAME_MS
        var i = 0
        while (i + FRAME <= audio.size) {
            var sumSq = 0.0
            for (k in 0 until FRAME) {
                val s = audio[i + k]
                sumSq += (s * s).toDouble()
            }
            val rms = sqrt(sumSq / FRAME).toFloat()
            totalFrames++
            if (rms < SILENCE_RMS) {
                run++
            } else {
                speechFrames++
                if (run >= pauseFrameThreshold) longPauses++
                run = 0
            }
            i += FRAME
        }
        if (run >= pauseFrameThreshold) longPauses++ // trailing pause
        val speechRatio = if (totalFrames > 0) speechFrames.toFloat() / totalFrames else 0f

        val fillerCount = countFillers(transcript.lowercase())

        return FluencyMetrics(duration, wordCount, wpm, longPauses, speechRatio, fillerCount)
    }

    private fun countFillers(text: String): Int {
        var count = 0
        for (filler in FILLERS) {
            val pattern = Regex("(?<![a-z])" + Regex.escape(filler) + "(?![a-z])")
            count += pattern.findAll(text).count()
        }
        return count
    }
}
