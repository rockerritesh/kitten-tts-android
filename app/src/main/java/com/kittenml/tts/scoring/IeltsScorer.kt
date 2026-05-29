package com.kittenml.tts.scoring

import kotlin.math.abs
import kotlin.math.roundToInt


/**
 * Produces an IELTS-style band breakdown across the four official criteria:
 * Task Response, Fluency & Coherence, Lexical Resource, and Grammatical Range
 * & Accuracy.
 *
 * IMPORTANT — what this can and cannot do.
 * A read-aloud task gives the candidate the words to say, so it cannot truly
 * measure lexical range or grammatical construction the way a free-speaking
 * test does. Instead we derive honest *proxies* from the ASR comparison:
 *
 *  - Task Response          → coverage: how much of the passage was read.
 *  - Fluency & Coherence    → reading accuracy + speaking pace (words/minute).
 *  - Lexical Resource       → accuracy on the passage's vocabulary, weighted
 *                             by the passage's own difficulty band.
 *  - Grammatical Range & Accuracy → accuracy of reproducing the full phrasing.
 *
 * For a genuine four-criteria speaking score, the app's "free response" mode
 * (where the learner answers a prompt in their own words) should be assessed
 * with a language model; this scorer is the offline, read-aloud approximation.
 */
object IeltsScorer {

    /** Comfortable read-aloud pace in words per minute. */
    private const val IDEAL_WPM = 140f

    fun assess(score: ScoreResult, durationSeconds: Float, passageBand: Float): IeltsAssessment {
        val ref = score.referenceWordCount.coerceAtLeast(1)
        val accuracy = (1f - score.wer).coerceIn(0f, 1f)

        // Coverage: words the reader actually attempted (correct + misread).
        val coverage = ((score.correct + score.substitutions).toFloat() / ref).coerceIn(0f, 1f)

        // Pace: hypothesis word count over the recording duration.
        val spokenWords = score.correct + score.substitutions + score.insertions
        val minutes = (durationSeconds / 60f).coerceAtLeast(0.05f)
        val wpm = spokenWords / minutes
        val paceQuality = paceQuality(wpm)

        val taskResponse = CriterionScore(
            name = "Task Response",
            band = qualityToBand(coverage),
            detail = "Read ${(coverage * 100).roundToInt()}% of the passage."
        )
        val fluency = CriterionScore(
            name = "Fluency & Coherence",
            band = qualityToBand(0.55f * accuracy + 0.45f * paceQuality),
            detail = "${wpm.roundToInt()} wpm · ${(accuracy * 100).roundToInt()}% smooth."
        )
        // Nudge lexical score toward the passage's own band when read accurately.
        val lexQuality = (accuracy * (0.7f + 0.3f * (passageBand / 9f))).coerceIn(0f, 1f)
        val lexical = CriterionScore(
            name = "Lexical Resource",
            band = qualityToBand(lexQuality),
            detail = "Vocabulary of a Band ${passageBand} text reproduced ${(accuracy * 100).roundToInt()}%."
        )
        val grammar = CriterionScore(
            name = "Grammatical Range & Accuracy",
            band = qualityToBand(accuracy),
            detail = "${score.substitutions + score.deletions} phrasing errors detected."
        )

        val overall = BandUtil.roundToHalf(
            (taskResponse.band + fluency.band + lexical.band + grammar.band) / 4f
        )
        return IeltsAssessment(overall, taskResponse, fluency, lexical, grammar)
    }

    private fun paceQuality(wpm: Float): Float {
        // 1.0 at the ideal pace, falling off as it drifts away.
        val diff = abs(wpm - IDEAL_WPM)
        return (1f - diff / IDEAL_WPM).coerceIn(0f, 1f)
    }

    private fun qualityToBand(quality: Float): Float = BandUtil.qualityToBand(quality)
}
