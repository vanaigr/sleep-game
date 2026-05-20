extends Label

var bridgePlugin := Engine.get_singleton("BridgePlugin")

func _process(delta: float) -> void:
	var stats = bridgePlugin.getStats()
	if stats == null:
		text = 'Нет данных'
	else:
		text = '\n'.join([
			'Время пробуждения: ' + stats['end_time'], 
			'Время засыпания: ' + stats['begin_time'],
			'Качество: ' + stats['quality'],
			'Продолжительность: ' + stats['duration'],
		])
