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
| WebSocket | `/collection-ws` | lobby + realtime game state |
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
- **Gzipped book texts** are staged to the server-only classpath. Players see
  the **full text** of a book during a round, delivered **one passage at a
  time**: the server selects passages and streams them incrementally (clients
  request the next passage via a cursor) rather than shipping whole books to
  the device. This keeps the two-column party-game layout snappy and bounds
  per-client payloads.

## Lobby protocol

The lobby is a single JSON-per-frame WebSocket at `/collection-ws/lobby`.

| Direction | Message |
|-----------|---------|
| client→server | `{"type":"host","code","token"}` · `{"type":"join","code","name","playerId"?}` · `{"type":"start","gameId"}` *(host)* · `{"type":"settings","settings":{"familyFriendly","answerTimerSeconds","rounds"}}` *(host)* · `choose-work {workId}` · `submit-answer {text}` · `cast-vote {answerId,golden}` · `advance` *(host)* · `{"type":"leave"}` *(host closes room)* · `{"type":"ping"}` |
| server→client | `{"type":"welcome","host","playerId"?,"room":{…}}` · `{"type":"state","room":{…}}` · `{"type":"game","game":{…}}` (host) · `{"type":"game","game":{…},"you":{…}}` (player) · `{"type":"closed","reason"}` · `{"type":"pong"}` · `{"type":"error","message"}` |

`room` = `{code, phase, gameId, settings, players:[{id,name,host,connected}]}`;
the host appears as a participant with id `"host"`. Room codes are 4 chars from
`ABCDEFGHJKLMNPQRSTUVWXYZ23456789` (no `I`/`O`/`0`/`1`); the room registry is
in-memory (single instance) for v1.

Game state is delivered on the same socket with the **two-view rule**: every
participant receives the public `game` view (the host's big-screen view), and
each player additionally receives their own `you` view (private hand/state).
The server computes the public view once per broadcast and shares it.

## Get Lit

Phases: `select → answer → vote → results` (loops `answer` per round) `→ finished`.
Each player picks one work at the start and answers every prompt from it. A work's
full text is read from the server one passage at a time:

- `GET /collection/books?familyFriendly=` — selectable works
- `GET /collection/books/{id}/text?offset=&limit=` — a passage (`last` flags the end)

Scoring: a normal heart is 1 point, a golden heart 3, both only on someone else's
answer; at most one normal heart per answer per voter, one golden heart per player
per round, no self-votes. The host advances each phase.


## Environments

Two backends, selectable at runtime (no rebuild): the session's **temporary**
preview instance and **production**. See the root README's "Backend environment
selector". Clients must never hard-code a backend URL.

## Status

The lobby and the **Get Lit** game are implemented and verified end-to-end on
the temporary instance: room creation (REST), host/join over WebSocket, live
roster + settings, work selection, answering (validated against the player's own
work), anonymous voting with normal + golden hearts (self-vote and duplicate-heart
guards), per-round scoring, and finish. Both clients render every game phase: the
Godot host shows the big-screen display with an Advance control, and the web
players controller renders work selection, a paginated passage reader for
answering, anonymous ♥/★ voting, and results/standings screens. Next: the
remaining four games. See the game-design repo for per-game rules and scoring.
