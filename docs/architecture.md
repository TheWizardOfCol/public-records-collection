# Architecture

## Components

```
                 ┌─────────────────────────────────────────┐
   Host display  │  host/  (native Godot 4.4)               │
   (one player)  │  renders lobby + game views               │
                 └───────────────┬─────────────────────────┘
                                 │ REST + WebSocket
                                 ▼
                 ┌─────────────────────────────────────────┐
   Element       │  server/  (Namazu Elements, Java)         │
   (authoritative)│  • room codes & lobby membership         │
                 │  • all game state + rules                │
                 │  • serves players/ bundle + content      │
                 └───────────────┬─────────────────────────┘
                                 │ REST + WebSocket
                                 ▼
                 ┌─────────────────────────────────────────┐
   Player        │  players/  (browser, Vite/TS)             │
   controllers   │  join with name + room code              │
   (3–8)         │  game-specific input UI                  │
                 └─────────────────────────────────────────┘
```

The **Element is the single source of truth**. Clients send intents (join,
submit answer, draw) and receive authoritative state; they never compute
outcomes. That keeps an 8-way mobile game consistent despite differing latency
and device speed.

## Surfaces (Element)

| Surface | Path | Notes |
|---------|------|-------|
| REST base | `/collection` | e.g. `GET /collection/health` |
| WebSocket | `/collection/ws` | lobby + realtime game state |
| Static (players app + content) | `/app/static/collection/` | built in, same-origin for players |
| Dashboard UI plugin | `/app/ui/collection/` | admin surface |

The browser player app is served **by the Element**, so its API calls are
same-origin relative paths.

## Content flow

```
Prompts/ (raw)  →  content/build_content.py  →  content/generated/*.json   ┐
                →  content/build_assets.py   →  content/assets/*           ┘
                                                    │
                         players/ npm run build ────┤
                                                    ▼
                                  server/ mvn install  →  .elm archive
```

- **JSON** (prompts, books metadata, parody titles) and **sticker WebP** are
  staged into the served static tree.
- **Gzipped book texts** are staged to the server-only classpath; the server
  selects a passage and sends just that text to clients.

## Environments

Two backends, selectable at runtime (no rebuild): the session's **temporary**
preview instance and **production**. See the root README's "Backend environment
selector". Clients must never hard-code a backend URL.

## Status

Scaffold: Element builds and resolves its backend; the WebSocket protocol,
lobby, and per-game logic are the next build steps. See the game-design repo for
per-game rules and scoring.
