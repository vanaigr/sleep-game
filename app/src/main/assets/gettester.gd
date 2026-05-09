extends Label

func _ready() -> void:
	Database.db.query_with_bindings('select id from test', [])
	var result = Database.db.query_result
	
	text = 'Db data: ' + str(result)
