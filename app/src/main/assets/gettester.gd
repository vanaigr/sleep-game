extends Label

func _ready() -> void:
	pass
	
func _process(delta: float) -> void:
	Database.db.query_with_bindings('select id, period_id, type, recorded_time from sleep_records order by id', [])
	var records = Database.db.query_result

	var recordsByPeriod = {}
	for record in records:
		var byPeriod = recordsByPeriod.get(record["period_id"], [])
		byPeriod.append(record)
		recordsByPeriod[record["period_id"]] = byPeriod
	
	var sleepPeriods = recordsByPeriod.values()
	sleepPeriods.sort_custom(func(a, b): return a[0].id < b[0].id) # id is proxy for time
	
	var labelText = ""
	for sleepPeriod in sleepPeriods:
		labelText += "{ " 
		labelText += ",\n  ".join(sleepPeriod.map(func(it): return it["type"] + ": " + it["recorded_time"]))
		labelText += " }\n"
		
	text = labelText
