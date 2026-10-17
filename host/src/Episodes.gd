extends Control


@onready var _scroll = $ScrollContainer
@onready var _list = $ScrollContainer/VBoxContainer

func _ready() -> void:
	var style = StyleBoxFlat.new()
	style.bg_color = Color(0.08, 0.07, 0.06, 1)
	$Background.add_theme_stylebox_override("panel", style)
	_load_episodes()

func _load_episodes() -> void:
	var episodes = _load_json("res://assets/cryptid/episodes.json")
	if episodes is Array:
		for i in episodes.size():
			var ep = episodes[i]
			var btn = Button.new()
			btn.text = str(ep.get("title", "Episode"))
			btn.custom_minimum_size = Vector2(280, 80)
			# simple styling
			btn.pressed.connect(func(): _select_episode(i))
			_list.add_child(btn)
	elif episodes is Dictionary and episodes.has("episodes"):
		var list = episodes["episodes"]
		for i in list.size():
			var ep = list[i]
			var btn = Button.new()
			btn.text = str(ep.get("title", "Episode"))
			btn.custom_minimum_size = Vector2(280, 80)
			btn.pressed.connect(func(): _select_episode(i))
			_list.add_child(btn)

func _select_episode(idx: int) -> void:
	if has_node("/root/EpisodeSelect"):
		get_node("/root/EpisodeSelect").selected_episode = idx
	# go back to previous scene (Main)
	# go back and signal main to start
	var main_scene = load("res://scenes/Main.tscn")
	get_tree().change_scene_to_file("res://scenes/Main.tscn")

func _load_json(path: String) -> Variant:
	var f = FileAccess.open(path, FileAccess.READ)
	if not f: return []
	var txt = f.get_as_text()
	var p = JSON.new()
	var err = p.parse(txt)
	if err != OK: return []
	return p.data
