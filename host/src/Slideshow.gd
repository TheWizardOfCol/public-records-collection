extends Control
## Title-screen slideshow.
##
## Replaces the old parallax + falling-sticker layers. Shows one image at a
## time, drifting gently in a small circle, and slowly crossfades to the next:
## hold ~[member hold_seconds], fade ~[member fade_seconds]. Calm and bounded —
## nothing accumulates over time, so it looks the same after an hour as it does
## after a minute.
##
## Images are read from [member image_dir]; drop replacements there and they are
## picked up with no code change (swap these for the "famous images" later).

@export_dir var image_dir := "res://assets/art_rev"
@export var hold_seconds := 10.0
@export var fade_seconds := 1.0
## Opacity of the images — kept below 1 so the title text stays legible on top.
@export var image_alpha := 0.6
## How far each image drifts from centre, in pixels (kept inside [constant MARGIN]).
@export var drift_radius := 22.0
@export var drift_speed := 0.15

## Extra bleed beyond the viewport so drifting never exposes an edge.
const MARGIN := 48.0
const IMAGE_EXTENSIONS := ["webp", "png", "jpg", "jpeg"]

var _textures: Array = []
var _slots: Array = []
var _order: Array = []
var _pos := 0
var _front := 0
var _fading := false
var _timer := 0.0


func _ready() -> void:
	_load_textures()
	if _textures.is_empty():
		return

	_slots = [_make_slot(), _make_slot()]

	if _textures.size() == 1:
		_assign(0, _textures[0])
		_slots[1].node.visible = false
		_front = 0
		_timer = hold_seconds
		return

	_shuffle()
	_assign(0, _take())
	_assign(1, _take())
	_slots[0].node.modulate.a = image_alpha
	_slots[1].node.modulate.a = 0.0
	_front = 0
	_timer = hold_seconds


func _process(delta: float) -> void:
	if _slots.is_empty():
		return

	_drift(_slots[0], delta)
	_drift(_slots[1], delta)

	if _textures.size() < 2:
		return

	if _fading:
		_timer += delta
		var t := clampf(_timer / maxf(fade_seconds, 0.01), 0.0, 1.0)
		_slots[_front].node.modulate.a = (1.0 - t) * image_alpha
		_slots[1 - _front].node.modulate.a = t * image_alpha
		if t >= 1.0:
			_fading = false
			_front = 1 - _front
			_timer = hold_seconds
	else:
		_timer -= delta
		if _timer <= 0.0:
			var back := 1 - _front
			_assign(back, _take())
			_slots[back].node.modulate.a = 0.0
			_fading = true
			_timer = 0.0


# --- internals ----------------------------------------------------------------

func _load_textures() -> void:
	var dir := DirAccess.open(image_dir)
	if dir == null:
		push_warning("Slideshow: cannot open %s" % image_dir)
		return
	var files: Array = []
	dir.list_dir_begin()
	var f := dir.get_next()
	while f != "":
		if not dir.current_is_dir() and not f.begins_with(".") \
				and f.get_extension().to_lower() in IMAGE_EXTENSIONS:
			files.append(f)
		f = dir.get_next()
	dir.list_dir_end()
	files.sort()
	for fname in files:
		var tex := load(image_dir.path_join(fname))
		if tex is Texture2D:
			_textures.append(tex)
	_order.resize(_textures.size())


func _make_slot() -> Dictionary:
	var tr := TextureRect.new()
	tr.set_anchors_and_offsets_preset(Control.PRESET_FULL_RECT)
	tr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
	tr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
	tr.mouse_filter = Control.MOUSE_FILTER_IGNORE
	tr.offset_left = -MARGIN
	tr.offset_top = -MARGIN
	tr.offset_right = MARGIN
	tr.offset_bottom = MARGIN
	add_child(tr)
	return {"node": tr, "angle": randf_range(0.0, TAU)}


func _assign(slot: int, texture: Texture2D) -> void:
	if texture == null:
		return
	var s: Dictionary = _slots[slot]
	s.node.texture = texture
	s.angle = randf_range(0.0, TAU)


func _drift(s: Dictionary, delta: float) -> void:
	s.angle += drift_speed * delta
	var pan := Vector2(cos(s.angle), sin(s.angle)) * drift_radius
	var node: TextureRect = s.node
	node.offset_left = -MARGIN + pan.x
	node.offset_right = MARGIN + pan.x
	node.offset_top = -MARGIN + pan.y
	node.offset_bottom = MARGIN + pan.y


func _take() -> Texture2D:
	if _order.is_empty():
		return null
	if _pos >= _order.size():
		var last: int = _order[_order.size() - 1]
		_shuffle()
		if _order.size() > 1 and _order[0] == last:
			var tmp: int = _order[0]
			_order[0] = _order[1]
			_order[1] = tmp
		_pos = 0
	var idx: int = _order[_pos]
	_pos += 1
	return _textures[idx]


func _shuffle() -> void:
	for i in _order.size():
		_order[i] = i
	_order.shuffle()
