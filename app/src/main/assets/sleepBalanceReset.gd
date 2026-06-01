extends Button

var bridgePlugin := Engine.get_singleton("BridgePlugin")

func _on_pressed() -> void:
	bridgePlugin.resetSleepBalance()
