package com.kittenml.tts.data

/**
 * An IELTS-style speaking prompt the learner answers in their own words.
 *
 * - Part 1: short personal questions (~30 s answers)
 * - Part 2: a cue-card long turn (~120 s, with cue points to cover)
 * - Part 3: abstract discussion questions (~45 s)
 *
 * [keywords] are the points a good answer is expected to touch; the scorer
 * uses them to estimate Task Response.
 *
 * Note: `cues`/`keywords` are nullable because Gson instantiates this class
 * via Unsafe (bypassing the constructor), so Kotlin default values are NOT
 * applied — a field absent from the JSON arrives as null. Access them through
 * [cuePoints] / [keywordPoints], which coerce null to an empty list.
 */
data class SpeakingPrompt(
    val id: Int = 0,
    val part: Int = 1,
    val topic: String = "",
    val prompt: String = "",
    val cues: List<String>? = null,
    val keywords: List<String>? = null,
    val expectedSeconds: Int = 45
) {
    val partLabel: String get() = "Part $part"
    val cuePoints: List<String> get() = cues ?: emptyList()
    val keywordPoints: List<String> get() = keywords ?: emptyList()
}
