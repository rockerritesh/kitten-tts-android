package com.kittenml.tts.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Loads the bundled IELTS read-aloud paragraphs from
 * `assets/ielts/paragraphs.json`. The dataset ships inside the APK, so it
 * works fully offline (same "bind data inside the app" approach as the
 * ONNX models).
 */
class ParagraphRepository(private val context: Context) {

    @Volatile
    private var cache: List<Paragraph>? = null

    fun load(): List<Paragraph> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val json = context.assets.open("ielts/paragraphs.json").use {
                it.bufferedReader().readText()
            }
            val type = object : TypeToken<List<Paragraph>>() {}.type
            val list: List<Paragraph> = Gson().fromJson(json, type)
            val sorted = list.sortedBy { it.id }
            cache = sorted
            return sorted
        }
    }

    fun byId(id: Int): Paragraph? = load().firstOrNull { it.id == id }

    /** Distinct topics, for filter chips. */
    fun topics(): List<String> = load().map { it.topic }.distinct().sorted()
}
