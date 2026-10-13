extends Control

@export var speed := 0.01
@export var textures := []

func _ready() -> void:
  var dir = DirAccess.open("res://assets/art_rev")
  if dir:
    var files := []
    dir.list_dir_begin()
    var f := dir.get_next()
    while f != "":
      if not dir.current_is_dir() and f.ends_with(".webp"):
        files.append(f)
      f = dir.get_next()
    dir.list_dir_end()
    files.shuffle()
    for fname in files:
      var tex = load("res://assets/art_rev/" + fname)
      if tex == null: continue
      var spr = TextureRect.new()
      spr.texture = tex
      spr.expand_mode = TextureRect.EXPAND_IGNORE_SIZE
      spr.stretch_mode = TextureRect.STRETCH_KEEP_ASPECT_COVERED
      spr.modulate.a = 0.12
      spr.z_index = -2
      add_child(spr)
      spr.anchor_left = 0.0
      spr.anchor_right = 1.0
      spr.anchor_top = 0.0
      spr.anchor_bottom = 1.0
      spr.offset_left = randf_range(-200, 200)
      spr.offset_right = randf_range(200, -200)
      spr.offset_top = randf_range(-200, 200)
      spr.offset_bottom = randf_range(200, -200)
      spr.set_meta("base", spr.position)
      spr.set_meta("ang", randf_range(0, TAU))
      textures.append(spr)

func _process(delta: float) -> void:
  var t = Time.get_ticks_msec() / 5000.0
  for spr in textures:
    var ang = spr.get_meta("ang") + t * speed
    spr.set_meta("ang", ang)
    spr.position = spr.get_meta("base") + Vector2(cos(ang), sin(ang)) * 20.0
