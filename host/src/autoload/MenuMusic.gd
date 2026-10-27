extends Node
## Looping menu music for the title and lobby screens ("Canon in D" by
## Pachelbel/Kevin MacLeod), routed through the Music bus so the pause menu's
## Music slider controls it.
##
## The autoload starts the track as soon as the app is alive (which covers the
## title screen), keeps it on across scene changes, and stops it when the host
## calls stop_menu — i.e. the moment a game actually starts.

const MUSIC_PATH := "res://assets/menu/canon-in-d-major.mp3"
const MENU_BUS := "Music"

var _player: AudioStreamPlayer

func _ready() -> void:
	_ensure_bus(MENU_BUS)
	_player = AudioStreamPlayer.new()
	_player.bus = MENU_BUS
	var stream := load(MUSIC_PATH)
	if stream is AudioStreamMP3:
		stream.loop = true
	_player.stream = stream
	add_child(_player)
	play_menu()

## Ensures the host-menu buses Music/SFX/Voice exist so volume sliders in the
## pause menu always have something to control.
static func _ensure_bus(name: String) -> void:
	if AudioServer.get_bus_index(name) == -1:
		AudioServer.add_bus()
		AudioServer.set_bus_name(AudioServer.bus_count - 1, name)
		AudioServer.set_bus_send(AudioServer.get_bus_index(name), "Master")

## Music for the menu screens; a no-op when it is already rolling.
func play_menu() -> void:
	if _player != null and not _player.playing:
		_player.play()

## Called once a game begins — the pack's own sounds take over.
func stop_menu() -> void:
	if _player != null:
		_player.stop()
