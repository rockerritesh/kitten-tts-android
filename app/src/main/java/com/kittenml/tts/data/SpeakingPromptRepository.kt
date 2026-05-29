package com.kittenml.tts.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** Loads bundled IELTS speaking prompts from `assets/ielts/speaking_prompts.json`. */
class SpeakingPromptRepository(private val context: Context) {

    @Volatile
    private var cache: List<SpeakingPrompt>? = null

    fun load(): List<SpeakingPrompt> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val json = context.assets.open("ielts/speaking_prompts.json").use {
                it.bufferedReader().readText()
            }
            val type = object : TypeToken<List<SpeakingPrompt>>() {}.type
            val list: List<SpeakingPrompt> = Gson().fromJson(json, type)
            val sorted = list.sortedWith(compareBy({ it.part }, { it.id }))
            cache = sorted
            return sorted
        }
    }

    fun byId(id: Int): SpeakingPrompt? = load().firstOrNull { it.id == id }
}
