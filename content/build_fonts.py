#!/usr/bin/env python3
"""Fetch the free (SIL Open Font License) fonts the Public Records Collection uses."""
from __future__ import annotations

import argparse
import os
import re
import sys
import urllib.parse
import urllib.request

FONTS = {
    "caveat": ("Caveat", "caveat", None),
    "gochi-hand": ("Gochi Hand", "gochihand", None),
    "shadows-into-light": ("Shadows Into Light", "shadowsintolight", None),
    "patrick-hand": ("Patrick Hand", "patrickhand", None),
    "playfair-display": ("Playfair Display", "playfairdisplay", None),
    "cinzel": ("Cinzel", "cinzel", None),
    "im-fell-english": ("IM Fell English", "imfellenglish", None),
    "special-elite": ("Special Elite", "specialelite", None),
    "rye": ("Rye", "rye", None),
    "old-standard-tt": ("Old Standard TT", "oldstandardtt", None),
    "cormorant-sc": ("Cormorant SC", "cormorantsc", None),
    "almendra-sc": ("Almendra SC", "almendrasc", None),
    "fredericka-the-great": ("Fredericka the Great", "frederickathegreat", None),
    "unifrakturcook": ("UnifrakturCook", "unifrakturcook", ":700"),
}

OLD_UA = "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1)"
CSS_URL = "https://fonts.googleapis.com/css2?family={family}&display=swap"
OFL_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/{dir}/OFL.txt"
URL_RE = re.compile(r"url\((https://[^)]+)\)")


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": OLD_UA})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def main() -> None:
    here = os.path.dirname(__file__)
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--web-out", default=os.path.join(here, "assets", "fonts"))
    ap.add_argument("--godot-out", default=os.path.join(here, "..", "host", "assets", "fonts"))
    args = ap.parse_args()
    os.makedirs(args.web_out, exist_ok=True)
    os.makedirs(args.godot_out, exist_ok=True)

    for slug, (family, repo_dir, weight) in FONTS.items():
        css_url = CSS_URL.format(family=urllib.parse.quote(family))
        if weight:
            css_url = css_url + weight
        css = fetch(css_url).decode("utf-8")
        url = URL_RE.search(css)
        if not url:
            sys.exit(f"no font url for {family}")
        data = fetch(url.group(1))
        head = data[:4]
        ext = "otf" if head == b"OTTO" else "ttf"
        for out in (args.web_out, args.godot_out):
            with open(os.path.join(out, f"{slug}.{ext}"), "wb") as f:
                f.write(data)
        print(f"{slug}.{ext}  {len(data)/1024:.0f} KB  ({family})")
        try:
            ofl = fetch(OFL_URL.format(dir=repo_dir))
            with open(os.path.join(args.web_out, f"{slug}-OFL.txt"), "wb") as f:
                f.write(ofl)
        except Exception:
            pass


if __name__ == "__main__":
    main()
