package com.kittenml.tts.scoring

/** Alignment operation for a single word, used to colour the reference text. */
enum class WordOp { CORRECT, SUBSTITUTED, DELETED, INSERTED }

/**
 * One element of the alignment between the reference (what should have been
 * read) and the hypothesis (what the ASR heard).
 *
 * - CORRECT     → [reference] == [hypothesis]
 * - SUBSTITUTED → reader said the wrong word ([reference] vs [hypothesis])
 * - DELETED     → reader skipped [reference] (hypothesis is null)
 * - INSERTED    → reader added [hypothesis] that isn't in the reference
 */
data class AlignedWord(
    val op: WordOp,
    val reference: String?,
    val hypothesis: String?
)

data class ScoreResult(
    /** Word Error Rate in [0,1]; can exceed 1 with many insertions. */
    val wer: Float,
    val referenceWordCount: Int,
    val correct: Int,
    val substitutions: Int,
    val deletions: Int,
    val insertions: Int,
    val alignment: List<AlignedWord>,
    val transcript: String
) {
    /** Reading accuracy as a percentage, clamped to [0,100]. */
    val accuracyPercent: Int
        get() = ((1f - wer).coerceIn(0f, 1f) * 100f).toInt()

    /** Rough IELTS-style band estimate from reading accuracy (guidance only). */
    val estimatedBand: Float
        get() = when {
            accuracyPercent >= 97 -> 9.0f
            accuracyPercent >= 93 -> 8.0f
            accuracyPercent >= 88 -> 7.0f
            accuracyPercent >= 80 -> 6.0f
            accuracyPercent >= 70 -> 5.0f
            else -> 4.0f
        }
}
