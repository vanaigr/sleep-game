extends VBoxContainer

@export var detailedStatsPopup: DetailedStatsWindow

var bridgePlugin := Engine.get_singleton("BridgePlugin")
var lastVersion: Variant

func _process(delta: float) -> void:
	# TODO: instead of this, save all relevant data (e.g. also current timezone and locale)
	# and then check it in `getAllPeriodStats()` instead
	var version = bridgePlugin.getSleepDataVersion()
	if version == lastVersion:
		return
	lastVersion = version

	var newItems = bridgePlugin.getAllPeriodsStats()
	
	for child in get_children():
		child.queue_free()
			
	for item in newItems:
		var forClick = MarginContainer.new()
		forClick.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		
		var margin := MarginContainer.new()
		margin.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		margin.add_theme_constant_override("margin_left", 12)
		margin.add_theme_constant_override("margin_right", 12)
		margin.add_theme_constant_override("margin_top", 6)
		margin.add_theme_constant_override("margin_bottom", 6)
		forClick.add_child(margin)
		
		var button := Button.new()
		button.text = ""
		#button.flat = true
		#button.focus_mode = Control.FOCUS_NONE
		#button.mouse_filter = Control.MOUSE_FILTER_STOP
		button.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		button.size_flags_vertical = Control.SIZE_EXPAND_FILL
		button.set_anchors_preset(Control.PRESET_FULL_RECT, true)
		button.mouse_filter = Control.MOUSE_FILTER_PASS
		button.pressed.connect(func(): detailedStatsPopup.open_with_data(item))
		forClick.add_child(button)
		
		var row := HBoxContainer.new()
		row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.custom_minimum_size.y = 40
		row.add_theme_constant_override("separation", 20)
		margin.add_child(row)

		var text1 := Label.new()
		text1.text = item["date"]
		text1.add_theme_font_size_override("font_size", 30)
		text1.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		text1.custom_minimum_size.x = 0

		var text2 := Label.new()
		text2.text = item["quality"]
		text2.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		text2.add_theme_font_size_override("font_size", 30)

		var text3 := Label.new()
		text3.text = item["duration"]
		text3.size_flags_horizontal = Control.SIZE_SHRINK_CENTER
		text3.add_theme_font_size_override("font_size", 30)

		row.add_child(text1)
		row.add_child(text2)
		row.add_child(text3)
				
		add_child(forClick)
