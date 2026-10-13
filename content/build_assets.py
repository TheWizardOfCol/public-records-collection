#!/usr/bin/env python3
"""Optimize the Public Records Collection's raw assets for web delivery.

Sticker art is downscaled and re-encoded to WebP so it can be served to browser
players (and loaded by the Godot host over HTTP). Book texts are gzipped so the
server can carry the full corpus without bloating the repo or the `.elm`.

Raw sources under ``/workspace/Prompts`` are large and are never committed; this
script writes the small, web-ready results under ``content/assets/``.

Usage:
    python3 content/build_assets.py [--source /workspace/Prompts] [--out content/assets]
"""
from __future__ import annotations

import argparse
import gzip
import json
import os
import re
import sys
import unicodedata

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install --user --break-system-packages Pillow")

# Files that are clearly not usable sticker art (full-resolution museum scans,
# stray uploads). Kept out of the optimized set; the raw files stay untracked.
EXCLUDE_FILES = {
    "Hans_Holbein_the_Younger_-_The_Ambassadors_-_Google_Art_Project.png",
}
# Safety net: anything this large is almost certainly a misfiled master, not art
# meant for a party game.
MAX_SOURCE_BYTES = 40_000_000

STICKER_DIRS = ("Stickers", "Stickers 2")
MAX_DIMENSION = 768
WEBP_QUALITY = 82


def slugify(name: str) -> str:
    text = unicodedata.normalize("NFKD", name)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return re.sub(r"-+", "-", text)


def optimize_stickers(source: str, out_dir: str):
    os.makedirs(out_dir, exist_ok=True)
    index = []
    skipped = []
    for sub in STICKER_DIRS:
        d = os.path.join(source, sub)
        if not os.path.isdir(d):
            continue
        for name in sorted(os.listdir(d)):
            src = os.path.join(d, name)
            if not os.path.isfile(src):
                continue
            if not name.lower().endswith((".png", ".jpg", ".jpeg", ".webp")):
                skipped.append((name, os.path.getsize(src), "not an image"))
                continue
            size = os.path.getsize(src)
            if name in EXCLUDE_FILES:
                skipped.append((name, size, "excluded (misfiled master)"))
                continue
            if size > MAX_SOURCE_BYTES:
                skipped.append((name, size, "excluded (oversized master)"))
                continue
            slug = slugify(os.path.splitext(name)[0])
            dst = os.path.join(out_dir, f"{slug}.webp")
            with Image.open(src) as im:
                im = im.convert("RGBA") if "A" in im.getbands() else im.convert("RGB")
                im.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.Resampling.LANCZOS)
                im.save(dst, "WEBP", quality=WEBP_QUALITY, method=6)
                width, height = im.size
            index.append({
                "slug": slug,
                "file": os.path.basename(dst),
                "source": f"{sub}/{name}",
                "width": width,
                "height": height,
                "bytes": os.path.getsize(dst),
            })
    index.sort(key=lambda e: e["slug"])
    with open(os.path.join(out_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
        f.write("\n")
    total = sum(e["bytes"] for e in index)
    print(f"stickers: {len(index)} optimized, {total / 1e6:.2f} MB total")
    for name, size, why in skipped:
        print(f"    skipped {name} ({size / 1e6:.1f} MB): {why}")


# --- Photographed props + textures (the "Textures and images" scans) ----------
# These are the hand-made assets the host and players dress the UI with:
# index-card buttons, leatherbound book covers, and full-bleed backgrounds.
# Cut-outs keep their alpha; backgrounds are downscaled but stay opaque.
ART_MAX_DIMENSION = {
    "notecard": 700,
    "book": 900,
    "background": 1600,
    "object": 1200,
}
ART_WEBP_QUALITY = 82


def art_role(name: str) -> str:
    low = name.lower()
    if "notecard" in low:
        return "notecard"
    if "leatherbound" in low:
        return "book"
    if any(
        k in low
        for k in (
            "painted fabric",
            "pittsburgh map",
            "school report",
            "standup",
            "blank book spread 2",
        )
    ):
        return "background"
    return "object"


def optimize_art(source: str, web_out: str, godot_out: str):
    """Optimize the prop/texture scans for both the web client and the Godot host."""
    if not os.path.isdir(source):
        print(f"art: no source dir at {source}, skipping")
        return
    os.makedirs(web_out, exist_ok=True)
    os.makedirs(godot_out, exist_ok=True)
    index = []
    for name in sorted(os.listdir(source)):
        src = os.path.join(source, name)
        if not os.path.isfile(src) or not name.lower().endswith((".png", ".jpg", ".jpeg", ".webp")):
            continue
        role = art_role(name)
        slug = slugify(os.path.splitext(name)[0])
        with Image.open(src) as im:
            alpha = im.convert("RGBA").getchannel("A").getextrema()[0] < 255
            im = im.convert("RGBA") if alpha else im.convert("RGB")
            im.thumbnail((ART_MAX_DIMENSION[role], ART_MAX_DIMENSION[role]), Image.Resampling.LANCZOS)
            width, height = im.size
            for out in (web_out, godot_out):
                im.save(os.path.join(out, f"{slug}.webp"), "WEBP", quality=ART_WEBP_QUALITY, method=6)
        index.append({
            "slug": slug,
            "file": f"{slug}.webp",
            "role": role,
            "source": name,
            "width": width,
            "height": height,
            "alpha": alpha,
            "bytes": os.path.getsize(os.path.join(web_out, f"{slug}.webp")),
        })
    index.sort(key=lambda e: (e["role"], e["slug"]))
    with open(os.path.join(web_out, "index.json"), "w", encoding="utf-8") as f:
        json.dump(index, f, ensure_ascii=False, indent=2)
        f.write("\n")
    total = sum(e["bytes"] for e in index)
    by_role: dict[str, int] = {}
    for e in index:
        by_role[e["role"]] = by_role.get(e["role"], 0) + 1
    print(f"art: {len(index)} textures {by_role}, {total / 1e6:.2f} MB total")


def gzip_books(books_json: str, books_src: str, out_dir: str):
    os.makedirs(out_dir, exist_ok=True)
    with open(books_json, encoding="utf-8") as f:
        books = json.load(f)
    written = 0
    missing = []
    for book in books:
        text_file = book.get("textFile")
        if not text_file:
            missing.append(book["title"])
            continue
        src = os.path.join(books_src, text_file)
        if not os.path.isfile(src):
            missing.append(book["title"])
            continue
        dst = os.path.join(out_dir, f"{book['slug']}.txt.gz")
        with open(src, "rb") as fin, gzip.open(dst, "wb", compresslevel=9) as fout:
            fout.write(fin.read())
        written += 1
    total = sum(
        os.path.getsize(os.path.join(out_dir, f))
        for f in os.listdir(out_dir)
        if f.endswith(".txt.gz")
    )
    print(f"books: {written} gzipped, {total / 1e6:.2f} MB total")
    if missing:
        print(f"    {len(missing)} books have no source text (e.g. {missing[0]})")


def main():
    here = os.path.dirname(__file__)
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--source", default="/workspace/Prompts")
    ap.add_argument("--out", default=os.path.join(here, "assets"))
    ap.add_argument("--books-json", default=os.path.join(here, "generated", "get-lit", "books.json"))
    ap.add_argument("--art-source", default="/workspace/public-records-collection/Textures and images")
    ap.add_argument("--art-godot-out", default=os.path.join(here, "..", "host", "assets", "art"))
    args = ap.parse_args()

    optimize_stickers(args.source, os.path.join(args.out, "stickers"))
    gzip_books(args.books_json, os.path.join(args.source, "get-lit-content", "books"),
               os.path.join(args.out, "books"))
    optimize_art(args.art_source, os.path.join(args.out, "art"), args.art_godot_out)


if __name__ == "__main__":
    main()
