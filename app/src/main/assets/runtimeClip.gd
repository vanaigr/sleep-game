extends Node

func _ready() -> void:
	var parent = get_parent()
	if 'clip_contents' in parent:
		parent.clip_contents = true
