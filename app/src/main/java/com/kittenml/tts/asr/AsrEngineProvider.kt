package com.kittenml.tts.asr

import android.content.Context

/**
 * Process-wide holder for a single [WhisperAsrEngine].
 *
 * Both the reading-practice and free-speaking features transcribe speech, and
 * the Whisper ONNX sessions are heavy. Sharing one engine avoids loading the
 * model twice. It is bound to the application context and lives for the life
 * of the process, so callers must NOT close it.
 */
object AsrEngineProvider {

    @Volatile
    private var engine: WhisperAsrEngine? = null

    fun get(context: Context): WhisperAsrEngine {
        return engine ?: synchronized(this) {
            engine ?: WhisperAsrEngine(context.applicationContext).also { engine = it }
        }
    }
}
