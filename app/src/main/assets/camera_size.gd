@tool
extends Node

const safeSize = Vector2(387, 688)
const minAspect = 9.0 / 21.0
const maxAspect = 3.0 / 4.0

var screenSize = safeSize
signal screenSizeChanged()

func coverSize(size: Vector2, coverAspectRatio: float) -> Vector2:
	var aspectRatio = size.aspect()
	if aspectRatio > coverAspectRatio:
		return Vector2(size.x, size.x / coverAspectRatio)
	else:
		return Vector2(size.y * coverAspectRatio, size.y)

func _process(delta: float):
	var newScreenSize = screenSize
	if not Engine.is_editor_hint():
		var windowSize := DisplayServer.window_get_size()
		var aspect = clamp(windowSize.aspect(), CameraSize.minAspect, CameraSize.maxAspect)
		newScreenSize = CameraSize.coverSize(CameraSize.safeSize, aspect)
		
	if newScreenSize != screenSize:
		screenSize = newScreenSize
		get_tree().root.content_scale_size = screenSize
		screenSizeChanged.emit()
