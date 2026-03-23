package com.kittenml.tts.model

sealed class EngineState {
    data object Idle : EngineState()
    data object Loading : EngineState()
    data object Ready : EngineState()
    data object Generating : EngineState()
    data class Error(val message: String) : EngineState()
}
