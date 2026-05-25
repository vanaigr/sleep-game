extends Button

@export var input: LineEdit
@export var validIndicator: Panel

var debug = false

var bridgePlugin := Engine.get_singleton("BridgePlugin")

func _process(delta: float) -> void:
	if not debug:
		input.text = bridgePlugin._debugGetCurrentTime()
	
	var timeValid = bridgePlugin._debugSetCurrentTime(input.text, debug)
	
	var style := StyleBoxFlat.new()
	style.bg_color = Color.GREEN if timeValid else Color.RED
	validIndicator.add_theme_stylebox_override("panel", style)
	
	text = "Время подменено" if debug else "Подмена времени отключена" 
	
func _on_pressed() -> void:
	debug = not debug
