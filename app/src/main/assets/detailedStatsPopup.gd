extends Control
class_name DetailedStatsWindow

@export var graph: SleepGraph

func _ready() -> void:
	close()

func open_with_data(data: Dictionary) -> void:
	graph.periodId = data["periodId"]
	open()

func _on_button_pressed() -> void:
	close()

func open():
	ScreenStack.push(self, close)
	set_process_recursive(self, true)
	show()

func close() -> void:
	ScreenStack.delete(self)
	set_process_recursive(self, false)
	hide()
	
func set_process_recursive(node: Node, enabled: bool) -> void:
	node.set_process(enabled)

	for child in node.get_children():
		set_process_recursive(child, enabled)
