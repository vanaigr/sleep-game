@tool
extends Container
class_name Screen

func _ready() -> void:
	CameraSize.screenSizeChanged.connect(func(): _sort_children())

func _notification(what: int) -> void:
	if what == NOTIFICATION_SORT_CHILDREN:
		_sort_children()

func _sort_children() -> void:
	var s = CameraSize.screenSize
	for child in get_children():
		if child is Control and child.visible:
			fit_child_in_rect(child, Rect2(Vector2(-s.x * 0.5, -s.y * 0.5), s))
