# Content pipeline

The curated game content (books, prompts, parody titles, sticker prompts) starts
as spreadsheets/CSV and sticker images under a raw source tree. This directory
turns that into the small, web-ready data the Element ships.

## Raw sources (not committed)

The raw sources are large (~200 MB) and live outside this repository — by
default at `/workspace/Prompts`:

```
Prompts/
  get-lit-content/
    Get Lit Content.xlsx     # sheets: Books, Prompts, Parody Titles
    books/                   # 88 plain-text books
  sticker-doodle-prompts.csv # text prompts (celebrity / movie_tv / person_type)
  Stickers/                  # sticker art (PNG)
  Stickers 2/                # more sticker art (PNG)
```

## Outputs (committed)

Both scripts write under `content/` and are idempotent.

```bash
python3 content/build_content.py   # → content/generated/   (JSON, ~156 KB)
python3 content/build_assets.py    # → content/assets/      (~28 MB)
```

- `content/generated/get-lit/{books,prompts,parody_titles}.json`
- `content/generated/sticker-doodle/prompts.json`
- `content/assets/stickers/*.webp` + `index.json` — art downscaled to ≤768 px,
  re-encoded to WebP (103 images, ~3.5 MB)
- `content/assets/books/<slug>.txt.gz` — book texts gzipped for the server
  (88 books, ~24.6 MB)

## Storage strategy

**Optimize + commit.** Web-ready assets are committed so a clone + build
produces a complete, reproducible deploy with no external dependencies or
runtime asset hosting. Raw originals stay out of git. The build stages these
into the Element (`server/element/pom.xml`): JSON + sticker WebP go to the
served static tree (`/app/static/collection/content/`), and gzipped books go to
the server-only classpath so passages can be selected server-side.

Excluded on purpose: `Hans_Holbein_..._The_Ambassadors...png` (a 308 MB
misfiled full-resolution scan, not sticker art) and a stray 84 MB `.tmp`
upload. Any source image over 40 MB is skipped as a misfiled master.

## Requirements

```bash
pip install --user --break-system-packages openpyxl Pillow
```
