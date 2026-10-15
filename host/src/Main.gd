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
var _audio_round := -1
var _audio_busy := false

## Dev aid: when > 0, the host auto-sends `advance` every N seconds while a game
## is running (set with --auto-advance=<seconds>). Handy for exercising the game
## display headless; leave 0 in normal use.
var _auto_advance := 0.0
var _auto_accum := 0.0

## Dev aid: when > 0, the host auto-starts the game once this many non-host
## players have joined (set with --auto-start=<n>). Leave 0 in normal use.
var _auto_start := 0
var _did_start := false


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
	# a game is running and show the big-screen view instead.
	_lobby_panel.visible = not in_game
	_game_panel.visible = in_game
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
	var rtype := _round_type_label(str(game.get("roundType", "")))
	_game_title.text = "Cryptid Cinema — Round %d / %d · %s" % [max(round, 1), total, rtype]
	print("[host] cryptid phase: %s (round %d/%d)" % [phase, round, total])

	var video := str(game.get("video", ""))
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

	_cryptid_notes.text = "\n".join(lines)


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
	var subs: Array = game.get("submissions", []).duplicate()
	subs.sort_custom(func(a, b): return int(a.get("points", 0)) > int(b.get("points", 0)))
	for s in subs:
		var url := str(s.get("audioUrl", ""))
		if not url.is_empty():
			_audio_queue.append(url)
	_pump_audio()


func _pump_audio() -> void:
	if _audio_player == null or _audio_busy or _audio_player.playing:
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
	_ws.send_text(JSON.stringify({"type": "start", "gameId": GAME_IDS[_game_index]}))


func _on_advance_pressed() -> void:
	if _ws.get_ready_state() != WebSocketPeer.STATE_OPEN:
		return
	_ws.send_text(JSON.stringify({"type": "advance"}))


# --- UI construction ----------------------------------------------------------

func _build_ui() -> void:
	for child in get_children():
		child.queue_free()

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
	title.text = "The Public Records Collection"
	title.add_theme_font_size_override("font_size", 40)
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
	film_aspect.ratio = 4.0 / 3.0
	film_aspect.custom_minimum_size = Vector2(640, 480)
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

	_status_label = Label.new()
	_status_label.add_theme_font_size_override("font_size", 18)
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
	_code_label.add_theme_font_size_override("font_size", 72)
	_code_label.text = "····"
	join_box.add_child(_code_label)

	var url_col := VBoxContainer.new()
	url_col.size_flags_vertical = Control.SIZE_SHRINK_CENTER
	join_box.add_child(url_col)

	var url_caption := Label.new()
	url_caption.text = "Join at"
	url_col.add_child(url_caption)

	_url_label = Label.new()
	_url_label.add_theme_font_size_override("font_size", 20)
	_url_label.text = BackendConfig.players_url()
	url_col.add_child(_url_label)

	var players_caption := Label.new()
	players_caption.text = "Players"
	players_caption.add_theme_font_size_override("font_size", 24)
	panel.add_child(players_caption)

	_players_label = Label.new()
	_players_label.add_theme_font_size_override("font_size", 22)
	_players_label.text = "Waiting for players…"
	panel.add_child(_players_label)

	var controls := HBoxContainer.new()
	controls.add_theme_constant_override("separation", 20)
	panel.add_child(controls)

	_family_check = CheckBox.new()
	_family_check.text = "Family-friendly"
	_family_check.button_pressed = true
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
	_game_select.item_selected.connect(func(idx: int) -> void:
		_game_index = idx
		_update_start_label())
	controls.add_child(_game_select)

	_start_button = Button.new()
	_start_button.disabled = true
	_start_button.pressed.connect(_on_start_pressed)
	controls.add_child(_start_button)
	_update_start_label()


func _update_start_label() -> void:
	if _start_button != null:
		_start_button.text = "Start " + str(GAME_LABELS[_game_index])


func _set_status(text: String) -> void:
	if _status_label != null:
		_status_label.text = text
	print("[host] ", text)
