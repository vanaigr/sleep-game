extends Control
class_name DetailedStatsWindow

@export var label: Label

func _ready() -> void:
	close()

func open_with_data(data: Dictionary) -> void:
	label.text = "test"
	ScreenStack.push(self, close)
	show()

func close() -> void:
	ScreenStack.delete(self)
	hide()

func _on_button_pressed() -> void:
	close()

func _unhandled_input(event: InputEvent) -> void:
	if not visible:
		return

	if event.is_action_pressed("ui_cancel"):
		get_viewport().set_input_as_handled()
		hide()
