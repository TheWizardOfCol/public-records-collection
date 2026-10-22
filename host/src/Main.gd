extends Control
## Host display for The Public Records Collection.
##
## The native Godot app the host runs on the shared screen. It creates a room
## via the REST API, holds the lobby open over WebSocket (room code, join URL,
## live roster, host-only settings/start), and renders the per-game public view.
##
## The server sends two kinds of frames: room `state` (lobby) and `game` (the
## public, big-screen view of the running game). The host never gets the players'
## private views.

const GAME_LABELS := ["Cryptid Cinema", "Get Lit"]
const GAME_IDS := ["cryptid-cinema", "get-lit"]

var _http: HTTPRequest
var _ws := WebSocketPeer.new()
var _host_name := "Host"
var _game_index := 0

var _room_code := ""
var _host_token := ""

# UI — lobby
var _lobby_panel: VBoxContainer
var _code_label: Label
var _url_label: Label
var _players_label: Label
var _status_label: Label
var _start_button: Button
var _advance_button: Button
var _family_check: CheckBox
var _timer_spin: SpinBox
var _game_select: OptionButton

# Title-page styling (mirrors scenes/Title.tscn so the lobby reads as the same game).
const TITLE_FONT := "res://assets/fonts/playfair-display.ttf"
const BODY_FONT := "res://assets/fonts/old-standard-tt.ttf"
const GOLD := Color(1.0, 1.0, 0.85)
const ACCENT := Color(0.8, 0.7, 0.5)
var _slideshow: Control

# UI — game
var _game_panel: VBoxContainer
var _game_title: Label
var _game_body: RichTextLabel

# UI — Cryptid Cinema (big screen film + notes)
var _cryptid_view: HBoxContainer
var _film: VideoStreamPlayer
var _cryptid_notes: RichTextLabel

# Film scheduling (Godot can't seek Theora, so we play forward and pause at a mark).
var _film_path := ""
var _film_target := -1.0
var _film_started := false

# Host-side screening of player recordings (WAV, one after another).
var _audio_player: AudioStreamPlayer
var _audio_http: HTTPRequest
var _audio_queue: PackedStringArray = PackedStringArray()
var _audio_res_queue: PackedStringArray = PackedStringArray() # res:// paths
var _audio_round := -1
var _audio_busy := false

const FOUNDTRACK_MUSIC := {
	"01-01-la-primavera-spring-allegro": "res://assets/cryptid/music/01-01-la-primavera-spring-allegro.mp3",
	"adventures-in-adventureland-kevin-macleod": "res://assets/cryptid/music/adventures-in-adventureland-kevin-macleod.mp3",
	"amazing-grace-traditional": "res://assets/cryptid/music/amazing-grace-traditional.mp3",
	"android-sock-hop-kevin-macleod": "res://assets/cryptid/music/android-sock-hop-kevin-macleod.mp3",
	"army-strong-mark-isham": "res://assets/cryptid/music/army-strong-mark-isham.mp3",
	"balloon-game-kevin-macleod": "res://assets/cryptid/music/balloon-game-kevin-macleod.mp3",
	"blue-danube-johann-strauss-ii": "res://assets/cryptid/music/blue-danube-johann-strauss-ii.mp3",
	"brain-dance-kevin-macleod": "res://assets/cryptid/music/brain-dance-kevin-macleod.mp3",
	"circuit-bent-keyboard-demo-some-sicko": "res://assets/cryptid/music/circuit-bent-keyboard-demo-some-sicko.mp3",
	"danse-macabre-camille-saint-sa-ns": "res://assets/cryptid/music/danse-macabre-camille-saint-sa-ns.mp3",
	"drankin-song-kevin-macleod": "res://assets/cryptid/music/drankin-song-kevin-macleod.mp3",
	"entry-of-the-gladiators-julius-fucik": "res://assets/cryptid/music/entry-of-the-gladiators-julius-fucik.mp3",
	"epic-score-jeremusic70": "res://assets/cryptid/music/epic-score-jeremusic70.mp3",
	"everyone-loyalty-freak-music": "res://assets/cryptid/music/everyone-loyalty-freak-music.mp3",
	"farting-around-kevin-macleod": "res://assets/cryptid/music/farting-around-kevin-macleod.mp3",
	"flying-kerfuffle-kevin-macleod": "res://assets/cryptid/music/flying-kerfuffle-kevin-macleod.mp3",
	"funeral-march-chopin": "res://assets/cryptid/music/funeral-march-chopin.mp3",
	"funiculi-funicula-luigi-denza-and-peppino-turco": "res://assets/cryptid/music/funiculi-funicula-luigi-denza-and-peppino-turco.mp3",
	"god-save-the-king": "res://assets/cryptid/music/god-save-the-king.mp3",
	"gymnopedie-no-1-erik-satie": "res://assets/cryptid/music/gymnopedie-no-1-erik-satie.mp3",
	"habanera": "res://assets/cryptid/music/habanera.mp3",
	"haiduceasca-traditional": "res://assets/cryptid/music/haiduceasca-traditional.mp3",
	"halleljeah-george-frideric-handel": "res://assets/cryptid/music/halleljeah-george-frideric-handel.mp3",
	"happy-happy-game-show-kevin-macleod": "res://assets/cryptid/music/happy-happy-game-show-kevin-macleod.mp3",
	"high-stakes-spymaster-rfm1011": "res://assets/cryptid/music/high-stakes-spymaster-rfm1011.mp3",
	"in-the-hall-of-the-mountain-king-edvard-grieg": "res://assets/cryptid/music/in-the-hall-of-the-mountain-king-edvard-grieg.mp3",
	"kun-khmer-fight-music-traditional": "res://assets/cryptid/music/kun-khmer-fight-music-traditional.mp3",
	"liberty-bell-march-john-philip-sousa": "res://assets/cryptid/music/liberty-bell-march-john-philip-sousa.mp3",
	"little-fugue-in-g-minor-bach": "res://assets/cryptid/music/little-fugue-in-g-minor-bach.mp3",
	"maple-leaf-rag-scott-joplin": "res://assets/cryptid/music/maple-leaf-rag-scott-joplin.mp3",
	"mass-of-the-day-gajard-dom-joseph": "res://assets/cryptid/music/mass-of-the-day-gajard-dom-joseph.mp3",
	"meatball-parade-kevin-macleod": "res://assets/cryptid/music/meatball-parade-kevin-macleod.mp3",
	"mesmerizing-galaxy-kevin-macleod": "res://assets/cryptid/music/mesmerizing-galaxy-kevin-macleod.mp3",
	"modern-lounge-prettyjohn1": "res://assets/cryptid/music/modern-lounge-prettyjohn1.mp3",
	"modern-sadness-kevin-macleod": "res://assets/cryptid/music/modern-sadness-kevin-macleod.mp3",
	"monkeys-spinning-monkeys-kevin-macleod": "res://assets/cryptid/music/monkeys-spinning-monkeys-kevin-macleod.mp3",
	"mouthalovania-oatymilkgalaxy": "res://assets/cryptid/music/mouthalovania-oatymilkgalaxy.mp3",
	"nano-hoedown-kevin-macleod": "res://assets/cryptid/music/nano-hoedown-kevin-macleod.mp3",
	"o-canada": "res://assets/cryptid/music/o-canada.mp3",
	"ode-to-joy-on-the-netherlands-carillon": "res://assets/cryptid/music/ode-to-joy-on-the-netherlands-carillon.mp3",
	"pomp-and-circumstance": "res://assets/cryptid/music/pomp-and-circumstance.mp3",
	"pookatori-and-friends-kevin-macleod": "res://assets/cryptid/music/pookatori-and-friends-kevin-macleod.mp3",
	"pop-goes-the-weasel-traditional": "res://assets/cryptid/music/pop-goes-the-weasel-traditional.mp3",
	"ride-of-the-valkyries-richard-wagner": "res://assets/cryptid/music/ride-of-the-valkyries-richard-wagner.mp3",
	"sabre-dance-aram-khachaturian": "res://assets/cryptid/music/sabre-dance-aram-khachaturian.mp3",
	"soviet-national-anthem": "res://assets/cryptid/music/soviet-national-anthem.mp3",
	"star-spangled-banner-francis-scott-key-and-john-stafford-smith": "res://assets/cryptid/music/star-spangled-banner-francis-scott-key-and-john-stafford-smith.mp3",
	"symphony-no-9-ludwig-van-beethoven": "res://assets/cryptid/music/symphony-no-9-ludwig-van-beethoven.mp3",
	"that-zen-moment-kevin-macleod": "res://assets/cryptid/music/that-zen-moment-kevin-macleod.mp3",
	"the-britons-kevin-macleod": "res://assets/cryptid/music/the-britons-kevin-macleod.mp3",
	"the-entertainer-scott-joplin": "res://assets/cryptid/music/the-entertainer-scott-joplin.mp3",
	"the-german-s-arrival-frank-wilson": "res://assets/cryptid/music/the-german-s-arrival-frank-wilson.mp3",
	"the-laughing-record": "res://assets/cryptid/music/the-laughing-record.mp3",
	"the-whistling-bowery-boy-s-h-dudley": "res://assets/cryptid/music/the-whistling-bowery-boy-s-h-dudley.mp3",
	"toscaninini-ludwig-van-beethoven": "res://assets/cryptid/music/toscaninini-ludwig-van-beethoven.mp3",
	"toy-symphony-allegro-leopold-mozart": "res://assets/cryptid/music/toy-symphony-allegro-leopold-mozart.mp3",
	"william-tell-overture-gioachino-rossini": "res://assets/cryptid/music/william-tell-overture-gioachino-rossini.mp3",
	"wedding-march-felix-mendelssohn": "res://assets/cryptid/music/wedding-march-felix-mendelssohn.mp3",
	"welcome-to-horrorland-kevin-macleod": "res://assets/cryptid/music/welcome-to-horrorland-kevin-macleod.mp3",
	"yankee-doodle-traditional": "res://assets/cryptid/music/yankee-doodle-traditional.mp3",
}

## Dev aid: when > 0, the host auto-sends `advance` every N seconds while a game
## is running (set with --auto-advance=<seconds>). Handy for exercising the game
## display headless; leave 0 in normal use.
var _auto_advance := 0.0
var _auto_accum := 0.0

## Dev aid: when > 0, the host auto-starts the game once this many non-host
## players have joined (set with --auto-start=<n>). Leave 0 in normal use.
var _auto_start := 0
var _did_start := false

## Keepalive: seconds accumulated since the last `ping` sent to the lobby.
var _ping_accum := 0.0


func _ready() -> void:
	var args := OS.get_cmdline_args()
	args.append_array(OS.get_cmdline_user_args())
	for arg in args:
		if arg.begins_with("--host-name="):
			_host_name = arg.trim_prefix("--host-name=")
		elif arg.begins_with("--auto-advance="):
			_auto_advance = maxf(0.0, arg.trim_prefix("--auto-advance=").to_float())
		elif arg.begins_with("--auto-start="):
			_auto_start = maxi(0, arg.trim_prefix("--auto-start=").to_int())

	_build_ui()


	_http = HTTPRequest.new()
	add_child(_http)
	_http.request_completed.connect(_on_room_created)

	_set_status("Creating room…")
	var name_param := _host_name.uri_encode()
	_http.request(
		"%s?hostName=%s" % [BackendConfig.rooms_url(), name_param],
		PackedStringArray(),
		HTTPClient.METHOD_POST
	)


func _process(delta: float) -> void:
	_ws.poll()
	match _ws.get_ready_state():
		WebSocketPeer.STATE_OPEN:
			while _ws.get_available_packet_count() > 0:
				_on_message(_ws.get_packet().get_string_from_utf8())
			_ping_accum += delta
			if _ping_accum >= 20.0:
				_ping_accum = 0.0
				_ws.send_text(JSON.stringify({"type": "ping"}))
		WebSocketPeer.STATE_CLOSED:
			pass

	_update_film()

	if _auto_advance > 0.0 and _game_panel.visible and not _advance_button.disabled:
		_auto_accum += delta
		if _auto_accum >= _auto_advance:
			_auto_accum = 0.0
			_on_advance_pressed()


func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_CLOSE_REQUEST or what == NOTIFICATION_PREDELETE:
		if _ws.get_ready_state() != WebSocketPeer.STATE_CLOSED:
			_ws.send_text(JSON.stringify({"type": "leave"}))
			_ws.close()


# --- REST: create room --------------------------------------------------------

func _on_room_created(_result: int, response_code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	if response_code != 200:
		_set_status("Could not create room (HTTP %d)." % response_code)
		return

	var room = JSON.parse_string(body.get_string_from_utf8())
	if typeof(room) != TYPE_DICTIONARY or not room.has("code"):
		_set_status("Unexpected create-room response.")
		return

	_room_code = str(room["code"])
	_host_token = str(room["hostToken"])
	_code_label.text = _room_code
	_url_label.text = BackendConfig.players_url()
	print("[host] room code ", _room_code)

	_set_status("Connecting lobby…")
	# The Namazu Cloud edge rejects handshakes with no User-Agent (403) — the
	# browser and Godot's HTTPRequest send one, but WebSocketPeer does not.
	_ws.set_handshake_headers(PackedStringArray(["User-Agent: PublicRecordsCollection-Host/1.0 (Godot Engine)"]))
	var err := _ws.connect_to_url(BackendConfig.lobby_ws_url())
	if err != OK:
		_set_status("WebSocket connect failed (error %d)." % err)
		return

	_await_open_then_host()


func _await_open_then_host() -> void:
	while _ws.get_ready_state() == WebSocketPeer.STATE_CONNECTING:
		await get_tree().create_timer(0.05).timeout
	if _ws.get_ready_state() == WebSocketPeer.STATE_OPEN:
		_ws.send_text(JSON.stringify({
			"type": "host", "code": _room_code, "token": _host_token,
		}))
		_set_status("Lobby open — waiting for players.")
	else:
		_set_status("Lobby connection did not open.")


# --- WebSocket messages -------------------------------------------------------

func _on_message(text: String) -> void:
	var msg = JSON.parse_string(text)
	if typeof(msg) != TYPE_DICTIONARY:
		return

	match str(msg.get("type", "")):
		"welcome", "state":
			_render_room(msg.get("room", {}))
		"game":
			_render_game(msg.get("game", {}))
		"closed":
			_set_status("Room closed: %s" % str(msg.get("reason", "")))
			_start_button.disabled = true
			_advance_button.disabled = true
		"error":
			_set_status("Error: %s" % str(msg.get("message", "")))
		"pong":
			pass


func _render_room(room: Dictionary) -> void:
	var players: Array = room.get("players", [])
	var lines := PackedStringArray()
	for p in players:
		var tags := PackedStringArray()
		if p.get("host", false):
			tags.append("host")
		if not p.get("connected", true):
			tags.append("away")
		var suffix := "" if tags.is_empty() else "  (%s)" % ", ".join(tags)
		lines.append("• %s%s" % [str(p.get("name", "?")), suffix])
	_players_label.text = "\n".join(lines) if not lines.is_empty() else "Waiting for players…"

	var settings: Dictionary = room.get("settings", {})
	_family_check.set_pressed_no_signal(bool(settings.get("familyFriendly", true)))
	_timer_spin.set_value_no_signal(int(settings.get("answerTimerSeconds", 30)))

	var phase := str(room.get("phase", "lobby"))
	_start_button.disabled = phase != "lobby"
	if phase == "lobby":
		_set_status("Lobby open — %d player(s)." % players.size())
	else:
		_set_status("Phase: %s" % phase)

	if _auto_start > 0 and not _did_start and phase == "lobby":
		var non_host := 0
		for p in players:
			if not p.get("host", false) and p.get("connected", true):
				non_host += 1
		if non_host >= _auto_start:
			_did_start = true
			_on_start_pressed()


# --- Game display -------------------------------------------------------------

func _render_game(game: Dictionary) -> void:
	var phase := str(game.get("phase", ""))
	var in_game := phase != ""

	# The lobby furniture only makes sense before/around a game; hide it once
	# a game is running and show the big-screen view instead. The drifting
	# slideshow is a lobby/title backdrop, so it goes away with the lobby too.
	_lobby_panel.visible = not in_game
	_game_panel.visible = in_game
	if _slideshow != null:
		_slideshow.visible = not in_game
	_advance_button.disabled = phase == "finished" or phase == ""

	if not in_game:
		_game_body.text = ""
		_cryptid_view.visible = false
		_stop_film()
		_audio_round = -1
		_audio_queue = PackedStringArray()
		return

	var cryptid := game.has("episodeId")
	_cryptid_view.visible = cryptid
	_game_body.visible = not cryptid

	if cryptid:
		_render_cryptid(game, phase)
	else:
		_stop_film()
		_render_getlit(game, phase)


func _render_getlit(game: Dictionary, phase: String) -> void:
	var round := int(game.get("round", 0))
	var total := int(game.get("totalRounds", 0))
	_game_title.text = "Get Lit — Round %d / %d" % [max(round, 1), total]
	print("[host] game phase: %s (round %d/%d)" % [phase, round, total])

	var lines := PackedStringArray()
	var prompt := str(game.get("prompt", ""))
	if not prompt.is_empty():
		lines.append("[font_size=34][b]%s[/b][/font_size]" % prompt)
		lines.append("")

	match phase:
		"select":
			lines.append("[i]Everyone is choosing a work…[/i]")
			lines.append("")
			for w in game.get("works", []):
				var chosen := str(w.get("title", ""))
				var label := chosen if not chosen.is_empty() else "[i]choosing…[/i]"
				lines.append("%s — %s" % [str(w.get("playerName", "?")), label])
		"answer":
			lines.append("%d of %d players have answered." % [
				int(game.get("submitted", 0)), int(game.get("players", 0))])
			lines.append("")
			lines.append(_works_list(game))
		"vote":
			lines.append("%d of %d players have answered. Vote now!" % [
				int(game.get("submitted", 0)), int(game.get("players", 0))])
			lines.append("")
			lines.append(_answers_list(game, false))
		"results":
			lines.append(_answers_list(game, true))
			lines.append("")
			lines.append("[b]This round[/b]")
			lines.append(_scores_list(game.get("roundScores", [])))
			lines.append("")
			lines.append("[b]Standings[/b]")
			lines.append(_scores_list(game.get("scores", [])))
		"finished":
			lines.append("[font_size=30][b]Final Standings[/b][/font_size]")
			lines.append(_scores_list(game.get("scores", [])))
			var scores: Array = game.get("scores", [])
			if not scores.is_empty():
				lines.append("")
				lines.append("[b]%s wins![/b]" % str(scores[0].get("name", "?")))

	_game_body.text = "\n".join(lines)


# --- Cryptid Cinema display ---------------------------------------------------

func _render_cryptid(game: Dictionary, phase: String) -> void:
	var round := int(game.get("round", 0))
	var total := int(game.get("totalRounds", 0))
	var round_type := str(game.get("roundType", ""))
	var is_foundtrack := round_type == "FOUNDTRACK"
	var rtype := _round_type_label(round_type)
	_game_title.text = "Cryptid Cinema — Round %d / %d · %s" % [max(round, 1), total, rtype]
	print("[host] cryptid phase: %s (round %d/%d)" % [phase, round, total])

	# Minimal on-screen text while the video plays; narration/notes appear as HUD.
	_game_title.visible = not _video_playing_now(game, phase)
	if not _video_playing_now(game, phase):
		_game_title.text = "Cryptid Cinema — Round %d / %d · %s" % [max(round, 1), total, rtype]

	# Up to the foundtrack round the film plays with its own music; from the
	# foundtrack screening on, the host uses the music-less cut and layers the
	# chosen track and the players' takes over it.
	var video := str(game.get("videoNoMusic", "")) if is_foundtrack else str(game.get("video", ""))
	if video.is_empty():
		video = str(game.get("video", ""))
	if not video.is_empty():
		_ensure_film(video)

	var lines := PackedStringArray()
	lines.append("[font_size=26][b]%s[/b][/font_size]" % str(game.get("episodeTitle", "")))
	lines.append("")
	var notes := str(game.get("directorNotes", ""))
	if not notes.is_empty():
		lines.append("[i]%s[/i]" % notes)
		lines.append("")

	match phase:
		"brief":
			_play_film_until(float(game.get("seeEnd", 0.0)))
			lines.append("Roll film — get ready to record.")
			lines.append("")
			lines.append(_prompt_pool(game))
		"record":
			_pause_film()
			lines.append("Recording…  %d of %d submitted." % [
				int(game.get("submitted", 0)), int(game.get("players", 0))])
		"vote":
			_pause_film()
			lines.append("%d of %d submitted — vote now!" % [
				int(game.get("submitted", 0)), int(game.get("players", 0))])
			lines.append("")
			lines.append(_cryptid_submissions(game, false))
		"results":
			if is_foundtrack:
				_replay_film_from_start()
			else:
				_pause_film()
			lines.append(_cryptid_submissions(game, true))
			lines.append("")
			lines.append("[b]This round[/b]")
			lines.append(_scores_list(game.get("roundScores", [])))
			lines.append("")
			lines.append("[b]Standings[/b]")
			lines.append(_scores_list(game.get("scores", [])))
			_enqueue_screening(game)
		"finished":
			_pause_film()
			lines.append("[font_size=26][b]Final Standings[/b][/font_size]")
			lines.append(_scores_list(game.get("scores", [])))
			var scores: Array = game.get("scores", [])
			if not scores.is_empty():
				lines.append("")
				lines.append("[b]%s wins![/b]" % str(scores[0].get("name", "?")))

	# show only minimal info when video is playing
	if _video_playing_now(game, phase):
		var submitted = 0
		var players_list = game.get("players", [])
		for p in players_list:
			if p.get("submitted", false) or p.get("ready", false):
				submitted += 1
		_cryptid_notes.text = "[b]Round %d/%d[/b] — Submitted: %d/%d" % [max(round, 1), total, submitted, len(players_list)]
		_cryptid_notes.visible = true
	else:
		_cryptid_notes.text = "\n".join(lines)
		_cryptid_notes.visible = true


func _round_type_label(t: String) -> String:
	match t:
		"UNDYINGALOG":
			return "Undyingalog"
		"FOLEY":
			return "Foley"
		"UNKNOWABLE_SFX", "UFSFX":
			return "Unknowable SFX"
		"FOUNDTRACK":
			return "Foundtrack"
		_:
			return t


func _prompt_pool(game: Dictionary) -> String:
	var prompts: Array = game.get("prompts", [])
	if prompts.is_empty():
		return ""
	var lines := PackedStringArray()
	lines.append("[b]Possible lines[/b]")
	for p in prompts:
		lines.append("• %s" % str(p))
	return "\n".join(lines)


func _cryptid_submissions(game: Dictionary, revealed: bool) -> String:
	var subs: Array = game.get("submissions", [])
	if subs.is_empty():
		return "[i]No takes yet.[/i]"
	var lines := PackedStringArray()
	for s in subs:
		var who := str(s.get("playerName", "")) if revealed else "a player"
		var tail := "  (+%d)" % int(s.get("points", 0)) if revealed else ""
		if str(s.get("kind", "")) == "recording":
			lines.append("♪ %s%s" % [who, tail])
		else:
			lines.append("“%s” — %s%s" % [str(s.get("label", "")), who, tail])
	return "\n".join(lines)


# --- Film playback ------------------------------------------------------------

func _ensure_film(path: String) -> void:
	if path == _film_path and _film.stream != null:
		return
	_film_path = path
	var stream := load(path)
	if stream == null:
		push_warning("[host] could not load film: " + path)
		return
	_film.stream = stream
	_film.stop()
	_film_started = false
	_film_target = -1.0


func _play_film_until(seconds: float) -> void:
	if _film.stream == null:
		return
	if seconds > 0.0 and _film.stream_position >= seconds:
		_film.paused = true
		_film_target = -1.0
		return
	if not _film_started:
		_film.play()
		_film_started = true
	_film.paused = false
	_film_target = seconds


func _pause_film() -> void:
	if _film.stream != null:
		_film.paused = true
	_film_target = -1.0


func _stop_film() -> void:
	if _film == null:
		return
	_film.stop()
	_film_started = false
	_film_target = -1.0


## The Screening: the foundtrack winner is in, so roll the music-less cut from
## the top and let the recorded takes and the chosen song play over it.
func _replay_film_from_start() -> void:
	if _film == null or _film.stream == null:
		return
	_film.stop()
	_film.play()
	_film.paused = false
	_film_started = true
	_film_target = -1.0


func _update_film() -> void:
	if _film == null or _film_target < 0.0 or _film.stream == null:
		return
	if _film.stream_position >= _film_target:
		_film.paused = true
		_film_target = -1.0


# --- Host-side screening of player recordings ---------------------------------

func _enqueue_screening(game: Dictionary) -> void:
	var round := int(game.get("round", 0))
	if round == _audio_round:
		return
	_audio_round = round
	_audio_queue = PackedStringArray()
	_audio_res_queue = PackedStringArray()

	var subs: Array = game.get("submissions", [])
	var winners := _top_submissions(subs)

	if str(game.get("roundType", "")) == "FOUNDTRACK":
		# Only the winning song plays: the track the players voted for, keyed off
		# the submission's choice id (each player was dealt a private hand, so the
		# public option list is empty).
		if not winners.is_empty():
			var cid := str(winners[0].get("choiceId", ""))
			if FOUNDTRACK_MUSIC.has(cid):
				_audio_res_queue.append(FOUNDTRACK_MUSIC[cid])
	else:
		# Only the winning take(s) play — never every player's.
		for s in winners:
			var url := str(s.get("audioUrl", ""))
			if not url.is_empty():
				_audio_queue.append(url)

	_pump_audio()


## The submissions tied for the most points this round. Used to screen only the
## winners, not every take. (When no votes were cast — e.g. one or two players —
## every submission is tied at zero and all play, matching "used directly".)
func _top_submissions(subs: Array) -> Array:
	if subs.is_empty():
		return []
	var best := int(subs[0].get("points", 0))
	for s in subs:
		best = maxi(best, int(s.get("points", 0)))
	var winners: Array = []
	for s in subs:
		if int(s.get("points", 0)) == best:
			winners.append(s)
	return winners


func _pump_audio() -> void:
	if _audio_player == null or _audio_busy or _audio_player.playing:
		return
	# A queued local track (foundtrack song) plays straight from res://.
	if not _audio_res_queue.is_empty():
		var res_path := _audio_res_queue[0]
		_audio_res_queue.remove_at(0)
		var stream := load(res_path)
		if stream is AudioStream:
			_audio_player.stream = stream
			_audio_player.play()
			return
	if _audio_queue.is_empty():
		return
	_audio_busy = true
	var path := _audio_queue[0]
	_audio_queue.remove_at(0)
	var err := _audio_http.request(
		BackendConfig.url + path, PackedStringArray(), HTTPClient.METHOD_GET)
	if err != OK:
		_audio_busy = false


func _on_audio_fetched(result: int, response_code: int, _headers: PackedStringArray, body: PackedByteArray) -> void:
	_audio_busy = false
	if result == HTTPRequest.RESULT_SUCCESS and response_code == 200:
		var wav := _wav_from_bytes(body)
		if wav != null:
			_audio_player.stream = wav
			_audio_player.play()
			return
	_pump_audio()


func _wav_from_bytes(bytes: PackedByteArray) -> AudioStreamWAV:
	if bytes.size() < 44 or bytes.slice(0, 4).get_string_from_ascii() != "RIFF":
		return null
	var channels := 1
	var mix_rate := 44100
	var bits := 16
	var data_offset := -1
	var data_size := 0
	var pos := 12
	while pos + 8 <= bytes.size():
		var chunk_id := bytes.slice(pos, pos + 4).get_string_from_ascii()
		var chunk_size := bytes.decode_u32(pos + 4)
		if chunk_id == "fmt ":
			channels = bytes.decode_u16(pos + 10)
			mix_rate = bytes.decode_u32(pos + 12)
			bits = bytes.decode_u16(pos + 22)
		elif chunk_id == "data":
			data_offset = pos + 8
			data_size = chunk_size
			break
		pos += 8 + chunk_size + (chunk_size & 1)
	if data_offset < 0 or data_offset + data_size > bytes.size():
		return null
	var wav := AudioStreamWAV.new()
	wav.mix_rate = mix_rate
	wav.stereo = channels == 2
	wav.format = AudioStreamWAV.FORMAT_16_BITS if bits == 16 else AudioStreamWAV.FORMAT_8_BITS
	wav.data = bytes.slice(data_offset, data_offset + data_size)
	return wav


func _works_list(game: Dictionary) -> String:
	var lines := PackedStringArray()
	for w in game.get("works", []):
		var title := str(w.get("title", ""))
		if title.is_empty():
			continue
		lines.append("%s — %s" % [str(w.get("playerName", "?")), title])
	return "\n".join(lines)


func _answers_list(game: Dictionary, revealed: bool) -> String:
	var lines := PackedStringArray()
	for a in game.get("answers", []):
		var text := str(a.get("text", ""))
		if revealed:
			var points := int(a.get("points", 0))
			lines.append("“%s”\n    — %s  (+%d)" % [text, str(a.get("authorName", "?")), points])
		else:
			lines.append("“%s”" % text)
	return "\n".join(lines)


func _scores_list(entries: Array) -> String:
	var lines := PackedStringArray()
	var rank := 1
	for e in entries:
		lines.append("%d. %s — %d" % [rank, str(e.get("name", "?")), int(e.get("score", 0))])
		rank += 1
	return "\n".join(lines) if not lines.is_empty() else "[i]—[/i]"


# --- Host controls ------------------------------------------------------------

func _send_settings() -> void:
	if _ws.get_ready_state() != WebSocketPeer.STATE_OPEN:
		return
	_ws.send_text(JSON.stringify({
		"type": "settings",
		"settings": {
			"familyFriendly": _family_check.button_pressed,
			"answerTimerSeconds": int(_timer_spin.value),
		},
	}))


func _on_start_pressed() -> void:
	if _ws.get_ready_state() != WebSocketPeer.STATE_OPEN:
		return
	if GAME_IDS[_game_index] == "cryptid-cinema":
		# show episode chooser in same scene to preserve room state
		_show_episodes()
		return
	var payload = {"type": "start", "gameId": GAME_IDS[_game_index]}
	_ws.send_text(JSON.stringify(payload))


func _on_advance_pressed() -> void:
	if _ws.get_ready_state() != WebSocketPeer.STATE_OPEN:
		return
	_ws.send_text(JSON.stringify({"type": "advance"}))


# --- UI construction ----------------------------------------------------------

func _build_ui() -> void:
	for child in get_children():
		child.queue_free()

	# Same backdrop as the title screen: a near-black wash, then the drifting
	# slideshow, then the UI on top of both.
	var background := ColorRect.new()
	background.set_anchors_preset(Control.PRESET_FULL_RECT)
	background.color = Color(0.08, 0.07, 0.06, 1.0)
	background.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(background)

	_slideshow = load("res://src/Slideshow.gd").new()
	_slideshow.set_anchors_preset(Control.PRESET_FULL_RECT)
	_slideshow.mouse_filter = Control.MOUSE_FILTER_IGNORE
	add_child(_slideshow)

	var margin := MarginContainer.new()
	margin.set_anchors_preset(Control.PRESET_FULL_RECT)
	margin.add_theme_constant_override("margin_left", 48)
	margin.add_theme_constant_override("margin_right", 48)
	margin.add_theme_constant_override("margin_top", 36)
	margin.add_theme_constant_override("margin_bottom", 36)
	add_child(margin)

	var root := VBoxContainer.new()
	root.add_theme_constant_override("separation", 16)
	margin.add_child(root)

	var title := Label.new()
	title.text = "THE PUBLIC RECORDS COLLECTION"
	title.add_theme_color_override("font_color", GOLD)
	var title_font := load(TITLE_FONT)
	if title_font:
		title.add_theme_font_override("font", title_font)
	title.add_theme_font_size_override("font_size", 52)
	root.add_child(title)

	_lobby_panel = VBoxContainer.new()
	_lobby_panel.add_theme_constant_override("separation", 16)
	root.add_child(_lobby_panel)

	_game_panel = VBoxContainer.new()
	_game_panel.add_theme_constant_override("separation", 12)
	_game_panel.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_game_panel.visible = false
	root.add_child(_game_panel)

	_build_lobby(_lobby_panel)

	_game_title = Label.new()
	_game_title.add_theme_font_size_override("font_size", 30)
	_game_panel.add_child(_game_title)

	_cryptid_view = HBoxContainer.new()
	_cryptid_view.add_theme_constant_override("separation", 16)
	_cryptid_view.visible = false
	_cryptid_view.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_game_panel.add_child(_cryptid_view)

	var film_aspect := AspectRatioContainer.new()
	film_aspect.ratio = 16.0 / 9.0
	film_aspect.custom_minimum_size = Vector2(960, 720)
	film_aspect.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	film_aspect.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_cryptid_view.add_child(film_aspect)

	_film = VideoStreamPlayer.new()
	_film.expand = true
	_film.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	_film.size_flags_vertical = Control.SIZE_EXPAND_FILL
	film_aspect.add_child(_film)

	_cryptid_notes = RichTextLabel.new()
	_cryptid_notes.bbcode_enabled = true
	_cryptid_notes.fit_content = true
	_cryptid_notes.scroll_active = true
	_cryptid_notes.custom_minimum_size = Vector2(420, 0)
	_cryptid_notes.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_cryptid_view.add_child(_cryptid_notes)

	_game_body = RichTextLabel.new()
	_game_body.bbcode_enabled = true
	_game_body.fit_content = true
	_game_body.scroll_active = true
	_game_body.size_flags_vertical = Control.SIZE_EXPAND_FILL
	_game_panel.add_child(_game_body)

	_advance_button = Button.new()
	_advance_button.text = "Advance ▸"
	_advance_button.disabled = true
	_advance_button.pressed.connect(_on_advance_pressed)
	_game_panel.add_child(_advance_button)
	_style_button(_advance_button)

	_status_label = Label.new()
	_status_label.add_theme_color_override("font_color", Color(0.85, 0.82, 0.72))
	_apply_font(_status_label, BODY_FONT, 18)
	root.add_child(_status_label)

	_audio_player = AudioStreamPlayer.new()
	_audio_player.finished.connect(_pump_audio)
	add_child(_audio_player)

	_audio_http = HTTPRequest.new()
	add_child(_audio_http)
	_audio_http.request_completed.connect(_on_audio_fetched)


func _build_lobby(panel: VBoxContainer) -> void:
	var join_box := HBoxContainer.new()
	join_box.add_theme_constant_override("separation", 24)
	panel.add_child(join_box)

	_code_label = Label.new()
	_code_label.text = "····"
	_code_label.add_theme_color_override("font_color", GOLD)
	_apply_font(_code_label, TITLE_FONT, 88)
	join_box.add_child(_code_label)

	var url_col := VBoxContainer.new()
	url_col.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	join_box.add_child(url_col)

	var url_caption := Label.new()
	url_caption.text = "Join at"
	url_caption.add_theme_color_override("font_color", ACCENT)
	_apply_font(url_caption, BODY_FONT, 22)
	url_col.add_child(url_caption)

	_url_label = Label.new()
	_url_label.text = BackendConfig.players_url()
	_url_label.add_theme_color_override("font_color", Color(0.92, 0.9, 0.82))
	_apply_font(_url_label, BODY_FONT, 24)
	url_col.add_child(_url_label)
	var url_btns := HBoxContainer.new()
	url_btns.add_theme_constant_override("separation", 8)
	url_col.add_child(url_btns)

	var copy_url_btn := Button.new()
	copy_url_btn.text = "Copy join URL"
	_style_button(copy_url_btn)
	copy_url_btn.pressed.connect(func() -> void: DisplayServer.clipboard_set(BackendConfig.players_url()))
	url_btns.add_child(copy_url_btn)

	var copy_code_btn := Button.new()
	copy_code_btn.text = "Copy room code"
	_style_button(copy_code_btn)
	copy_code_btn.pressed.connect(func() -> void: DisplayServer.clipboard_set(_room_code))
	url_btns.add_child(copy_code_btn)


	var players_caption := Label.new()
	players_caption.text = "Players"
	players_caption.add_theme_color_override("font_color", ACCENT)
	_apply_font(players_caption, BODY_FONT, 26)
	panel.add_child(players_caption)

	_players_label = Label.new()
	_players_label.text = "Waiting for players…"
	_players_label.add_theme_color_override("font_color", Color(0.92, 0.9, 0.82))
	_apply_font(_players_label, BODY_FONT, 24)
	panel.add_child(_players_label)

	var controls := HBoxContainer.new()
	controls.add_theme_constant_override("separation", 20)
	panel.add_child(controls)

	_family_check = CheckBox.new()
	_family_check.text = "Family-friendly"
	_family_check.button_pressed = true
	_family_check.add_theme_color_override("font_color", Color(0.92, 0.9, 0.82))
	_apply_font(_family_check, BODY_FONT, 22)
	_family_check.toggled.connect(func(_on: bool) -> void: _send_settings())
	controls.add_child(_family_check)

	_timer_spin = SpinBox.new()
	_timer_spin.min_value = 5
	_timer_spin.max_value = 300
	_timer_spin.value = 30
	_timer_spin.suffix = "s timer"
	_timer_spin.value_changed.connect(func(_v: float) -> void: _send_settings())
	controls.add_child(_timer_spin)

	_game_select = OptionButton.new()
	for i in GAME_LABELS.size():
		_game_select.add_item(str(GAME_LABELS[i]), i)
	_game_select.selected = _game_index
	_style_button(_game_select)
	_game_select.item_selected.connect(func(idx: int) -> void:
		_game_index = idx
		_update_start_label())
	controls.add_child(_game_select)

	_start_button = Button.new()
	_start_button.disabled = true
	_start_button.pressed.connect(_on_start_pressed)
	_style_button(_start_button)
	controls.add_child(_start_button)
	_update_start_label()


## Apply one of the title-screen fonts (Playfair for display text, Old Standard
## for body/UI text) at the given size.
func _apply_font(node: Control, path: String, size: int) -> void:
	var font := load(path)
	if font:
		node.add_theme_font_override("font", font)
	node.add_theme_font_size_override("font_size", size)


## Give a button the title screen's look: translucent dark fill, gold hairline
## border, Old Standard label.
func _style_button(btn: Button) -> void:
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.12, 0.12, 0.12, 0.6)
	style.border_color = ACCENT
	style.set_border_width_all(2)
	style.set_corner_radius_all(4)
	btn.add_theme_stylebox_override("normal", style)
	btn.add_theme_stylebox_override("hover", style)
	btn.add_theme_stylebox_override("pressed", style)
	btn.add_theme_stylebox_override("disabled", style)
	btn.add_theme_color_override("font_color", GOLD)
	btn.add_theme_color_override("font_disabled_color", Color(0.6, 0.56, 0.48))
	_apply_font(btn, BODY_FONT, 24)


func _update_start_label() -> void:
	if _start_button != null:
		_start_button.text = "BEGIN THE GAME"


func _set_status(text: String) -> void:
	if _status_label != null:
		_status_label.text = text
	print("[host] ", text)
func _video_playing_now(game: Dictionary, phase: String) -> bool:
	return phase in ["play", "film", "review"] and not phase.begins_with("vote") and not phase.begins_with("record") and not phase.begins_with("waiting")


var _episodes_panel: Control = null

func _show_episodes() -> void:
	if _episodes_panel != null:
		return
	var episodes = load("res://scenes/Episodes.tscn").instantiate()
	add_child(episodes)
	_episodes_panel = episodes

func _on_episode_selected(episode_id: String) -> void:
	if _episodes_panel != null:
		_episodes_panel.queue_free()
		_episodes_panel = null
	var payload = {"type": "start", "gameId": "cryptid-cinema"}
	payload["episodeId"] = episode_id
	if _ws.get_ready_state() == WebSocketPeer.STATE_OPEN:
		_ws.send_text(JSON.stringify(payload))
