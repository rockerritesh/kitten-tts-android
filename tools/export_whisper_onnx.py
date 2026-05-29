#!/usr/bin/env python3
"""
Export Whisper-tiny.en to ONNX and lay down everything the Android ASR engine
needs in app/src/main/assets/asr/.

It produces:
  whisper_encoder.onnx   input_features [1,80,3000]  -> last_hidden_state
  whisper_decoder.onnx   input_ids, encoder_hidden_states -> logits
  vocab.json             the byte-level BPE vocab (token -> id)
  asr_config.json        I/O names + decoder prompt + special token ids,
                         read straight from the model config so nothing is
                         hard-coded incorrectly.

Usage:
  pip install "optimum[exporters]" onnx onnxruntime transformers
  python tools/export_whisper_onnx.py                 # whisper-tiny.en (default)
  python tools/export_whisper_onnx.py --model openai/whisper-base.en

These ONNX files are bundled inside the APK (same approach as the TTS models),
so recognition runs fully offline on-device.
"""
import argparse
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ASSETS_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "asr"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", default="openai/whisper-tiny.en")
    ap.add_argument("--out", default=str(ASSETS_DIR))
    args = ap.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as tmp:
        tmp_dir = Path(tmp)
        print(f"[1/4] Exporting {args.model} to ONNX via optimum…")
        subprocess.run(
            [
                sys.executable, "-m", "optimum.exporters.onnx",
                "--model", args.model,
                "--task", "automatic-speech-recognition",
                str(tmp_dir),
            ],
            check=True,
        )

        # optimum emits encoder_model.onnx / decoder_model.onnx (+ _with_past).
        print("[2/4] Copying encoder/decoder models…")
        shutil.copy(tmp_dir / "encoder_model.onnx", out_dir / "whisper_encoder.onnx")
        shutil.copy(tmp_dir / "decoder_model.onnx", out_dir / "whisper_decoder.onnx")

        print("[3/4] Copying tokenizer vocab…")
        shutil.copy(tmp_dir / "vocab.json", out_dir / "vocab.json")

        print("[4/4] Writing asr_config.json from the model config…")
        from transformers import WhisperForConditionalGeneration, WhisperTokenizer

        model = WhisperForConditionalGeneration.from_pretrained(args.model)
        tok = WhisperTokenizer.from_pretrained(args.model)
        cfg = model.config
        gen = model.generation_config

        sot = cfg.decoder_start_token_id
        eos = cfg.eos_token_id
        no_ts = tok.convert_tokens_to_ids("<|notimestamps|>")

        # Build the decoder prompt: start token, then any forced ids, then the
        # no-timestamps token if not already present.
        prompt = [sot]
        forced = getattr(gen, "forced_decoder_ids", None) or []
        for _, token_id in sorted(forced, key=lambda x: x[0]):
            prompt.append(int(token_id))
        if no_ts not in prompt:
            prompt.append(int(no_ts))

        asr_config = {
            "encoderModel": "whisper_encoder.onnx",
            "decoderModel": "whisper_decoder.onnx",
            "vocabFile": "vocab.json",
            "decoderStartIds": [int(x) for x in prompt],
            "eosTokenId": int(eos),
            "maxNewTokens": 224,
            "inputFeaturesName": "input_features",
            "encoderHiddenName": "last_hidden_state",
            "decoderInputIdsName": "input_ids",
            "decoderEncoderHiddenName": "encoder_hidden_states",
            "decoderLogitsName": "logits",
        }
        (out_dir / "asr_config.json").write_text(json.dumps(asr_config, indent=2))

    print("\nDone. Wrote to", out_dir)
    for f in sorted(out_dir.iterdir()):
        size = f.stat().st_size / (1024 * 1024)
        print(f"  {f.name:28s} {size:7.1f} MB")
    print("\nNote: track the .onnx files with git-lfs (the repo already uses it).")


if __name__ == "__main__":
    main()
