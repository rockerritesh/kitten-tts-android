package com.kittenml.tts.asr

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * On-device automatic speech recognition with a Whisper-tiny.en ONNX model.
 *
 * Pipeline:  mic PCM → [MelSpectrogram] → encoder ONNX → autoregressive
 * greedy decode through the decoder ONNX → [WhisperTokenizer] → text.
 *
 * Models are bundled in `assets/asr/` (same "bind ONNX inside the app"
 * pattern as the TTS engine) and copied to cache on first load. See
 * `assets/asr/README.md` / `tools/export_whisper_onnx.py` for how to produce
 * the required files. If the assets are missing the engine reports
 * [AsrState.Unavailable] and the rest of the app still works.
 */
class WhisperAsrEngine(private val context: Context) {

    /** I/O names + decoder prompt, written by the export script. */
    private data class AsrConfig(
        val encoderModel: String = "whisper_encoder.onnx",
        val decoderModel: String = "whisper_decoder.onnx",
        val vocabFile: String = "vocab.json",
        val decoderStartIds: List<Int> = listOf(50257, 50362), // <|sot|> <|notimestamps|>
        val eosTokenId: Int = 50256,
        val maxNewTokens: Int = 224,
        val inputFeaturesName: String = "input_features",
        val encoderHiddenName: String = "last_hidden_state",
        val decoderInputIdsName: String = "input_ids",
        val decoderEncoderHiddenName: String = "encoder_hidden_states",
        val decoderLogitsName: String = "logits"
    )

    private val _state = MutableStateFlow<AsrState>(AsrState.Idle)
    val state: StateFlow<AsrState> = _state

    private var env: OrtEnvironment? = null
    private var encoder: OrtSession? = null
    private var decoder: OrtSession? = null
    private var tokenizer: WhisperTokenizer? = null
    private var config: AsrConfig = AsrConfig()
    private var ready = false

    fun assetsPresent(): Boolean = try {
        val files = context.assets.list("asr")?.toSet() ?: emptySet()
        config.encoderModel in files && config.decoderModel in files && config.vocabFile in files
    } catch (e: Exception) {
        false
    }

    suspend fun load() {
        if (ready) return
        _state.value = AsrState.Loading
        withContext(Dispatchers.Default) {
            try {
                config = loadConfig()
                if (!assetsPresent()) {
                    _state.value = AsrState.Unavailable
                    return@withContext
                }
                val ortEnv = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }

                val encFile = copyAssetToCache("asr/${config.encoderModel}", config.encoderModel)
                val decFile = copyAssetToCache("asr/${config.decoderModel}", config.decoderModel)

                val enc = ortEnv.createSession(encFile.absolutePath, opts)
                val dec = ortEnv.createSession(decFile.absolutePath, opts)
                val tok = WhisperTokenizer.fromAssets(context, "asr/${config.vocabFile}")

                env = ortEnv
                encoder = enc
                decoder = dec
                tokenizer = tok
                ready = true
                _state.value = AsrState.Ready
            } catch (e: Exception) {
                _state.value = AsrState.Error(e.message ?: "Failed to load ASR model")
            }
        }
    }

    private fun loadConfig(): AsrConfig {
        return try {
            context.assets.open("asr/asr_config.json").use {
                Gson().fromJson(it.bufferedReader().readText(), AsrConfig::class.java)
            } ?: AsrConfig()
        } catch (e: Exception) {
            AsrConfig() // sensible whisper-tiny.en defaults
        }
    }

    private fun copyAssetToCache(assetPath: String, name: String): File {
        val out = File(context.cacheDir, name)
        if (!out.exists() || out.length() == 0L) {
            context.assets.open(assetPath).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return out
    }

    /**
     * Transcribes mono 16 kHz float PCM. Audio longer than 30 s is processed
     * in sequential 30 s windows and concatenated.
     */
    suspend fun transcribe(audio: FloatArray): String {
        if (!ready) throw IllegalStateException("ASR engine not ready")
        _state.value = AsrState.Transcribing
        return withContext(Dispatchers.Default) {
            try {
                val windowSize = MelSpectrogram.N_SAMPLES
                val pieces = StringBuilder()
                var offset = 0
                while (offset < audio.size) {
                    val end = minOf(offset + windowSize, audio.size)
                    val window = audio.copyOfRange(offset, end)
                    pieces.append(transcribeWindow(window)).append(' ')
                    offset += windowSize
                }
                _state.value = AsrState.Ready
                pieces.toString().trim().replace(Regex("\\s+"), " ")
            } catch (e: Exception) {
                _state.value = AsrState.Error(e.message ?: "Transcription failed")
                throw e
            }
        }
    }

    private fun transcribeWindow(window: FloatArray): String {
        val ortEnv = env!!
        val enc = encoder!!
        val dec = decoder!!
        val cfg = config

        val mel = MelSpectrogram.compute(window)
        val melTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(mel),
            longArrayOf(1, MelSpectrogram.N_MELS.toLong(), MelSpectrogram.N_FRAMES.toLong())
        )

        // Run encoder once, copy hidden states into our own tensor.
        val hiddenTensor: OnnxTensor
        try {
            val encResult = enc.run(mapOf(cfg.inputFeaturesName to melTensor), setOf(cfg.encoderHiddenName))
            try {
                val h = encResult[0] as OnnxTensor
                val shape = h.info.shape // [1, frames, dim]
                val buf = h.floatBuffer
                val data = FloatArray(buf.remaining())
                buf.get(data)
                hiddenTensor = OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(data), shape)
            } finally {
                encResult.close()
            }
        } finally {
            melTensor.close()
        }

        // Greedy autoregressive decode.
        val ids = ArrayList(cfg.decoderStartIds)
        val promptLen = ids.size
        try {
            for (step in 0 until cfg.maxNewTokens) {
                val next = decodeStep(dec, ortEnv, ids, hiddenTensor, cfg)
                if (next == cfg.eosTokenId) break
                ids.add(next)
            }
        } finally {
            hiddenTensor.close()
        }

        val generated = ids.drop(promptLen).filter { it != cfg.eosTokenId }
        return tokenizer!!.decode(generated).trim()
    }

    private fun decodeStep(
        dec: OrtSession,
        ortEnv: OrtEnvironment,
        ids: List<Int>,
        hiddenTensor: OnnxTensor,
        cfg: AsrConfig
    ): Int {
        val idArray = LongArray(ids.size) { ids[it].toLong() }
        val idTensor = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(idArray),
            longArrayOf(1, ids.size.toLong())
        )
        try {
            val result = dec.run(
                mapOf(
                    cfg.decoderInputIdsName to idTensor,
                    cfg.decoderEncoderHiddenName to hiddenTensor
                ),
                setOf(cfg.decoderLogitsName)
            )
            try {
                val logits = result[0] as OnnxTensor
                val shape = logits.info.shape // [1, seq, vocab]
                val vocab = shape[2].toInt()
                val seq = shape[1].toInt()
                val buf = logits.floatBuffer
                val offset = (seq - 1) * vocab
                var bestId = 0
                var bestVal = Float.NEGATIVE_INFINITY
                for (v in 0 until vocab) {
                    val value = buf.get(offset + v)
                    if (value > bestVal) {
                        bestVal = value
                        bestId = v
                    }
                }
                return bestId
            } finally {
                result.close()
            }
        } finally {
            idTensor.close()
        }
    }

    fun close() {
        encoder?.close(); encoder = null
        decoder?.close(); decoder = null
        ready = false
    }
}
