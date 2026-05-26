extends VBoxContainer

var bridgePlugin := Engine.get_singleton("BridgePlugin")

func _process(delta: float) -> void:
	var newItems = bridgePlugin.getAllPeriodsStats()
	
	for child in get_children():
		child.queue_free()
			
	for item in newItems:
		var child := MarginContainer.new()
		child.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		child.add_theme_constant_override("margin_left", 12)
		child.add_theme_constant_override("margin_right", 12)
		child.add_theme_constant_override("margin_top", 6)
		child.add_theme_constant_override("margin_bottom", 6)
	
		var row := HBoxContainer.new()
		row.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		row.custom_minimum_size.y = 40
		row.add_theme_constant_override("separation", 20)
		child.add_child(row)

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
		
		add_child(child)
