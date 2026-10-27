#!/usr/bin/env python3
"""Build the Cryptid Cinema SFX library (the U.F.S.F.X. hand contents).

Reads the raw sound-effect mp3s from the raw-materials folder, slugs their
file stamps (filename = the sound's display name), stages the untouched mp3s
into the Element's served static tree, and bakes the full library as the
UFSFX deck of every episode marked with type UFSFX (the round deals 5-card
hands out of it, exactly like FOUNDTRACK).

Usage:
    python3 content/build_sfx.py [--source "…/sound - complete"]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import unicodedata

SFX_DIR = "sfx"
IMAGE_EXTS = (".mp3",)


def slugify(name: str) -> str:
    text = unicodedata.normalize("NFKD", name)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return re.sub(r"-+", "-", text)


def build(source: str, generated: str, static: str) -> None:
    entries = []
    out_dir = os.path.join(static, "assets", "cryptid", SFX_DIR)
    os.makedirs(out_dir, exist_ok=True)
    for name in sorted(os.listdir(source)):
        base, ext = os.path.splitext(name)
        if not ext.lower() in IMAGE_EXTS or not os.path.isfile(os.path.join(source, name)):
            continue
        slug = slugify(base)
        if not slug:
            continue
        dst = os.path.join(out_dir, f"{slug}.mp3")
        shutil.copyfile(os.path.join(source, name), dst)
        entries.append({
            "id": slug,
            "label": base.strip(),
            "audioUrl": f"/app/static/collection/assets/cryptid/{SFX_DIR}/{slug}.mp3",
            "bytes": os.path.getsize(dst),
        })

    deck_path = os.path.join(generated, "cryptid", "sfx.json")
    os.makedirs(os.path.dirname(deck_path), exist_ok=True)
    with open(deck_path, "w", encoding="utf-8") as f:
        json.dump({"deck": entries}, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # Bake the deck into the UFSFX rounds of the shipped episodes.
    episodes_path = os.path.join(generated, "cryptid", "episodes.json")
    with open(episodes_path, encoding="utf-8") as f:
        doc = json.load(f)
    eps = doc.get("episodes") if isinstance(doc, dict) else doc
    touched = 0
    for episode in eps:
        for round_def in episode.get("rounds", []):
            if round_def.get("type") == "UFSFX":
                round_def["choices"] = []
                round_def["deck"] = [{k: e[k] for k in ("id", "label", "audioUrl")} for e in entries]
                touched += 1
    with open(episodes_path, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")

    # Mirror the episodes.json into every consumer's copy.
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    for rel in (
        "host/assets/cryptid/episodes.json",
        "server/element/src/main/resources/content/cryptid/episodes.json",
        "server/element/src/main/static/content/cryptid/episodes.json",
        "server/element/src/main/static/assets/cryptid/episodes.json",
    ):
        shutil.copyfile(episodes_path, os.path.join(root, rel))
        print(f"    episodes.json -> {rel}")
    total = sum(e["bytes"] for e in entries)
    print(f"sfx: {len(entries)} sounds, {total / 1e6:.2f} MB, {touched} UFSFX rounds decked")


def main() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default="/workspace/Cryptid Cinema raw materials/sound - complete")
    ap.add_argument("--generated", default=os.path.join(here, "generated"))
    ap.add_argument("--static", default=os.path.join(here, "..", "server", "element", "src", "main", "static"))
    args = ap.parse_args()
    build(args.source, args.generated, args.static)


if __name__ == "__main__":
    main()
