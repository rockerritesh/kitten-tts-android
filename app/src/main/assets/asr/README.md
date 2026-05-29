# ASR models (speech recognition)

The IELTS speaking practice feature scores your reading by transcribing your
voice with an on-device **Whisper-tiny.en** ONNX model and comparing the
transcript to the passage (Word Error Rate). The models are **bundled inside
the APK** and run fully offline — the same approach as the TTS models.

This folder must contain four files before scoring will work. The default is
the **quantized (int8) whisper-tiny.en** — the small build, validated to match
the reference feature extractor exactly and to transcribe correctly:

| File | What it is | Approx size |
|------|------------|-------------|
| `whisper_encoder.onnx` | audio (log-mel) → hidden states | ~10 MB |
| `whisper_decoder.onnx` | hidden states + tokens → logits | ~30 MB |
| `vocab.json` | byte-level BPE vocabulary (token → id) | ~1 MB |
| `asr_config.json` | I/O names, decoder prompt, special token ids | < 1 KB |

## How to get them (recommended: direct download, no PyTorch)

The simplest path downloads a pre-converted model from Hugging Face
(`onnx-community/whisper-tiny.en`) — no torch/optimum needed:

```bash
python3 tools/download_whisper_onnx.py                 # quantized (small, default)
python3 tools/download_whisper_onnx.py --precision fp32  # full precision (larger)
```

This writes all four files here and reads the decoder prompt + special-token
ids straight from the model config. The non-merged `decoder_model` it fetches
matches the engine's no-KV-cache greedy decode loop.

## Alternative: export it yourself

`tools/export_whisper_onnx.py` exports via `optimum` (note: needs
`optimum<2.0`, since 2.x removed the `exporters` module). The direct download
above is preferred.

Track the `.onnx` files with **git-lfs** (the repo already uses it for the TTS
models):

```bash
git lfs track "app/src/main/assets/asr/*.onnx"
git add .gitattributes app/src/main/assets/asr
```

## If the files are missing

The app still builds and runs. Recording works, but the engine reports
`Unavailable` and the Practice screen shows a hint instead of a score. Drop
the files in and rebuild to enable scoring.

## Notes

- `aaptOptions { noCompress("onnx") }` in `app/build.gradle.kts` already keeps
  the ONNX files uncompressed in the APK.
- Audio is captured at 16 kHz mono and processed in 30-second windows, so
  passages of any length are supported.
- For higher accuracy at the cost of size/speed, swap in `whisper-base.en`;
  the Kotlin side reads dimensions from the model, so no code change is needed.
