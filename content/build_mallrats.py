#!/usr/bin/env python3
"""Build the Mall Rats gift library.

Reads the raw gift shots (which live outside the repo), re-encodes each one to
a small WebP, and writes ``content/generated/mall-rats/gifts.json`` — the
source-of-truth store/gift data — plus serving copies under the Element's
static tree so browser players can open the pictures directly.

The gift's display name is its (stem of the) source filename, exactly as the
designer wrote it; dirs map to the four stores:

    Cheap      -> The Bargian Rac.        ($)
    Mid range  -> JC Possum               ($$)
    Expensive  -> Amber-Claw and Finch    ($$$)
    Impossible -> onkipowstal             (∞)

Usage:
    python3 content/build_mallrats.py [--source "/workspace/Mall Rats/Present time!"]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import sys
import unicodedata

try:
    from PIL import Image
except ImportError:  # pragma: no cover
    sys.exit("Pillow is required: pip install --user --break-system-packages Pillow")

MAX_DIMENSION = 640
WEBP_QUALITY = 82

# Raw subfolder -> store metadata (name as shown on the store sign, price meter).
STORES = [
    ("Cheap", {"id": "cheap", "name": "The Bargian Rac.", "meter": "$"}),
    ("Mid range", {"id": "mid-range", "name": "JC Possum", "meter": "$$"}),
    ("Expensive", {"id": "expensive", "name": "Amber-Claw and Finch", "meter": "$$$"}),
    ("Impossible", {"id": "impossible", "name": "Amonkipawstale", "meter": "∞"}),
]

IMAGE_EXTS = (".png", ".jpg", ".jpeg", ".webp", ".psd")


def slugify(name: str) -> str:
    text = unicodedata.normalize("NFKD", name)
    text = "".join(c for c in text if not unicodedata.combining(c))
    text = text.lower()
    text = re.sub(r"[^a-z0-9]+", "-", text).strip("-")
    return re.sub(r"-+", "-", text)


def build(source: str, out_generated: str, out_static: str) -> None:
    stores = []
    used_any = False
    for folder, meta in STORES:
        d = os.path.join(source, folder)
        if not os.path.isdir(d):
            print(f"    missing gift folder, skipping store {meta['name']}: {d}")
            continue
        used_any = True
        store_slug = meta["id"]
        gifts = []
        seen: set[str] = set()
        for name in sorted(os.listdir(d)):
            base, ext = os.path.splitext(name)
            if not ext.lower() in IMAGE_EXTS:
                continue
            if ext.lower() == ".psd":
                continue  # ex-PSD files (originally Photoshop sources) stay out of the pack
            src = os.path.join(d, name)
            if not os.path.isfile(src):
                continue
            slug = slugify(base)
            if not slug:
                continue
            # The Hawaiian cruise is merchandised from Amber-Claw and Finch,
            # so the cheaper stores never list it.
            if slug == "hawaiian-cruise":
                continue
            # Filenames repeat across stores — prefix collisions by store id.
            if f"{store_slug}:{slug}" in seen:
                slug = f"{slug}-{len(seen)}"
            seen.add(f"{store_slug}:{slug}")
            webp = f"{slug}.webp"
            im = Image.open(src)
            im = im.convert("RGBA") if "A" in im.getbands() else im.convert("RGB")
            im.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.Resampling.LANCZOS)
            dst_dir = os.path.join(out_static, "assets", "mall-rats", store_slug)
            os.makedirs(dst_dir, exist_ok=True)
            dst = os.path.join(dst_dir, webp)
            im.save(dst, "WEBP", quality=WEBP_QUALITY, method=6)
            display = base.strip()
            if store_slug == "cheap":
                lower = display.lower()
                starts_vowel = lower[0] in "aeiou"
                words = lower.split()
                is_plural = words[-1].endswith(("s",)) and not words[-1].endswith(("ss",))
                has_leading_article = lower.startswith(("a ", "an ", "the ", "your ", "some ", "'"))
                if not is_plural and not has_leading_article:
                    display = ("An " if starts_vowel else "A ") + display
            gifts.append({
                "id": slug,
                "name": display,
                "image": f"/app/static/collection/assets/mall-rats/{store_slug}/{webp}",
                "bytes": os.path.getsize(dst),
            })
        stores.append({**meta, "gifts": gifts})
        print(f"{meta['name']}: {len(gifts)} gifts")

    # --- relocate the Hawaiian cruise to the expensive store -----------------
    for folder in ("Mid range", "Cheap"):
        src = os.path.join(source, folder, "Hawaiian Cruise.jpg")
        if os.path.isfile(src):
            expensive = next(s for s in stores if s["id"] == "expensive")
            im = Image.open(src)
            im = im.convert("RGB")
            im.thumbnail((MAX_DIMENSION, MAX_DIMENSION), Image.Resampling.LANCZOS)
            dst = os.path.join(out_static, "assets", "mall-rats", "expensive", "hawaiian-cruise.webp")
            im.save(dst, "WEBP", quality=WEBP_QUALITY, method=6)
            expensive["gifts"].append({
                "id": "hawaiian-cruise",
                "name": "Hawaiian Cruise",
                "image": f"/app/static/collection/assets/mall-rats/expensive/hawaiian-cruise.webp",
                "bytes": os.path.getsize(dst),
            })
            print(f"    relocated 'Hawaiian Cruise' ({folder}) to Amber-Claw and Finch")
            break

    if not used_any:
        sys.exit(f"no gift folders found under {source}")

    doc = {"stores": stores}
    os.makedirs(os.path.join(out_generated, "mall-rats"), exist_ok=True)
    with open(os.path.join(out_generated, "mall-rats", "gifts.json"), "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")
    # Serving copy. kept in the repo, staged into the .elm by Maven.
    serv_dir = os.path.join(out_static, "content", "mall-rats")
    os.makedirs(serv_dir, exist_ok=True)
    shutil.copyfile(os.path.join(out_generated, "mall-rats", "gifts.json"),
                    os.path.join(serv_dir, "gifts.json"))
    total = sum(g["bytes"] for s in stores for g in s["gifts"])
    print(f"gifts.json: {sum(len(s['gifts']) for s in stores)} gifts, {total / 1e6:.2f} MB of webp")


def main() -> None:
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser()
    ap.add_argument("--source", default="/workspace/Mall Rats/Present time!")
    ap.add_argument("--out", default=os.path.join(here, "assets"))
    ap.add_argument("--generated", default=os.path.join(here, "generated"))
    ap.add_argument("--static", default=os.path.join(here, "..", "server", "element", "src", "main", "static"))
    args = ap.parse_args()
    build(args.source, args.generated, args.static)


if __name__ == "__main__":
    main()
