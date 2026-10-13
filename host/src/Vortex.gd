extends Control

@export var sticker_count := 10
@export var speed := 0.15

var _stickers := []
var _t := 0.0

func _ready() -> void:
  var dir = DirAccess.open("res://assets/art")
  if not dir:
    return
  var files := []
  dir.list_dir_begin()
  var f := dir.get_next()
  while f != "":
    if not dir.current_is_dir() and f.begins_with("vincent") == false and f.begins_with("portrait-of-vincent") == false and f.ends_with(".webp"):
      # prefer a mix; include most stickers? but we have many; just sample
      files.append(f)
    f = dir.get_next()
  dir.list_dir_end()
  # Fallback to all webps if none matched (but we have 103 stickers total? we staged art but stickers live in content/stickers)
  if files.is_empty():
    # try stickers dir from static? host doesn't have stickers copied except maybe; check
    pass
  for i in range(min(sticker_count, files.size())):
    var tex = load("res://assets/art/" + files[i])
    if tex == null:
      continue
    var spr = TextureRect.new()
    spr.texture = tex
    spr.pivot_offset = spr.size / 2
    spr.modulate.a = 0.5
    add_child(spr)
    _stickers.append({
      "spr": spr,
      "r": randf_range(40, 180),
      "ang": randf_range(0, TAU),
      "spin": randf_range(-0.4, 0.4),
      "size": randf_range(0.4, 0.8),
    })

func _process(delta: float) -> void:
  _t += delta * speed
  var center = size / 2
  for s in _stickers:
    var ang = s.ang + _t * s.spin
    s.ang = ang
    var r = s.r * (1.0 + sin(_t * 0.1 + ang) * 0.1)
    var x = center.x + cos(ang) * r
    var y = center.y + sin(ang) * r
    var spr := s.spr as TextureRect
    var sc = s.size * (0.9 + 0.1*sin(_t + ang))
    spr.scale = Vector2(sc, sc)
    spr.position = Vector2(x, y) - spr.pivot_offset * spr.scale
    spr.rotation = ang + _t * 0.05
