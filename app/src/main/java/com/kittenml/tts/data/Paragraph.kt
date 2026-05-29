package com.kittenml.tts.data

/**
 * One IELTS read-aloud item. The learner reads [text] out loud; the recorded
 * speech is transcribed with the Whisper ASR engine and compared against
 * [text] to produce a word-error-rate score.
 */
data class Paragraph(
    val id: Int,
    val title: String,
    val topic: String,
    /** Difficulty band, roughly mapped to IELTS bands 4–9. */
    val band: Float,
    val text: String
) {
    val wordCount: Int
        get() = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }

    val difficultyLabel: String
        get() = when {
            band < 5.5f -> "Beginner"
            band < 7.0f -> "Intermediate"
            else -> "Advanced"
        }
}
