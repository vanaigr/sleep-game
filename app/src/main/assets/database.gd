extends Node

var bridgePlugin := Engine.get_singleton("BridgePlugin")

func _ready():
	pass
   
func query(sql: String, args: Array[String]) -> Array[Dictionary]:
	var result: Array[Dictionary] = []
	result.assign(bridgePlugin.query(sql, args))
	return result
