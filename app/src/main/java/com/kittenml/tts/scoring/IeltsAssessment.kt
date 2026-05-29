package com.kittenml.tts.scoring

/** A single IELTS band descriptor with a 0–9 score and a short explanation. */
data class CriterionScore(
    val name: String,
    val band: Float,
    val detail: String
)

/**
 * An IELTS-style band breakdown across the four official criteria, plus an
 * overall band. For a *read-aloud* exercise these are necessarily estimates
 * derived from reading accuracy, coverage, and pace — see [IeltsScorer] for
 * exactly how each one is computed and what it can and cannot measure.
 */
data class IeltsAssessment(
    val overall: Float,
    val taskResponse: CriterionScore,
    val fluencyCoherence: CriterionScore,
    val lexicalResource: CriterionScore,
    val grammaticalRange: CriterionScore
) {
    val criteria: List<CriterionScore>
        get() = listOf(taskResponse, fluencyCoherence, lexicalResource, grammaticalRange)
}
