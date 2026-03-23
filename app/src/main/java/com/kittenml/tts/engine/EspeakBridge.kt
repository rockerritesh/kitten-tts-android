package com.kittenml.tts.engine

class EspeakBridge {
    companion object {
        init {
            System.loadLibrary("espeak-jni")
        }
    }

    external fun nativeInit(dataPath: String): Int
    external fun nativePhonemize(text: String): String
}
