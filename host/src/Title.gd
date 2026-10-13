extends Control

@onready var _bg = $Background

func _ready() -> void:
  var bg_texture = load("res://assets/art/painted-fabric.webp")
  if bg_texture:
    _bg.texture = bg_texture
  # Hook up to Main or allow immediate start? For now, clicking or key press to proceed
