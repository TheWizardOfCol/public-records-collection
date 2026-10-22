# The Public Records Collection — Element (server)

The server-side Namazu Elements **Element** for *The Public Records Collection*, a
Jackbox-style party pack. It is the authoritative runtime: it owns lobby membership,
room codes, and per-game state, and it pushes realtime updates to the Godot host display
and the browser player controllers over WebSockets.

This module is a standard multi-module Elements Element project:

```
api/       Exported interfaces (other Elements depend on this)
ui/        Dashboard UI plugin source (Vite/TypeScript; not deployed directly)
element/   REST endpoints, services, Guice modules → compiles to the .elm archive
debug/     Local Elements runtime harness (never deployed)
```

- **Element coordinate:** `com.publicrecords:element:1.1.0-SNAPSHOT:elm`
- **REST root:** `/collection` (e.g. `/collection/health`)
- **WebSocket root:** `/collection-ws`
- **Static player content:** `/app/static/collection/`
- **Dashboard UI plugin:** `/app/ui/collection/`

Targets **Namazu Elements 3.9.0-rc-9** (matching the deployed instances).

## Build

```bash
mvn install
```

Produces `element/target/com.publicrecords.element-1.0-SNAPSHOT.elm` and installs the
artifact to the local Maven repository.

## Run locally (SDK harness)

```bash
# MongoDB for the local runtime
docker compose -f services-dev/docker-compose.yml up -d

# Builds the UI plugin bundles, starts MongoDB, then boots the local Elements runtime
mvn -pl debug exec:java
```

The local runtime serves the platform API at `http://localhost:8080/api/rest`, and the
Element's endpoints at `http://localhost:8080/collection/...`.

See [CLAUDE.md](CLAUDE.md) for the Element development conventions (Guice DI, REST,
WebSockets, static/UI content, Morphia) and [MORPHIA.md](MORPHIA.md) for database access.
