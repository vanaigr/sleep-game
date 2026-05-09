extends Label

func _ready() -> void:
	pass
	
func _process(delta: float) -> void:
	Database.db.query_with_bindings('select id, begin_time, end_time from sleep_periods order by id', [])
	var ranges = Database.db.query_result
	
	Database.db.query_with_bindings('select sleep_period_id, recorded_time from sleep_interruptions order by id', [])
	var interruptions = Database.db.query_result
	var interruptionsByRange = {}
	for interruption in interruptions:
		var byRange = interruptionsByRange.get(interruption["sleep_period_id"], [])
		byRange.append(interruption)
		interruptionsByRange[interruption["sleep_period_id"]] = byRange
	
	
	var labelText = ""
	for range in ranges:
		var byRange = interruptionsByRange.get(range["id"], [])
		labelText = labelText + str(range["begin_time"])
		for interruption in byRange:
			labelText = labelText + "\n @ " + interruption["recorded_time"]
		labelText = labelText + "\n - " + str(range["end_time"]) + "\n"
		
	text = labelText
	
