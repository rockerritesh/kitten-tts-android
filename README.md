# KittenTTS Android — IELTS Speaking & Reading Trainer

An on-device English learning app for **IELTS speaking and reading practice**, built on
[KittenML](https://github.com/KittenML) neural TTS plus an on-device **Whisper** speech
recognizer. Read a passage aloud, and the app transcribes your voice and scores your
**reading accuracy (Word Error Rate)** and an **IELTS band estimate** across the four
official criteria — all fully offline, no internet required after install.

It also keeps the original **Voice Studio** (text-to-speech) so you can hear a model read
any passage before you try it yourself.

## How it works

The app has three tabs: **Reading**, **Speaking**, and **Voice Studio**.

**Reading (read-aloud)**
1. Pick from 200 bundled passages, graded across IELTS bands and topics.
2. **Listen** — a neural voice reads the passage aloud (KittenTTS).
3. **Record & read aloud** — your microphone audio is captured at 16 kHz.
4. **Score** — Whisper-tiny.en (ONNX, on-device) transcribes your speech; it is aligned
   word-by-word against the passage to compute Word Error Rate, reading accuracy, and an
   IELTS-style band breakdown.

**Speaking (free response)**
1. Pick an IELTS-style prompt (Part 1 questions, Part 2 cue cards, Part 3 discussion).
2. **Record your answer** in your own words.
3. **Score** — Whisper transcribes it, then a fluency analyzer (pace, pauses, fillers) and
   a free-speech scorer estimate all four official criteria — now genuinely from *your own*
   vocabulary and grammar: **Task Response · Fluency & Coherence · Lexical Resource ·
   Grammatical Range & Accuracy**, plus an overall band. It also shows which prompt points
   you covered and a transcript.

## Screenshots

<p align="center">
  <img src="screenshots/main-screen.jpg" width="300" alt="Main Screen" />
  &nbsp;&nbsp;&nbsp;
  <img src="screenshots/generated-audio.jpg" width="300" alt="Generated Audio with Play and Download" />
</p>

## Features

**IELTS reading practice**
- 200 bundled read-aloud passages graded by IELTS band (4.5–9) and topic
- On-device speech recognition (Whisper-tiny.en, ONNX) — fully offline
- Word Error Rate scoring with word-level highlighting (correct / misread / skipped)
- IELTS band estimate across the four official criteria
- "Listen" button — hear a neural voice read the passage first

**IELTS speaking practice (free response)**
- IELTS-style prompts across Part 1, Part 2 (cue cards), and Part 3
- Answer in your own words; Whisper transcribes on-device
- Fluency analysis from the audio: words-per-minute, long pauses, filler words
- Full four-criteria band estimate from your own vocabulary and grammar
- Prompt-point coverage feedback + transcript

**Voice Studio (text-to-speech)**
- 3 model sizes: **Nano** (15M), **Micro** (40M), **Mini** (80M)
- 8 voices: Rosie, Bella, Jasper, Luna, Bruno, Hugo, Kiki, Leo
- Adjustable speed (0.5x – 2.0x)
- Download generated audio as WAV to device
- **Long text / large context support** — paste entire articles, stories, or paragraphs. Text is automatically split into chunks at sentence boundaries, each chunk is synthesized independently, and the audio is seamlessly concatenated into a single output.
- 100% on-device inference via ONNX Runtime
- Dark theme UI matching the iOS version

## Architecture

### Speech recognition + scoring (IELTS practice)

```
Microphone (16 kHz mono PCM)
  → Log-mel spectrogram (80 bins, Whisper spec)
  → Whisper encoder ONNX  → hidden states
  → Whisper decoder ONNX  → greedy token decode (30 s windows)
  → Byte-level BPE detokenize → transcript
  → WER alignment vs. passage → accuracy + highlighting
  → IELTS four-criteria band estimate
```

The Whisper ONNX models (quantized whisper-tiny.en, ~41 MB) are **bundled in the APK**
(`assets/asr/`) and run on the same ONNX Runtime used for TTS. Fetch them with
[`tools/download_whisper_onnx.py`](tools/download_whisper_onnx.py) (no PyTorch needed) —
see [`app/src/main/assets/asr/README.md`](app/src/main/assets/asr/README.md). The pipeline
(mel spectrogram + greedy decode + byte-level tokenizer) is validated to match the
reference HuggingFace implementation exactly.

> The IELTS band estimate is an honest *approximation* derived from reading accuracy,
> coverage, and pace. A read-aloud task cannot fully measure free-speech lexical/grammatical
> range; those proxies are documented in `IeltsScorer.kt`.

### Text-to-speech (Voice Studio)

```
Text Input (any length)
  → Auto-chunking (max 400 chars at sentence boundaries)
  → Per-chunk: Punctuation normalization
  → Per-chunk: espeak-ng phonemization (JNI/NDK)
  → Per-chunk: IPA tokenization (178-token vocabulary)
  → Per-chunk: ONNX Runtime inference (24kHz Float32 PCM)
  → Concatenate all chunk audio
  → AudioTrack playback / WAV download
```

### Tech Stack

| Component | Technology |
|-----------|-----------|
| UI | Kotlin + Jetpack Compose + Navigation Compose |
| ML Inference | ONNX Runtime Android (TTS + Whisper ASR) |
| Speech recognition | Whisper-tiny.en (ONNX, on-device) |
| Phonemization | espeak-ng (C via JNI/NDK) |
| Audio | AudioTrack playback (24kHz) · AudioRecord capture (16kHz) |
| Scoring | Word Error Rate (Levenshtein) + IELTS band heuristics |
| Build | Gradle KTS, Android NDK, CMake |

## Download

Get the latest APK from [Releases](https://github.com/rockerritesh/kitten-tts-android/releases).

## Building from Source

### Prerequisites

- Android Studio (latest)
- Android SDK 34
- Android NDK 27+
- JDK 17

### Steps

1. Clone the repo:
   ```bash
   git clone https://github.com/rockerritesh/kitten-tts-android.git
   cd kitten-tts-android
   git lfs pull
   ```

2. Build espeak-ng native library:
   ```bash
   ./build-espeak-ng.sh
   ```

3. Fetch the Whisper ASR model into `assets/asr/` (needed for speaking/reading scoring):
   ```bash
   python3 tools/download_whisper_onnx.py
   ```

4. Open in Android Studio and build, or:
   ```bash
   ./gradlew assembleDebug
   ```

## Project Structure

```
app/src/main/
├── java/com/kittenml/tts/
│   ├── MainActivity.kt           # Entry point + bottom-nav (Practice / Voice Studio)
│   ├── engine/                   # TTS
│   │   ├── KittenTTSEngine.kt    # Core TTS pipeline
│   │   ├── EspeakBridge.kt       # JNI wrapper
│   │   └── AudioPlayer.kt        # AudioTrack playback
│   ├── asr/                      # Speech recognition
│   │   ├── AudioRecorder.kt      # 16 kHz mic capture
│   │   ├── MelSpectrogram.kt     # 80-bin log-mel features
│   │   ├── WhisperTokenizer.kt   # byte-level BPE decode
│   │   ├── WhisperAsrEngine.kt   # encoder/decoder ONNX inference
│   │   └── AsrState.kt
│   ├── scoring/
│   │   ├── WerScorer.kt          # Word Error Rate + alignment
│   │   ├── ScoreResult.kt
│   │   ├── IeltsScorer.kt        # read-aloud four-criteria estimate
│   │   ├── IeltsAssessment.kt
│   │   ├── BandUtil.kt           # quality → IELTS band helpers
│   │   ├── FluencyAnalyzer.kt    # pace / pauses / fillers from audio
│   │   └── FreeSpeechScorer.kt   # free-response four-criteria estimate
│   ├── data/
│   │   ├── Paragraph.kt / ParagraphRepository.kt
│   │   └── SpeakingPrompt.kt / SpeakingPromptRepository.kt
│   ├── ui/
│   │   ├── theme/                 # Dark theme (Color, Theme, Type)
│   │   └── screen/
│   │       ├── TTSScreen.kt      # Voice Studio UI
│   │       ├── TTSViewModel.kt
│   │       ├── practice/         # reading: list + record/score + ViewModel
│   │       └── speaking/         # free speaking: list + record/score + ViewModel
│   └── model/
│       ├── TTSModel.kt           # Model enum
│       └── EngineState.kt        # Engine state
├── cpp/
│   ├── espeak-bridge.c           # C phonemization bridge
│   ├── espeak-jni.c              # JNI glue layer
│   └── CMakeLists.txt            # NDK build config
└── assets/
    ├── models/                    # TTS ONNX model files (~168 MB)
    ├── voices/                    # Voice embedding JSONs (~51 MB)
    ├── espeak-ng-data/            # Phoneme data files (~1 MB)
    ├── asr/                       # Whisper ONNX + vocab (see asr/README.md)
    └── ielts/
        ├── paragraphs.json        # 200 read-aloud passages
        └── speaking_prompts.json  # free-speaking prompts (Parts 1–3)
```

## IELTS passages

The bundled passages live in [`app/src/main/assets/ielts/paragraphs.json`](app/src/main/assets/ielts/paragraphs.json),
each with `id`, `title`, `topic`, `band`, and `text`. The app ships with 200 graded
passages across 15 topics and IELTS bands 4.5–9.0. Add or edit passages by changing this
file — no code change is needed, as the list is read at runtime. Free-speaking prompts
live alongside it in `speaking_prompts.json`.

## License

Apache 2.0
