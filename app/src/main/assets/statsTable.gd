extends Label

var bridgePlugin := Engine.get_singleton("BridgePlugin")

func _process(delta: float) -> void:
	var stats = bridgePlugin.getLastCompletePeriodStats()
	if stats == null:
		text = 'Нет данных'
	else:
		text = '\n'.join([
			'Время пробуждения: ' + stats['end_time'], 
			'Время засыпания: ' + stats['begin_time'],
			'Качество: ' + stats['quality'],
			'Продолжительность: ' + stats['duration'],
			"Время лежания в кровати: " + stats["duration_before_falling_asleep"],
		])
