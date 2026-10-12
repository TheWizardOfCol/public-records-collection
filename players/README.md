# The Public Records Collection — Players (web controller)

The browser app players open on their phones to join a game. It talks to the
Collection Element (`server/`): the host runs the native Godot display app, and
players join with a name + the room code shown on that display.

## Develop

```bash
npm install
cp .env.example .env.local   # set VITE_BACKEND_URL to a backend, then:
npm run dev
```

`vite dev` proxies `/collection` (REST + WebSocket) to `VITE_BACKEND_URL`, so
the same relative API paths work locally and in production.

## Build

```bash
npm run build
```

Emits a static bundle to `players/dist/`. In a deploy that bundle is served by
the Element itself at `/app/static/collection/`, which is why the app uses
same-origin relative API paths rather than an injected backend URL.

> Status: scaffold. The join form and backend health check exist; the lobby
> WebSocket and per-game views are still to be built.
