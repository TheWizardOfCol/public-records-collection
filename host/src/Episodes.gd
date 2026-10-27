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
			_add_button(ep)
	elif episodes is Dictionary and episodes.has("episodes"):
		var list = episodes["episodes"]
		for i in list.size():
			_add_button(list[i])

func _add_button(ep: Dictionary) -> void:
	var btn = Button.new()
	btn.text = str(ep.get("title", "Episode"))
	btn.custom_minimum_size = Vector2(280, 80)
	var episode_id := str(ep.get("id", ""))
	btn.pressed.connect(func(): _select_episode(episode_id))
	_list.add_child(btn)

func _select_episode(episode_id: String) -> void:
	if has_node("/root/EpisodeSelect"):
		get_node("/root/EpisodeSelect").selected_id = episode_id
	var parent = get_parent()
	if parent != null and parent.has_method("_on_episode_selected"):
		parent.call("_on_episode_selected", episode_id)
	else:
		queue_free()

func _load_json(path: String) -> Variant:
	var f = FileAccess.open(path, FileAccess.READ)
	if not f: return []
	var txt = f.get_as_text()
	var p = JSON.new()
	var err = p.parse(txt)
	if err != OK: return []
	return p.data
