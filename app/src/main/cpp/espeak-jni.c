#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include "espeak-bridge.h"

JNIEXPORT jint JNICALL
Java_com_kittenml_tts_engine_EspeakBridge_nativeInit(
    JNIEnv *env, jobject thiz, jstring dataPath) {

    const char *path = (*env)->GetStringUTFChars(env, dataPath, NULL);
    int result = espeak_bridge_init(path);
    (*env)->ReleaseStringUTFChars(env, dataPath, path);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_kittenml_tts_engine_EspeakBridge_nativePhonemize(
    JNIEnv *env, jobject thiz, jstring text) {

    const char *inputText = (*env)->GetStringUTFChars(env, text, NULL);
    char *phonemes = espeak_bridge_phonemize(inputText);
    (*env)->ReleaseStringUTFChars(env, text, inputText);

    if (phonemes == NULL) {
        return (*env)->NewStringUTF(env, "");
    }

    jstring result = (*env)->NewStringUTF(env, phonemes);
    free(phonemes);
    return result;
}
