extends Node

var stack: Array[Dictionary] = []

func push(key: Variant, value: Callable):
	var foundI = stack.find_custom(func(el): el["key"] == key)
	if foundI != -1:
		stack.remove_at(foundI)
		
	var pair = {}
	pair["key"] = key
	pair["value"] = value
	
	stack.push_back(pair)

func delete(key: Variant):
	var foundI = stack.find_custom(func(el): el["key"] == key)
	if foundI != -1:
		stack.remove_at(foundI)

func _notification(what: int) -> void:
	if what == NOTIFICATION_WM_GO_BACK_REQUEST:
		if stack.size() > 0:
			stack.pop_back()["value"].call()
		else:
			get_tree().quit()
