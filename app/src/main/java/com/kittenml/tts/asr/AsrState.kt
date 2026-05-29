package com.kittenml.tts.asr

sealed class AsrState {
    /** Models not yet present in assets, or not loaded. */
    data object Unavailable : AsrState()
    data object Idle : AsrState()
    data object Loading : AsrState()
    data object Ready : AsrState()
    data object Recording : AsrState()
    data object Transcribing : AsrState()
    data class Error(val message: String) : AsrState()
}
