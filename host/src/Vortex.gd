extends Control

@export var sticker_count := 12
@export var speed := 0.08

var _stickers := []

func _ready() -> void:
  var dir = DirAccess.open("res://assets/art")
  if not dir:
    return
  var files := []
  dir.list_dir_begin()
  var f := dir.get_next()
  while f != "":
    if not dir.current_is_dir() and f.ends_with(".webp"):
      # skip very large backgrounds for small floating stickers? keep most
      files.append(f)
    f = dir.get_next()
  dir.list_dir_end()
  files.shuffle()
  var center = size / 2
  for i in range(min(sticker_count, files.size())):
    var tex = load("res://assets/art/" + files[i])
    if tex == null:
      continue
    var spr = TextureRect.new()
    spr.texture = tex
    spr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
    spr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_CENTERED
    spr.pivot_offset = tex.get_size() / 2
    spr.modulate.a = 0.55
    add_child(spr)
    var ang = randf_range(0, TAU)
    var r = randf_range(60, min(center.x, center.y) - 120)
    _stickers.append({
      "spr": spr,
      "ang": ang,
      "r": r,
      "spin": randf_range(-0.2, 0.2),
      "size": randf_range(0.25, 0.5),
      "t0": randf_range(0, TAU),
    })

func _process(delta: float) -> void:
  var center = size / 2
  for s in _stickers:
    s.ang += delta * speed * (1.0 + s.spin * 0.5)
    var r = s.r * (1.0 + sin(Time.get_ticks_msec() / 1000.0 + s.t0) * 0.1)
    var x = center.x + cos(s.ang) * r
    var y = center.y + sin(s.ang) * r
    var spr := s.spr as TextureRect
    var sc = s.size * (0.9 + 0.1 * sin(Time.get_ticks_msec() / 800.0 + s.t0))
    spr.scale = Vector2(sc, sc)
    spr.position = Vector2(x, y) - spr.pivot_offset * spr.scale
    spr.rotation = s.ang + sin(Time.get_ticks_msec() / 600.0 + s.t0) * 0.3
