extends Control

@export var sticker_count := 20
@export var fall_speed := 30.0

var _stickers := []
var _spawn_y := -200.0

func _ready() -> void:
  var dir = DirAccess.open("res://assets/art")
  if not dir:
    return
  var files := []
  dir.list_dir_begin()
  var f := dir.get_next()
  while f != "":
    if not dir.current_is_dir() and f.ends_with(".webp"):
      files.append(f)
    f = dir.get_next()
  dir.list_dir_end()
  files.shuffle()
  var w = size.x
  for i in range(min(sticker_count, files.size())):
    var tex = load("res://assets/art/" + files[i])
    if tex == null:
      continue
    var spr = TextureRect.new()
    spr.texture = tex
    spr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
    spr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
    spr.pivot_offset = tex.get_size() / 2
    spr.modulate.a = 0.25
    spr.z_index = -1
    add_child(spr)
    var sc = randf_range(0.15, 0.35)
    spr.scale = Vector2(sc, sc)
    var x = randf_range(0, w)
    var y = randf_range(_spawn_y - 400, _spawn_y)
    spr.position = Vector2(x, y) - spr.pivot_offset * spr.scale
    spr.rotation = randf_range(-0.3, 0.3)
    _stickers.append({
      "spr": spr,
      "x": x,
      "speed": randf_range(20.0, 60.0),
      "rot": randf_range(-0.2, 0.2),
      "t": randf_range(0, 1000),
    })

func _process(delta: float) -> void:
  var h = size.y
  for s in _stickers:
    var spr := s.spr as TextureRect
    spr.position.y += s.speed * delta
    spr.rotation += s.rot * delta * 0.5
    if spr.position.y > h + 200:
      spr.position.y = _spawn_y - randf_range(0, 300)
