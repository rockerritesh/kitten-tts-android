#include "espeak-bridge.h"
#include "speak_lib.h"
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

static int initialized = 0;

int espeak_bridge_init(const char *dataPath) {
    if (initialized) return 0;

    // output=0x02 → AUDIO_OUTPUT_RETRIEVAL (phonemes only, no audio)
    // options=0x01 → espeakINITIALIZE_DONT_EXIT
    int result = espeak_Initialize(0x02, 0, dataPath, 0x01);

    if (result == -1) {
        fprintf(stderr, "[espeak-bridge] espeak_Initialize failed for path: %s\n", dataPath);
        return -1;
    }

    // Set voice to American English
    espeak_ERROR voiceErr = espeak_SetVoiceByName("en-us");
    if (voiceErr != EE_OK) {
        fprintf(stderr, "[espeak-bridge] espeak_SetVoiceByName(en-us) failed: %d\n", voiceErr);
        // Try fallback to just "en"
        voiceErr = espeak_SetVoiceByName("en");
        if (voiceErr != EE_OK) {
            fprintf(stderr, "[espeak-bridge] espeak_SetVoiceByName(en) also failed: %d\n", voiceErr);
            return -2;
        }
    }

    initialized = 1;
    return 0;
}

char *espeak_bridge_phonemize(const char *text) {
    if (!initialized || !text || !*text) return NULL;

    size_t bufSize = 4096;
    char *result = (char *)malloc(bufSize);
    if (!result) return NULL;
    result[0] = '\0';
    size_t pos = 0;

    // Make a mutable copy
    size_t textLen = strlen(text);
    char *textCopy = (char *)malloc(textLen + 1);
    if (!textCopy) { free(result); return NULL; }
    strcpy(textCopy, text);

    const void *pText = textCopy;

    // espeakPHONEMES_IPA = 0x02 (IPA UTF-8 output with stress marks)
    int phonememode = 0x02;

    // espeak_TextToPhonemes advances pText through the input.
    // It returns phonemes for one "clause" at a time.
    // Loop until pText reaches the null terminator.
    while (pText != NULL && *(const char *)pText != '\0') {
        const char *phon = espeak_TextToPhonemes(
            &pText,
            0,              // textmode: UTF-8
            phonememode
        );

        if (phon && *phon) {
            size_t phLen = strlen(phon);
            while (pos + phLen + 2 > bufSize) {
                bufSize *= 2;
                char *newBuf = (char *)realloc(result, bufSize);
                if (!newBuf) { free(result); free(textCopy); return NULL; }
                result = newBuf;
            }
            memcpy(result + pos, phon, phLen);
            pos += phLen;
        } else {
            // No more phonemes — break to avoid infinite loop
            break;
        }
    }

    result[pos] = '\0';
    free(textCopy);
    return result;
}
