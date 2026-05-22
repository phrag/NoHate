#!/usr/bin/env python3
"""Export a Hugging Face text-classification model to ONNX.

Usage:
    pip install -U "optimum[exporters]" onnx onnxruntime onnxruntime-extensions transformers

    python scripts/export_onnx.py \\
        --model martin-ha/toxic-comment-model \\
        --output build/toxic-distilbert/

Then quantize with scripts/quantize_onnx.py and copy the result into
app/src/main/assets/models/ (see docs/MODELS.md).

We call optimum.exporters.onnx.main_export() directly instead of shelling out
to `optimum-cli` — the CLI arg surface has drifted across releases and is
fragile; the Python entry point is stable.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path


def export(model_id: str, out_dir: Path, task: str = "text-classification") -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    try:
        from optimum.exporters.onnx import main_export
    except ImportError as e:
        raise SystemExit(
            "optimum with the ONNX exporter is required. "
            "Install with: pip install -U \"optimum[exporters]\""
        ) from e

    print(f"[export] main_export(model={model_id!r}, output={str(out_dir)!r}, task={task!r})")
    main_export(
        model_name_or_path=model_id,
        output=str(out_dir),
        task=task,
    )
    onnx_path = out_dir / "model.onnx"
    if not onnx_path.exists():
        # Some optimum versions name the artefact after the model.
        candidates = list(out_dir.glob("*.onnx"))
        if len(candidates) == 1:
            candidates[0].rename(onnx_path)
        else:
            raise SystemExit(
                f"expected exactly one .onnx in {out_dir} after export, "
                f"found: {[p.name for p in candidates]}"
            )
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
        try:
            wrapped = embed_tokenizer(args.model, onnx_path)
        except Exception as e:
            print(
                f"[warn]    tokenizer embedding failed ({e}); keeping the "
                f"plain ONNX model. You can re-run with --skip-tokenizer "
                f"and tokenize on the Kotlin side instead."
            )
            print(f"[done]    {onnx_path} (no tokenizer embedded)")
            return 0
        # Keep the wrapped artefact as the canonical model.onnx so the
        # quantize step + asset bundling don't need to know about the suffix.
        shutil.move(str(wrapped), onnx_path)
        print(f"[done]    {onnx_path}")
    else:
        print(f"[done]    {onnx_path} (no tokenizer embedded)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
