extends Node

var camera: Camera2D

func _ready() -> void:
	camera = get_parent()

func _process(delta: float) -> void:
	var windowSize := DisplayServer.window_get_size()
	var aspect = clamp(windowSize.aspect(), CameraSize.minAspect, CameraSize.maxAspect)
	var screenSize = CameraSize.coverSize(CameraSize.safeSize, aspect)
	get_tree().root.content_scale_size = screenSize
