extends Control

@onready var _bg = $Background
@onready var _host_button = $HostButton

func _ready() -> void:
  var bg_texture = load("res://assets/art/painted-fabric.webp")
  if bg_texture:
    _bg.texture = bg_texture
  var notecard = load("res://assets/art/blue-notecard.webp")
  if notecard and _host_button:
    _host_button.texture_normal = notecard
    _host_button.stretch_mode = TextureButton.STRETCH_KEEP_ASPECT_CENTERED
    _host_button.texture_click_mask = notecard
  if _host_button:
    _host_button.pressed.connect(_on_host_pressed)

func _on_host_pressed() -> void:
  get_tree().change_scene_to_file("res://scenes/Main.tscn")

func _input(event: InputEvent) -> void:
  if event.is_pressed():
    pass  # Don't auto-proceed; use the button
