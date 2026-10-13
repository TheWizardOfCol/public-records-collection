extends Control

@onready var _start_button = $StartButton

func _ready() -> void:
  var style = StyleBoxFlat.new()
  style.bg_color = Color(0.12, 0.12, 0.12, 0.6)
  style.border_color = Color(0.8, 0.7, 0.5, 0.9)
  style.border_width_top = 2
  style.border_width_bottom = 2
  style.border_width_left = 2
  style.border_width_right = 2
  style.corner_radius_top_left = 4
  style.corner_radius_top_right = 4
  style.corner_radius_bottom_left = 4
  style.corner_radius_bottom_right = 4
  _start_button.add_theme_stylebox_override("normal", style)
  _start_button.add_theme_stylebox_override("hover", style)
  _start_button.add_theme_stylebox_override("pressed", style)
  var font = load("res://assets/fonts/old-standard-tt.ttf")
  if font:
    _start_button.add_theme_font_override("font", font)
  _start_button.add_theme_font_size_override("font_size", 36)
  _start_button.pressed.connect(_on_start_pressed)

func _on_start_pressed() -> void:
  get_tree().change_scene_to_file("res://scenes/Main.tscn")
