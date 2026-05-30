extends Node

@export var screen: Node
@export var autoLeft: bool = false
@export var autoRight: bool = false
@export var autoTop: bool = false
@export var autoBottom: bool = false

func _ready() -> void:
	setPositions()

func _process(delta: float) -> void:
	setPositions()
	
func setPositions():
	if screen == null:
		return
		
	var p = get_tree().root.content_scale_size * 0.5
	var v = get_parent() as Variant
		
	if autoLeft:
		v.offset_left = -p.x
	if autoRight:
		v.offset_right = p.x
	if autoTop:
		v.offset_top = -p.y
	if autoBottom:
		v.offset_bottom = p.y
