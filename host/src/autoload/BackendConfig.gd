extends Node
## Runtime environment selector for the Elements backend.
##
## Resolves the backend base URL at launch, with no rebuild required, in
## priority order:
##   1. --backend-url=<url> command-line argument
##   2. PRC_BACKEND_URL environment variable
##   3. user://settings.cfg  ([backend] url=<url>)
##   4. PRODUCTION_URL (default)
##
## The two targets this has to support are the session's temporary preview
## instance and production. Switching between them is a selector flip (CLI arg,
## env var, or settings file), never a code change.

const PRODUCTION_URL := "https://oatymilkgames.cloud.namazustudios.com"
const LOCAL_URL := "http://localhost:8080"

## Resolved Elements base URL, no trailing slash (e.g. https://host).
var url: String

## Where the resolved URL came from, for display/debugging.
var source: String


func _ready() -> void:
	url = _resolve()
	print("[BackendConfig] Elements backend = %s (from %s)" % [url, source])


func rest_url() -> String:
	return url + "/collection"


func ws_url() -> String:
	return url.replace("https://", "wss://").replace("http://", "ws://") + "/collection-ws"


## The lobby WebSocket players and the host connect to.
func lobby_ws_url() -> String:
	return ws_url() + "/lobby"


## REST endpoint that creates a room (POST ?hostName=...).
func rooms_url() -> String:
	return rest_url() + "/rooms"


## Public URL of the browser player controller (served by this Element).
func players_url() -> String:
	return url + "/app/static/collection/"


func _resolve() -> String:
	var args := OS.get_cmdline_args()
	args.append_array(OS.get_cmdline_user_args())
	for arg in args:
		if arg.begins_with("--backend-url="):
			source = "command line"
			return arg.trim_prefix("--backend-url=").trim_suffix("/")

	var env := OS.get_environment("PRC_BACKEND_URL")
	if env != "":
		source = "PRC_BACKEND_URL"
		return env.trim_suffix("/")

	var cfg := ConfigFile.new()
	if cfg.load("user://settings.cfg") == OK and cfg.has_section_key("backend", "url"):
		source = "user://settings.cfg"
		return str(cfg.get_value("backend", "url")).trim_suffix("/")

	source = "default (production)"
	return PRODUCTION_URL
