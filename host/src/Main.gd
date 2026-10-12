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

const GAME_ID := "get-lit"

var _http: HTTPRequest
var _ws := WebSocketPeer.new()
var _host_name := "Host"

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

# UI — game
var _game_panel: VBoxContainer
var _game_title: Label
var _game_body: RichTextLabel

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
	var round := int(game.get("round", 0))
	var total := int(game.get("totalRounds", 0))

	# The lobby furniture only makes sense before/around a game; hide it once
	# a game is running and show the big-screen view instead.
	var in_game := phase != ""
	_lobby_panel.visible = not in_game
	_game_panel.visible = in_game
	_advance_button.disabled = phase == "finished" or phase == ""

	if not in_game:
		_game_body.text = ""
		return

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
	_ws.send_text(JSON.stringify({"type": "start", "gameId": GAME_ID}))


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
	_game_panel.visible = false
	root.add_child(_game_panel)

	_build_lobby(_lobby_panel)

	_game_title = Label.new()
	_game_title.add_theme_font_size_override("font_size", 30)
	_game_panel.add_child(_game_title)

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

	_start_button = Button.new()
	_start_button.text = "Start Get Lit"
	_start_button.disabled = true
	_start_button.pressed.connect(_on_start_pressed)
	controls.add_child(_start_button)


func _set_status(text: String) -> void:
	if _status_label != null:
		_status_label.text = text
	print("[host] ", text)
