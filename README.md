# The Public Records Collection

A Jackbox-style party pack built on [Namazu Elements](https://namazustudios.com).
One player runs the host **display** app; everyone else joins from a phone
browser with a room code. Five games are planned; the platform and **Get Lit**
are built first.

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

1. **Get Lit** — *(built first)* read a passage, players answer.
2. **Sticker Doodle** — one rotating drawer per round.
3. **Present Time!** — simultaneous secret gift assignment.
4. **Average Joe** — seed data + lobby survey.
5. **Cin-A-Mouse** — *(last; nothing prepared yet)*.

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
  dev server proxies `/collection`. In a real deploy the Element serves the
  built app, so calls are same-origin.

See [`docs/architecture.md`](docs/architecture.md) for the component and data
flow, and [`content/README.md`](content/README.md) for the content pipeline.

> **Status:** scaffold. The Element builds and resolves its backend end-to-end;
> the lobby, WebSocket protocol, and per-game logic are next.
