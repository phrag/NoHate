#!/usr/bin/env python3
"""Export a Hugging Face text-classification model to ONNX with an embedded tokenizer.

Usage:
    pip install -U "optimum[exporters]" onnx onnxruntime onnxruntime-extensions transformers

    python scripts/export_onnx.py \\
        --model martin-ha/toxic-comment-model \\
        --output build/toxic-distilbert/

Then quantize with scripts/quantize_onnx.py and copy the result into
app/src/main/assets/models/ (see docs/MODELS.md).

This script intentionally does the simple thing — `optimum-cli export onnx`
plus a follow-up step that wraps the model with a tokenizer custom op so
the on-device classifier can feed raw strings.
"""

from __future__ import annotations

import argparse
import shutil
import subprocess
import sys
from pathlib import Path


def export(model_id: str, out_dir: Path, task: str = "text-classification") -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        "optimum-cli",
        "export",
        "onnx",
        "--model",
        model_id,
        "--task",
        task,
        str(out_dir),
    ]
    print("[export]", " ".join(cmd))
    subprocess.run(cmd, check=True)
    onnx_path = out_dir / "model.onnx"
    if not onnx_path.exists():
        raise SystemExit(f"expected {onnx_path} to exist after export")
    return onnx_path


def embed_tokenizer(model_id: str, onnx_path: Path) -> Path:
    """Wrap the model with a BertTokenizer / SentencePieceTokenizer custom op so
    the runtime can feed a raw string tensor. Requires onnxruntime-extensions."""
    try:
        from onnxruntime_extensions.tools import pre_post_processing as ppp
        from transformers import AutoTokenizer
    except ImportError as e:
        raise SystemExit(
            "onnxruntime-extensions and transformers are required. "
            "Install with: pip install onnxruntime-extensions transformers"
        ) from e

    tokenizer = AutoTokenizer.from_pretrained(model_id)
    wrapped_path = onnx_path.with_name(onnx_path.stem + "-tok.onnx")

    pipeline = ppp.PrePostProcessor(
        inputs=[ppp.create_named_value("text", "string", ["N"])]
    )
    pipeline.add_pre_processing([
        ppp.Tokenize(tokenizer),
    ])

    print(f"[embed]   wrapping {onnx_path.name} with tokenizer for {model_id}")
    pipeline.run(onnx_path, wrapped_path)
    return wrapped_path


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True, help="Hugging Face model id")
    ap.add_argument("--output", required=True, help="Output directory")
    ap.add_argument("--task", default="text-classification")
    ap.add_argument(
        "--skip-tokenizer",
        action="store_true",
        help="Export model only; do not embed the tokenizer custom op",
    )
    args = ap.parse_args()

    out_dir = Path(args.output)
    onnx_path = export(args.model, out_dir, args.task)
    if not args.skip_tokenizer:
        wrapped = embed_tokenizer(args.model, onnx_path)
        # Keep the wrapped artefact as the canonical model.onnx so the
        # quantize step + asset bundling don't need to know about the suffix.
        target = onnx_path
        shutil.move(str(wrapped), target)
        print(f"[done]    {target}")
    else:
        print(f"[done]    {onnx_path} (no tokenizer embedded)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
