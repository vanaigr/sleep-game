extends Control

var swipeThreshold = 150

var touchStart = Vector2.ZERO
var isTouching = false

@export var screenPositions: Array[Node2D]
@export var camera: Node2D
var screenI = 0

var animationTween: Tween

func _ready() -> void:
	camera.position = screenPositions[screenI].position

func goToScreen(index: int) -> void:
	screenI = clamp(index, 0, screenPositions.size() - 1)

	if animationTween != null:
		animationTween.kill()
		
	animationTween = create_tween()
	animationTween.tween_property(
		camera,
		"position",
		screenPositions[screenI].position,
		0.25
	).set_trans(Tween.TRANS_QUAD).set_ease(Tween.EASE_OUT)

func _input(event: InputEvent) -> void:
	if event is InputEventScreenTouch:
		if event.pressed:
			isTouching = true
			touchStart = event.position
			var a = event.index
		else:
			isTouching = false
			var swipeDelta = event.position - touchStart

			if abs(swipeDelta.y) > swipeThreshold:
				if swipeDelta.y < 0:
					goToScreen(screenI + 1)
				else:
					goToScreen(screenI - 1)
	elif event is InputEventScreenDrag and is_touching:
		touch_current = event.position
