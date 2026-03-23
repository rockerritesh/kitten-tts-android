#ifndef ESPEAK_BRIDGE_H
#define ESPEAK_BRIDGE_H

#include <stddef.h>

/// Initialize espeak-ng with the given data path.
/// Returns 0 on success, non-zero on failure.
int espeak_bridge_init(const char *dataPath);

/// Phonemize text using espeak-ng (en-us, with stress, preserve punctuation).
/// Returns a newly allocated string (caller must free) or NULL on error.
char *espeak_bridge_phonemize(const char *text);

#endif
