extends Control

@onready var _bg = $Background

func _ready() -> void:
  var bg_texture = load("res://assets/art/painted-fabric.webp")
  if bg_texture:
    _bg.texture = bg_texture
  # Hook up to Main or allow immediate start? For now, clicking or key press to proceed


func _input(event: InputEvent) -> void:
  if event.is_pressed():
    # Go to Main scene
    get_tree().change_scene_to_file("res://scenes/Main.tscn")
