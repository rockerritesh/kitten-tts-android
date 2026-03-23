package com.kittenml.tts.model

enum class TTSModel(
    val displayName: String,
    val modelFileName: String,
    val voicesFileName: String,
    val speedPriors: Map<String, Float>
) {
    NANO(
        displayName = "Nano (15M)",
        modelFileName = "kitten_tts_nano_v0_8.onnx",
        voicesFileName = "voices_nano.json",
        speedPriors = mapOf(
            "Bella" to 0.8f, "Jasper" to 0.8f,
            "Luna" to 0.8f, "Bruno" to 0.8f,
            "Rosie" to 0.8f, "Hugo" to 0.9f,
            "Kiki" to 0.8f, "Leo" to 0.8f
        )
    ),
    MICRO(
        displayName = "Micro (40M)",
        modelFileName = "kitten_tts_micro_v0_8.onnx",
        voicesFileName = "voices_micro.json",
        speedPriors = emptyMap()
    ),
    MINI(
        displayName = "Mini (80M)",
        modelFileName = "kitten_tts_mini_v0_8.onnx",
        voicesFileName = "voices_mini.json",
        speedPriors = emptyMap()
    );

    companion object {
        val voiceNames = listOf("Rosie", "Bella", "Jasper", "Luna", "Bruno", "Hugo", "Kiki", "Leo")
    }
}
