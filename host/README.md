# The Public Records Collection — Host (Godot display)

The native **Godot 4.4** app one player runs as the shared "display" — the
TV/laptop screen everyone can see. It shows the room code to join, then drives
the lobby and per-game presentation while the Element owns all state.

## Run

```bash
godot --path host
```

## Backend selector

Resolved at launch by the `BackendConfig` autoload (`src/autoload/BackendConfig.gd`),
no rebuild needed, in priority order:

1. `--backend-url=<url>` command-line argument
2. `PRC_BACKEND_URL` environment variable
3. `user://settings.cfg` — `[backend]` / `url=<url>`
4. default: production

```bash
# point at a temporary preview instance
godot --path host -- --backend-url=https://<fqdn>.cloud.namazustudios.com
```

`BackendConfig.rest_url()` and `BackendConfig.ws_url()` derive the Element's
`/collection` REST base and `/collection/ws` WebSocket from it.

> Status: scaffold. The project boots and resolves its backend; the lobby,
> room-code display, and per-game views are still to be built.
