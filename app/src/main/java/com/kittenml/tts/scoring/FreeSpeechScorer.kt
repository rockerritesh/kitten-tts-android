package com.kittenml.tts.scoring

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class FreeSpeechResult(
    val assessment: IeltsAssessment,
    val metrics: FluencyMetrics,
    val transcript: String,
    /** Prompt keywords the candidate actually addressed. */
    val keywordsHit: List<String>,
    val keywordsMissed: List<String>
)

/**
 * Estimates IELTS speaking bands across the four official criteria for a FREE
 * spoken response (the candidate answers a prompt in their own words). Unlike
 * the read-aloud scorer, here lexical and grammatical range are genuinely the
 * candidate's own, so these proxies are far more meaningful:
 *
 *  - Task Response               → keyword coverage of the prompt + length.
 *  - Fluency & Coherence         → pace, pauses, fillers, discourse markers.
 *  - Lexical Resource            → vocabulary diversity, sophistication, range.
 *  - Grammatical Range & Accuracy→ sentence variety + complex structures.
 *
 * These remain HEURISTICS computed on-device — they reward range and delivery,
 * but cannot judge meaning or detect every grammatical error the way a trained
 * examiner (or a language model) can. They are guidance for practice, not an
 * official score.
 */
object FreeSpeechScorer {

    private const val IDEAL_WPM = 135f

    private val CONNECTORS = listOf(
        "because", "although", "though", "while", "whereas", "which", "that",
        "who", "if", "unless", "when", "whenever", "since", "as", "however",
        "therefore", "moreover", "furthermore", "nevertheless", "despite",
        "in addition", "for instance", "for example", "on the other hand",
        "as a result", "in conclusion", "firstly", "secondly", "finally"
    )

    fun assess(
        transcript: String,
        metrics: FluencyMetrics,
        keywords: List<String>,
        expectedSeconds: Int
    ): FreeSpeechResult {
        val lower = transcript.lowercase()
        val words = lower.split(Regex("[^a-z']+")).filter { it.isNotBlank() }
        val n = words.size.coerceAtLeast(1)

        // ── Task Response ──
        val hit = keywords.filter { kw -> lower.contains(kw.lowercase()) }
        val missed = keywords.filter { it !in hit }
        val keywordCoverage = if (keywords.isEmpty()) 1f else hit.size.toFloat() / keywords.size
        val expectedWords = (expectedSeconds * (IDEAL_WPM / 60f))
        val lengthAdequacy = min(1f, metrics.wordCount / expectedWords.coerceAtLeast(1f))
        val taskQuality = 0.55f * keywordCoverage + 0.45f * lengthAdequacy
        val task = CriterionScore(
            "Task Response",
            BandUtil.qualityToBand(taskQuality),
            "Covered ${hit.size}/${keywords.size} prompt points · ${metrics.wordCount} words."
        )

        // ── Fluency & Coherence ──
        val minutes = (metrics.durationSeconds / 60f).coerceAtLeast(0.05f)
        val paceQ = (1f - kotlin.math.abs(metrics.wordsPerMinute - IDEAL_WPM) / IDEAL_WPM)
            .coerceIn(0f, 1f)
        val pauseRate = metrics.longPauses / minutes
        val pauseQ = (1f - pauseRate / 8f).coerceIn(0f, 1f)
        val fillerRate = metrics.fillerCount.toFloat() / n * 100f
        val fillerQ = (1f - fillerRate / 10f).coerceIn(0f, 1f)
        val distinctConnectors = CONNECTORS.count { lower.contains(it) }
        val discourseQ = min(1f, distinctConnectors / 5f)
        val fluencyQuality = 0.35f * paceQ + 0.25f * pauseQ + 0.2f * fillerQ + 0.2f * discourseQ
        val fluency = CriterionScore(
            "Fluency & Coherence",
            BandUtil.qualityToBand(fluencyQuality),
            "${metrics.wordsPerMinute.roundToInt()} wpm · ${metrics.longPauses} long pauses · ${metrics.fillerCount} fillers."
        )

        // ── Lexical Resource ──
        val uniqueWords = words.toSet().size
        val guiraud = uniqueWords / sqrt(n.toFloat()) // root type-token ratio
        val diversityQ = min(1f, guiraud / 7f)
        val sophisticated = words.count { it.length >= 8 }
        val sophQ = min(1f, (sophisticated.toFloat() / n) / 0.15f)
        val lengthFactor = min(1f, n / 60f)
        val lexQuality = 0.45f * diversityQ + 0.3f * sophQ + 0.25f * lengthFactor
        val lexical = CriterionScore(
            "Lexical Resource",
            BandUtil.qualityToBand(lexQuality),
            "$uniqueWords distinct words · ${(sophisticated * 100 / n)}% less-common."
        )

        // ── Grammatical Range & Accuracy ──
        val sentences = transcript.split(Regex("[.!?]+"))
            .map { it.trim() }.filter { it.isNotBlank() }
        val sentenceCount = sentences.size.coerceAtLeast(1)
        val sentLens = sentences.map { it.split(Regex("\\s+")).size }
        val avgLen = if (sentLens.isNotEmpty()) sentLens.average().toFloat() else 0f
        val variety = if (sentLens.size > 1) stdDev(sentLens).coerceAtMost(12f) / 12f else 0.2f
        // Ideal average sentence length ~12-18 words.
        val lenQ = (1f - kotlin.math.abs(avgLen - 15f) / 15f).coerceIn(0f, 1f)
        val complexQ = min(1f, distinctConnectors.toFloat() / sentenceCount / 0.6f)
        val grammarQuality = 0.4f * complexQ + 0.35f * lenQ + 0.25f * variety
        val grammar = CriterionScore(
            "Grammatical Range & Accuracy",
            BandUtil.qualityToBand(grammarQuality),
            "$sentenceCount sentences · avg ${avgLen.roundToInt()} words · $distinctConnectors complex links."
        )

        val overall = BandUtil.roundToHalf(
            (task.band + fluency.band + lexical.band + grammar.band) / 4f
        )
        val assessment = IeltsAssessment(overall, task, fluency, lexical, grammar)
        return FreeSpeechResult(assessment, metrics, transcript.trim(), hit, missed)
    }

    private fun stdDev(values: List<Int>): Float {
        if (values.size < 2) return 0f
        val mean = values.average()
        val varc = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(varc).toFloat()
    }
}
