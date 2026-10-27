# The Public Records Collection

A Jackbox-style party pack built on [Namazu Elements](https://namazustudios.com).
One player runs the host **display** app; everyone else joins from a phone
browser with a room code. Five games are planned; the platform and
**Cryptid Cinema** are built so far.

Everything is **server-authoritative**: the Collection Element owns lobby
membership, room codes, and all game state, and pushes realtime updates to the
host and players over WebSockets. Clients render and send input — they never
decide outcomes.

## Repository layout

| Path | What it is |
|------|------------|
| `server/` | The Namazu Elements **Element** (Java/Maven). Authoritative game server + REST/WS API. |
| `host/` | The host **display** app — a native **Godot 4.4** project. |
| `players/` | The **browser player controller** (Vite/TypeScript). |
| `content/` | Content pipeline + curated game data & optimized assets. |
| `docs/` | Architecture and process notes. |

The game-design document lives in its own repo:
[`public-records-collection-game-design`](https://github.com/TheWizardOfCol/public-records-collection-game-design).

## The games

1. **Super Sticker Vandalz** — drawing guessing with mandatory stickers:
   a public-domain person sticker anchors the doodle; players guess it.
   Graffiti-visual, prompt packs (movies, TV, celebs, occupations).
2. **Mall Rats** — *(next up)* 1980s mall of rat people: rate the gift pool
   0–5, then shop for everyone else; points both for giving and receiving.
   First to 30 wins. Host picks the Store (a set of gifts) at game start.
3. **Average Joe** — Guesspionage-style data guessing with several answer
   shapes (yes/no, 1–5, multi-select, point-on-diagram, percent-trivia);
   lobby surveys feed the data while players wait.
4. **Cryptid Cinema** — *(built)* remix an old film with sound effects,
   music, and your own voice.
5. **Time-Traveling Stalker Simulator 3027** — help Scary Guy stalk history:
   where/when-photo mini-rounds and a final escape trivia.
   *(Get Lit and Present Time! were retired from the pack.)*

A single family-friendly on/off toggle filters "spicy" content (`is_spicy`).

## Building

Prerequisites: JDK 21 + Maven, Node 22, Godot 4.4.

```bash
# 1. Game content → JSON + optimized assets (needs Python 3 + openpyxl + Pillow)
python3 content/build_content.py
python3 content/build_assets.py

# 2. Browser player controller → players/dist/
cd players && npm install && npm run build && cd ..

# 3. Element → server/element/target/com.publicrecords.element-1.0-SNAPSHOT.elm
#    (stages players/dist + content into the archive)
cd server && mvn install && cd ..

# 4. Host display (native)
godot --path host
```

## Backend environment selector

Both clients can point at either environment without a rebuild:

- **Host (Godot):** `--backend-url=<url>` CLI arg, `PRC_BACKEND_URL` env var,
  or `user://settings.cfg`; defaults to production.
- **Players (Vite dev):** set `VITE_BACKEND_URL` in `players/.env.local`; the
  dev server proxies `/collection` and `/collection-ws`. In a real deploy the
  Element serves the built app, so calls are same-origin.

See [`docs/architecture.md`](docs/architecture.md) for the component and data
flow, and [`content/README.md`](content/README.md) for the content pipeline.

> **Status:** the lobby and **Cryptid Cinema** work end-to-end on production —
> room codes, host/join over WebSocket, episodes, rounds (recording + choice),
> winner screenings, and scoring. Both clients render every phase (the Godot
> host big-screen display + Advance control; the web players controller).
> **Mall Rats** is next (scaffold planned); the shared design doc lives in the
> game-design repo.
