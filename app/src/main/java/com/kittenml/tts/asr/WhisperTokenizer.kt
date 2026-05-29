package com.kittenml.tts.asr

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.ByteArrayOutputStream

/**
 * Decode-only Whisper / GPT-2 byte-level BPE tokenizer.
 *
 * We only ever turn predicted token ids back into text (we never tokenize
 * input, since the model is fed audio), so this implements just the decode
 * path: token id → byte-level string → raw bytes → UTF-8 text.
 *
 * Loads `assets/asr/vocab.json` (the standard HF `{ "token": id }` map).
 * Special / timestamp tokens are absent from vocab.json, so any id we can't
 * resolve is simply dropped during decoding.
 */
class WhisperTokenizer private constructor(
    private val idToToken: Map<Int, String>,
    private val byteDecoder: Map<Char, Int>
) {

    fun decode(ids: List<Int>): String {
        val out = ByteArrayOutputStream()
        for (id in ids) {
            val tok = idToToken[id] ?: continue // special/timestamp token
            for (ch in tok) {
                val b = byteDecoder[ch] ?: continue
                out.write(b)
            }
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    companion object {
        fun fromAssets(context: Context, path: String = "asr/vocab.json"): WhisperTokenizer {
            val json = context.assets.open(path).use { it.bufferedReader().readText() }
            val type = object : TypeToken<Map<String, Int>>() {}.type
            val vocab: Map<String, Int> = Gson().fromJson(json, type)
            val idToToken = HashMap<Int, String>(vocab.size)
            for ((tok, id) in vocab) idToToken[id] = tok
            return WhisperTokenizer(idToToken, buildByteDecoder())
        }

        /** Reverse of GPT-2's bytes_to_unicode(): printable-char → original byte. */
        private fun buildByteDecoder(): Map<Char, Int> {
            val bs = ArrayList<Int>()
            for (b in '!'.code..'~'.code) bs.add(b)
            for (b in '¡'.code..'¬'.code) bs.add(b)
            for (b in '®'.code..'ÿ'.code) bs.add(b)
            val cs = ArrayList<Int>(bs)
            var n = 0
            for (b in 0..255) {
                if (b !in bs) {
                    bs.add(b)
                    cs.add(256 + n)
                    n++
                }
            }
            val map = HashMap<Char, Int>(bs.size)
            for (i in bs.indices) map[cs[i].toChar()] = bs[i]
            return map
        }
    }
}
