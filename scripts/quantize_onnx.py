#!/usr/bin/env python3
"""Quantize an ONNX model to INT8 (dynamic) so it fits in a phone APK.

Usage:
    pip install onnx onnxruntime

    python scripts/quantize_onnx.py \\
        --input build/toxic-distilbert/model.onnx \\
        --output app/src/main/assets/models/toxic-distilbert-int8.onnx

After quantizing, update app/src/main/assets/models/registry.json with the
new sha256 and sizeBytes for the entry. The script prints both at the end.
"""

from __future__ import annotations

import argparse
import hashlib
import sys
from pathlib import Path


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="Path to the FP32 ONNX model")
    ap.add_argument("--output", required=True, help="Path to write the INT8 ONNX model")
    ap.add_argument(
        "--per-channel",
        action="store_true",
        help="Per-channel weight quantization (slightly better accuracy, larger file)",
    )
    args = ap.parse_args()

    try:
        from onnxruntime.quantization import QuantType, quantize_dynamic
    except ImportError as e:
        raise SystemExit(
            "onnxruntime is required. Install with: pip install onnxruntime"
        ) from e

    src = Path(args.input)
    dst = Path(args.output)
    dst.parent.mkdir(parents=True, exist_ok=True)

    print(f"[quantize] {src} -> {dst}")
    quantize_dynamic(
        model_input=str(src),
        model_output=str(dst),
        weight_type=QuantType.QInt8,
        per_channel=args.per_channel,
    )

    digest = sha256(dst)
    size = dst.stat().st_size
    print(f"[done]    sha256={digest}")
    print(f"[done]    sizeBytes={size}")
    print()
    print("Update app/src/main/assets/models/registry.json:")
    print(f'  "sha256":    "{digest}",')
    print(f'  "sizeBytes": {size},')
    return 0


if __name__ == "__main__":
    sys.exit(main())
