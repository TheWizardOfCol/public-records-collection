extends Control
## Main menu / lobby screen for the host display.
##
## This is the native Godot "display" app the host runs: players join from
## their phones at the URL the game shows here. This skeleton only proves the
## project boots and resolves its backend; the lobby, room code, and per-game
## views are built on top of it.

@onready var _status: Label = $Center/Status


func _ready() -> void:
	_status.text = "\n".join([
		"The Public Records Collection",
		"",
		"Backend: %s" % BackendConfig.url,
		"REST:    %s" % BackendConfig.rest_url(),
		"WS:      %s" % BackendConfig.ws_url(),
	])
