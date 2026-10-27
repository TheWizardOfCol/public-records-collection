extends CanvasLayer
## The host pause menu, opened with Escape:
##   - "Resume" closes it, "Main Menu" bails to the title screen.
##   - Three volume sliders (Music / Sound FX / Host voice) drive the
##     Music/SFX/Voice audio buses, so the eventual plugin pieces (spray-can
##     effects, host voice-over) land on their channel just by setting the bus.
##
## Autoload: always listening, process_mode ALWAYS so it works over anything.

const MENU := "res://scenes/Title.tscn"

var _panel: PanelContainer
var _root: Control

func _ready() -> void:
	layer = 100
	process_mode = Node.PROCESS_MODE_ALWAYS
	MenuMusic._ensure_bus("Music")
	MenuMusic._ensure_bus("SFX")
	MenuMusic._ensure_bus("Voice")
	_build()

func _unhandled_input(event: InputEvent) -> void:
	if event.is_action_pressed("ui_cancel") or event is InputEventKey and event.pressed and event.keycode == KEY_ESCAPE:
		toggle()

func _build() -> void:
	_root = Control.new()
	_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	_root.visible = false
	add_child(_root)

	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.6)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	_root.add_child(dim)

	_panel = PanelContainer.new()
	_panel.set_anchors_preset(Control.PRESET_CENTER)
	_panel.custom_minimum_size = Vector2(420, 0)
	var style := StyleBoxFlat.new()
	style.bg_color = Color(0.1, 0.09, 0.08, 0.96)
	style.border_color = Color(0.8, 0.7, 0.5)
	style.set_border_width_all(2)
	style.set_corner_radius_all(6)
	style.set_content_margin_all(20)
	_panel.add_theme_stylebox_override("panel", style)
	_root.add_child(_panel)

	var box := VBoxContainer.new()
	box.add_theme_constant_override("separation", 12)
	_panel.add_child(box)

	var title := Label.new()
	title.text = "PAUSED"
	title.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	title.add_theme_font_size_override("font_size", 30)
	box.add_child(title)

	box.add_child(_volume_row("Music", "Music", 0.6))
	box.add_child(_volume_row("Sound FX", "SFX", 1.0))
	box.add_child(_volume_row("Host voice", "Voice", 1.0))

	var resume := Button.new()
	resume.text = "Resume"
	resume.pressed.connect(toggle)
	box.add_child(resume)

	var to_menu := Button.new()
	to_menu.text = "Main Menu"
	to_menu.pressed.connect(func() -> void:
		toggle()
		get_tree().change_scene_to_file(MENU))
	box.add_child(to_menu)

func _volume_row(label: String, bus: String, initial: float) -> Control:
	var row := HBoxContainer.new()
	row.add_theme_constant_override("separation", 10)
	var caption := Label.new()
	caption.text = label
	caption.custom_minimum_size = Vector2(120, 0)
	row.add_child(caption)
	var slider := HSlider.new()
	slider.min_value = 0.0
	slider.max_value = 1.0
	slider.step = 0.05
	slider.value = initial
	slider.custom_minimum_size = Vector2(220, 0)
	var idx := AudioServer.get_bus_index(bus)
	if idx != -1:
		AudioServer.set_bus_volume_db(idx, linear_to_db(clampf(initial, 0.05, 1.0)))
	slider.value_changed.connect(func(v: float) -> void:
		var i := AudioServer.get_bus_index(bus)
		if i != -1:
			AudioServer.set_bus_volume_db(i, linear_to_db(maxf(v, 0.0001)) if v > 0 else -80.0))
	row.add_child(slider)
	return row

func toggle() -> void:
	_root.visible = not _root.visible
	get_tree().paused = _root.visible
