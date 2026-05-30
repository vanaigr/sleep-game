@tool
extends Node

const safeSize = Vector2(1080, 1920)
const minAspect = 9.0 / 21.0
const maxAspect = 3.0 / 4.0

func coverSize(size: Vector2, coverAspectRatio: float) -> Vector2:
	var aspectRatio = size.aspect()
	if aspectRatio > coverAspectRatio:
		return Vector2(size.x, size.x / coverAspectRatio)
	else:
		return Vector2(size.y * coverAspectRatio, size.y)
