extends Node

@export var screens: Array[Node]
@export var screenTransitionControls: Array[Array]
@export var camera: Camera2D
var currentScreenI: int
var transitionTween: Tween = Tween.new()

# Called when the node enters the scene tree for the first time.
func _ready() -> void:
	currentScreenI = 0
	camera.position = screens[currentScreenI].position

# Called every frame. 'delta' is the elapsed time since the previous frame.
func _process(delta: float) -> void:
	var animating = transitionTween.is_valid()
	
	for i in range(screenTransitionControls.size()):
		var active = false
		if not animating and i == currentScreenI:
			active = true

		for controlButNotActually in screenTransitionControls[i]:
			var control = get_node(controlButNotActually)
			if "visible" in control:
				control.visible = active
			if "mouse_filter" in control:
				control.mouse_filter = Control.MOUSE_FILTER_STOP if active else Control.MOUSE_FILTER_IGNORE
			if "disabled" in control:
				control.disabled = not active

func toCellar():
	animateTo(1)

func toBedroom():
	animateTo(0)
	
func animateTo(screen: int):
	if transitionTween == null and currentScreenI == screen:
		return
		
	if transitionTween.is_valid():
		transitionTween.kill()

	currentScreenI = screen
	
	transitionTween = create_tween()
	transitionTween \
		.tween_property(camera, "global_position", screens[currentScreenI].global_position, 0.25) \
		.set_trans(Tween.TRANS_CUBIC)
	
	
