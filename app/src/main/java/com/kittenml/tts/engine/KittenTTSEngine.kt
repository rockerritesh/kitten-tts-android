package com.kittenml.tts.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.kittenml.tts.model.EngineState
import com.kittenml.tts.model.TTSModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

class KittenTTSEngine(private val context: Context) {

    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state

    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    private var voices: Map<String, List<List<Float>>> = emptyMap()
    private var espeakReady = false
    private var loadedModel: TTSModel? = null
    private val espeakBridge = EspeakBridge()
    val audioPlayer = AudioPlayer()

    // ── espeak-ng Data Setup ──

    private fun prepareEspeakData(): String {
        val dest = File(context.cacheDir, "espeak-ng-data")

        // Cache check
        if (File(dest, "phontab").exists() && File(dest, "lang/gmw/en-US").exists()) {
            return dest.absolutePath
        }

        dest.deleteRecursively()
        dest.mkdirs()

        // Copy 6 core binary files from assets
        val coreFiles = listOf(
            "phontab", "phondata", "phondata-manifest",
            "phonindex", "intonations", "en_dict"
        )
        for (file in coreFiles) {
            context.assets.open("espeak-ng-data/$file").use { input ->
                File(dest, file).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        // Write language definitions from code (extensionless files)
        val langDir = File(dest, "lang/gmw")
        langDir.mkdirs()

        File(langDir, "en-US").writeText(
            """
            name English (America)
            language en-us 2
            language en 3

            phonemes en-us
            dictrules 3 6

            stressLength 140 120 190 170 0 0 255 300
            stressAmp  17 16  19 19  19 19  21 19

            replace 03 I  i
            replace 03 I2 i
            """.trimIndent()
        )

        File(langDir, "en").writeText(
            """
            name English (Great Britain)
            language en-gb  2
            language en 2

            tunes s1 c1 q1 e1
            """.trimIndent()
        )

        // Create required empty directory (espeak scans this)
        File(dest, "voices/!v").mkdirs()

        return dest.absolutePath
    }

    // ── Model Loading ──

    suspend fun loadModel(model: TTSModel) {
        _state.value = EngineState.Loading
        loadedModel = null
        session?.close()
        session = null

        withContext(Dispatchers.Default) {
            try {
                // Create ONNX session — copy model to cache to avoid loading full
                // byte array into JVM heap (models are 39-75 MB)
                val ortEnv = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions()
                opts.setIntraOpNumThreads(2)

                val modelFile = File(context.cacheDir, model.modelFileName)
                if (!modelFile.exists()) {
                    context.assets.open("models/${model.modelFileName}").use { input ->
                        modelFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                val ortSession = ortEnv.createSession(modelFile.absolutePath, opts)

                // Parse voice embeddings
                val voiceJson = context.assets.open("voices/${model.voicesFileName}").use {
                    it.bufferedReader().readText()
                }
                val type = object : TypeToken<Map<String, List<List<Double>>>>() {}.type
                val parsed: Map<String, List<List<Double>>> = Gson().fromJson(voiceJson, type)
                val voiceMap = parsed.mapValues { (_, positions) ->
                    positions.map { pos -> pos.map { it.toFloat() } }
                }

                // Initialize espeak-ng
                val dataDir = prepareEspeakData()
                val initResult = espeakBridge.nativeInit(dataDir)
                if (initResult != 0) {
                    _state.value = EngineState.Error("Failed to initialize espeak-ng")
                    return@withContext
                }

                env = ortEnv
                session = ortSession
                voices = voiceMap
                espeakReady = true
                loadedModel = model
                _state.value = EngineState.Ready
            } catch (e: Exception) {
                _state.value = EngineState.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ── Text Processing (exact port of iOS) ──

    private fun chunkText(text: String, maxLen: Int = 400): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()

        val sentences = trimmed.split(Regex("[.!?]"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.isEmpty()) return listOf(trimmed)

        val chunks = mutableListOf<String>()
        for (sentence in sentences) {
            if (sentence.length <= maxLen) {
                chunks.add(sentence)
            } else {
                var buf = ""
                for (word in sentence.split(" ")) {
                    if (buf.length + word.length + 1 <= maxLen) {
                        buf += (if (buf.isEmpty()) "" else " ") + word
                    } else {
                        if (buf.isNotEmpty()) chunks.add(buf)
                        buf = word
                    }
                }
                if (buf.isNotEmpty()) chunks.add(buf)
            }
        }
        return chunks
    }

    private fun ensurePunctuation(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return t
        val last = t.last()
        return if (".!?,;:".contains(last)) t else "$t,"
    }

    private fun phonemizePreservingPunctuation(text: String): String {
        val punctChars = setOf(';', ':', ',', '.', '!', '?', '\u2014', '\u2026')
        data class Segment(val text: String, val isPunct: Boolean)

        val segments = mutableListOf<Segment>()
        var current = StringBuilder()

        for (ch in text) {
            if (ch in punctChars) {
                if (current.isNotEmpty()) {
                    segments.add(Segment(current.toString(), false))
                    current = StringBuilder()
                }
                segments.add(Segment(ch.toString(), true))
            } else {
                current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            segments.add(Segment(current.toString(), false))
        }

        val result = StringBuilder()
        for (seg in segments) {
            if (seg.isPunct) {
                result.append(seg.text)
            } else {
                val trimmed = seg.text.trim()
                if (trimmed.isEmpty()) {
                    result.append(seg.text)
                    continue
                }
                val phonemes = espeakBridge.nativePhonemize(trimmed)
                result.append(phonemes)
            }
        }

        return result.toString()
    }

    private fun basicEnglishTokenize(text: String): String {
        val tokens = mutableListOf<String>()
        var word = StringBuilder()

        for (ch in text) {
            if (ch.isLetterOrDigit() || ch == '_') {
                word.append(ch)
            } else if (!ch.isWhitespace()) {
                if (word.isNotEmpty()) {
                    tokens.add(word.toString())
                    word = StringBuilder()
                }
                tokens.add(ch.toString())
            } else {
                if (word.isNotEmpty()) {
                    tokens.add(word.toString())
                    word = StringBuilder()
                }
            }
        }
        if (word.isNotEmpty()) tokens.add(word.toString())

        return tokens.joinToString(" ")
    }

    private fun phonemesToTokens(text: String): MutableList<Long> {
        val tokens = mutableListOf<Long>()
        for (codePoint in text.codePoints().toArray()) {
            val id = vocabulary[codePoint]
            if (id != null) {
                tokens.add(id)
            }
        }
        return tokens
    }

    // ── Generation ──

    suspend fun generate(
        text: String,
        voice: String,
        speed: Float = 1.0f
    ): FloatArray {
        val currentSession = session ?: throw IllegalStateException("Engine not ready")
        val voicePositions = voices[voice] ?: throw IllegalArgumentException("Voice '$voice' not found")

        _state.value = EngineState.Generating

        return withContext(Dispatchers.Default) {
            try {
                val chunks = chunkText(text)
                data class PreparedChunk(val tokens: LongArray, val refId: Int)

                val prepared = chunks.map { chunk ->
                    val cleaned = ensurePunctuation(chunk)
                    val phonemes = phonemizePreservingPunctuation(cleaned)
                    val normalized = basicEnglishTokenize(phonemes)
                    val tokens = phonemesToTokens(normalized)

                    // [pad] + tokens + [end-of-text=10] + [pad]
                    tokens.add(0, 0L)
                    tokens.add(10L)
                    tokens.add(0L)

                    val refId = minOf(cleaned.length, voicePositions.size - 1)
                    PreparedChunk(tokens.toLongArray(), refId)
                }

                // Speed prior
                val effectiveSpeed = loadedModel?.speedPriors?.get(voice)?.let {
                    speed * it
                } ?: speed

                val allSamples = mutableListOf<Float>()

                for (chunk in prepared) {
                    val samples = runInference(
                        currentSession,
                        chunk.tokens,
                        voicePositions[chunk.refId].toFloatArray(),
                        effectiveSpeed
                    )
                    allSamples.addAll(samples.toList())
                }

                _state.value = EngineState.Ready
                allSamples.toFloatArray()
            } catch (e: Exception) {
                _state.value = EngineState.Error(e.message ?: "Generation failed")
                throw e
            }
        }
    }

    // ── ONNX Inference ──

    private fun runInference(
        session: OrtSession,
        tokens: LongArray,
        style: FloatArray,
        speed: Float
    ): FloatArray {
        val ortEnv = env ?: throw IllegalStateException("ORT environment not initialized")

        val idTensor = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(tokens),
            longArrayOf(1, tokens.size.toLong())
        )

        val styleTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(style),
            longArrayOf(1, 256)
        )

        val speedTensor = OnnxTensor.createTensor(
            ortEnv,
            FloatBuffer.wrap(floatArrayOf(speed)),
            longArrayOf(1)
        )

        val inputs = mapOf(
            "input_ids" to idTensor,
            "style" to styleTensor,
            "speed" to speedTensor
        )

        val results = session.run(inputs, setOf("waveform"))

        val waveformTensor = results.get("waveform").get() as OnnxTensor
        val rawSamples = waveformTensor.floatBuffer
        val totalSamples = rawSamples.remaining()
        val trimCount = minOf(5000, totalSamples)
        val usableCount = maxOf(0, totalSamples - trimCount)

        if (usableCount == 0) {
            idTensor.close()
            styleTensor.close()
            speedTensor.close()
            results.close()
            return FloatArray(0)
        }

        val samples = FloatArray(usableCount)
        rawSamples.get(samples, 0, usableCount)

        idTensor.close()
        styleTensor.close()
        speedTensor.close()
        results.close()

        return samples
    }

    // ── Vocabulary (exact copy from iOS — order is critical) ──

    companion object {
        private val vocabulary: Map<Int, Long> by lazy {
            val pad = "$"
            val punct = ";:,.!?\u00A1\u00BF\u2014\u2026\"\u00AB\u00BB\u201C\u201D "
            val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
            val ipa = "\u0251\u0250\u0252\u00E6\u0253\u0299\u03B2\u0254\u0255\u00E7" +
                "\u0257\u0256\u00F0\u02A4\u0259\u0258\u025A\u025B\u025C\u025D" +
                "\u025E\u025F\u0284\u0261\u0260\u0262\u029B\u0266\u0267\u0127" +
                "\u0265\u029C\u0268\u026A\u029D\u026D\u026C\u026B\u026E\u029F" +
                "\u0271\u026F\u0270\u014B\u0273\u0272\u0274\u00F8\u0275\u0278" +
                "\u03B8\u0153\u0276\u0298\u0279\u027A\u027E\u027B\u0280\u0281" +
                "\u027D\u0282\u0283\u0288\u02A7\u0289\u028A\u028B\u2C71\u028C" +
                "\u0263\u0264\u028D\u03C7\u028E\u028F\u0291\u0290\u0292\u0294" +
                "\u02A1\u0295\u02A2\u01C0\u01C1\u01C2\u01C3\u02C8\u02CC\u02D0" +
                "\u02D1\u02BC\u02B4\u02B0\u02B1\u02B2\u02B7\u02E0\u02E4\u02DE" +
                "\u2193\u2191\u2192\u2197\u2198\u2018\u0329\u2019\u1D7B"

            val all = pad + punct + letters + ipa
            val map = mutableMapOf<Int, Long>()
            var index = 0L
            for (codePoint in all.codePoints().toArray()) {
                map[codePoint] = index
                index++
            }
            map
        }
    }
}
