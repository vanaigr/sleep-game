@tool
class_name BsContainer
extends Container

# Godot does not allow any way to make a container clickable.
# So you have to add a transparent button on top of the UI.
# But Godot doesn't provide any way for the UI to define the size
# and the button to just exist. So this container is for that.

func _get_minimum_size() -> Vector2:
	var first := _first_control_child()
	if first == null:
		return Vector2.ZERO

	return first.get_combined_minimum_size()


func _notification(what: int) -> void:
	if what == NOTIFICATION_SORT_CHILDREN:
		var rect := Rect2(Vector2.ZERO, size)

		for child in get_children():
			if child is Control:
				fit_child_in_rect(child, rect)


func _first_control_child() -> Control:
	for child in get_children():
		if child is Control:
			return child

	return null
