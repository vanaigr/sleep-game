extends Control
class_name DetailedStatsWindow

var bridgePlugin := Engine.get_singleton("BridgePlugin")

@export var nightName: Label
@export var graph: SleepGraph
@export var nightInterruptions: Label
@export var sleepDuration: Label
@export var durationBeforeFallingAsleep: Label
@export var sleepQuality: Label
@export var sleepBalance: Label
var currentPeriodId: Variant

func _ready() -> void:
	close()

func open_with_data(data: Dictionary) -> void:
	nightName.text = "Ночь на " + str(data["date"])
	graph.periodId = data["period_id"]
	currentPeriodId = data["period_id"]
	nightInterruptions.text = "Кол-во ночных пробуждений: " + str(data["interruption_count"])
	sleepDuration.text = "Время сна: " + data["duration"]
	durationBeforeFallingAsleep.text = "Время лежания в кровати: " + data["duration_before_falling_asleep"]
	sleepQuality.text = "Качество сна: " + data["quality"]
	sleepQuality.text = "Сонный долг: " + data["sleep_balance"]
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


func _on_delete_pressed() -> void:
	if currentPeriodId != null:
		bridgePlugin.deleteSleepPeriod(currentPeriodId)
		close()
