package com.kittenml.tts.asr

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Records mono 16 kHz PCM from the microphone into an in-memory buffer.
 * Whisper expects 16 kHz mono float audio in [-1, 1], so we capture at the
 * native rate the model wants and avoid any resampling.
 *
 * Caller must hold the RECORD_AUDIO permission before calling [start].
 */
class AudioRecorder {

    companion object {
        const val SAMPLE_RATE = 16000
        private const val MAX_SECONDS = 60 // safety cap
    }

    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private val running = AtomicBoolean(false)
    private val samples = ArrayList<Float>(SAMPLE_RATE * 10)

    private val _amplitude = MutableStateFlow(0f)
    /** Most-recent normalized RMS level in [0,1], for a live mic meter. */
    val amplitude: StateFlow<Float> = _amplitude

    @SuppressLint("MissingPermission")
    fun start() {
        if (running.get()) return
        samples.clear()
        _amplitude.value = 0f

        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuf, SAMPLE_RATE) // ~1s buffer
        val ar = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            throw IllegalStateException("AudioRecord failed to initialize")
        }

        record = ar
        running.set(true)
        ar.startRecording()

        thread = Thread {
            val chunk = ShortArray(bufferSize)
            val maxSamples = SAMPLE_RATE * MAX_SECONDS
            while (running.get()) {
                val read = ar.read(chunk, 0, chunk.size)
                if (read <= 0) continue
                var sumSq = 0.0
                synchronized(samples) {
                    for (k in 0 until read) {
                        val f = chunk[k] / 32768f
                        samples.add(f)
                        sumSq += (f * f).toDouble()
                    }
                }
                val rms = Math.sqrt(sumSq / read).toFloat()
                _amplitude.value = (rms * 4f).coerceIn(0f, 1f)
                if (samples.size >= maxSamples) break
            }
        }.apply { isDaemon = true; start() }
    }

    /** Stops recording and returns the captured mono 16 kHz float PCM. */
    fun stop(): FloatArray {
        if (!running.get()) {
            return synchronized(samples) { samples.toFloatArray() }
        }
        running.set(false)
        try {
            thread?.join(500)
        } catch (_: InterruptedException) {
        }
        thread = null
        record?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        record = null
        _amplitude.value = 0f
        return synchronized(samples) { samples.toFloatArray() }
    }

    fun cancel() {
        running.set(false)
        try {
            thread?.join(500)
        } catch (_: InterruptedException) {
        }
        thread = null
        record?.let {
            try {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        record = null
        synchronized(samples) { samples.clear() }
        _amplitude.value = 0f
    }
}
