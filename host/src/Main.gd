extends Control
## Host display / lobby for The Public Records Collection.
##
## The native Godot app the host runs on the shared screen. It creates a room
## via the REST API, then holds the lobby open over WebSocket: it shows the room
## code and join URL for players, the live player roster, and the host-only
## controls (settings, start). Per-game display views are layered on top of this
## lobby state machine later.

const GAME_ID := "get-lit"

var _http: HTTPRequest
var _ws := WebSocketPeer.new()
var _host_name := "Host"

var _room_code := ""
var _host_token := ""

# UI
var _code_label: Label
var _url_label: Label
var _players_label: Label
var _status_label: Label
var _start_button: Button
var _family_check: CheckBox
var _timer_spin: SpinBox


func _ready() -> void:
	for arg in OS.get_cmdline_args():
		if arg.begins_with("--host-name="):
			_host_name = arg.trim_prefix("--host-name=")

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


func _process(_delta: float) -> void:
	_ws.poll()
	match _ws.get_ready_state():
		WebSocketPeer.STATE_OPEN:
			while _ws.get_available_packet_count() > 0:
				_on_message(_ws.get_packet().get_string_from_utf8())
		WebSocketPeer.STATE_CLOSED:
			pass


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

	_set_status("Connecting lobby…")
	var err := _ws.connect_to_url(BackendConfig.lobby_ws_url())
	if err != OK:
		_set_status("WebSocket connect failed (error %d)." % err)
		return

	# The host message is sent once the socket opens (see _process open handler
	# path below). We queue it by sending after the first open poll.
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
		"closed":
			_set_status("Room closed: %s" % str(msg.get("reason", "")))
			_start_button.disabled = true
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

	var join_box := HBoxContainer.new()
	join_box.add_theme_constant_override("separation", 24)
	root.add_child(join_box)

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
	root.add_child(players_caption)

	_players_label = Label.new()
	_players_label.add_theme_font_size_override("font_size", 22)
	_players_label.text = "Waiting for players…"
	root.add_child(_players_label)

	var controls := HBoxContainer.new()
	controls.add_theme_constant_override("separation", 20)
	root.add_child(controls)

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

	_status_label = Label.new()
	_status_label.add_theme_font_size_override("font_size", 18)
	root.add_child(_status_label)


func _set_status(text: String) -> void:
	if _status_label != null:
		_status_label.text = text
	print("[host] ", text)
