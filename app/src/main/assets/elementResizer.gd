extends Node

@export var cam: Camera2D
@export var autoLeft: bool = false
@export var autoRight: bool = false
@export var autoTop: bool = false
@export var autoBottom: bool = false

func _ready() -> void:
	setPositions()

func _process(delta: float) -> void:
	setPositions()
	
func setPositions():
	if cam == null:
		return
		
	var p = getCameraWorldPosition() 
	var v = get_parent() as Variant
		
	if autoLeft:
		v.offset_left = p[0].x - p[1].x
	if autoRight:
		v.offset_right = p[0].x + p[1].x
	if autoTop:
		v.offset_top = p[0].y - p[1].y
	if autoBottom:
		v.offset_bottom = p[0].y + p[1].y

func getCameraWorldPosition() -> PackedVector2Array:
	var viewport_size = cam.get_viewport_rect().size
	var half_size = viewport_size * cam.zoom * 0.5
	var center = cam.global_position

	return [center, half_size]
