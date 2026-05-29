#!/usr/bin/env python3
"""
Download a pre-converted Whisper-tiny.en ONNX model from Hugging Face and lay
it down in app/src/main/assets/asr/. No PyTorch / optimum needed — these files
are already exported (by the transformers.js / onnx-community project).

Default precision is `quantized` (int8) — the small build, ~3-4x smaller than
fp32, ideal for bundling in an APK. Pass --precision fp32 for full precision.

Usage:
  python3 tools/download_whisper_onnx.py                  # quantized (small)
  python3 tools/download_whisper_onnx.py --precision fp32
"""
import argparse
import json
import os
import urllib.request
from pathlib import Path

REPO = "onnx-community/whisper-tiny.en"
BASE = f"https://huggingface.co/{REPO}/resolve/main"
REPO_ROOT = Path(__file__).resolve().parents[1]
ASSETS = REPO_ROOT / "app" / "src" / "main" / "assets" / "asr"


def fetch(url: str, dest: Path) -> None:
    print(f"  ↓ {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "kitten-tts-android"})
    with urllib.request.urlopen(req) as r, open(dest, "wb") as f:
        while True:
            chunk = r.read(1 << 20)
            if not chunk:
                break
            f.write(chunk)
    print(f"    → {dest.name}  ({dest.stat().st_size / 1e6:.1f} MB)")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--precision", default="quantized",
                    choices=["quantized", "int8", "uint8", "fp16", "fp32"])
    args = ap.parse_args()

    suffix = "" if args.precision == "fp32" else f"_{args.precision}"
    ASSETS.mkdir(parents=True, exist_ok=True)

    print(f"Downloading whisper-tiny.en ({args.precision}) from {REPO}")
    fetch(f"{BASE}/onnx/encoder_model{suffix}.onnx", ASSETS / "whisper_encoder.onnx")
    fetch(f"{BASE}/onnx/decoder_model{suffix}.onnx", ASSETS / "whisper_decoder.onnx")
    fetch(f"{BASE}/vocab.json", ASSETS / "vocab.json")

    # Read token ids straight from the model configs (no guessing).
    cfg = json.loads(urllib.request.urlopen(f"{BASE}/config.json").read())
    gen = json.loads(urllib.request.urlopen(f"{BASE}/generation_config.json").read())

    sot = gen.get("decoder_start_token_id", cfg.get("decoder_start_token_id", 50257))
    eos = gen.get("eos_token_id", cfg.get("eos_token_id", 50256))

    prompt = [int(sot)]
    for pair in sorted(gen.get("forced_decoder_ids") or [], key=lambda x: x[0]):
        if pair[1] is not None:
            prompt.append(int(pair[1]))
    no_ts = gen.get("no_timestamps_token_id")
    if no_ts is not None and int(no_ts) not in prompt:
        prompt.append(int(no_ts))
    if len(prompt) == 1:               # fall back to tiny.en default
        prompt.append(50362)

    asr_config = {
        "encoderModel": "whisper_encoder.onnx",
        "decoderModel": "whisper_decoder.onnx",
        "vocabFile": "vocab.json",
        "decoderStartIds": prompt,
        "eosTokenId": int(eos),
        "maxNewTokens": 224,
        "inputFeaturesName": "input_features",
        "encoderHiddenName": "last_hidden_state",
        "decoderInputIdsName": "input_ids",
        "decoderEncoderHiddenName": "encoder_hidden_states",
        "decoderLogitsName": "logits",
    }
    (ASSETS / "asr_config.json").write_text(json.dumps(asr_config, indent=2))
    print(f"  → asr_config.json  (decoderStartIds={prompt}, eos={eos})")

    print("\nDone. Files in", ASSETS)
    for f in sorted(ASSETS.iterdir()):
        if f.is_file():
            print(f"  {f.name:26s} {f.stat().st_size/1e6:7.2f} MB")


if __name__ == "__main__":
    main()
