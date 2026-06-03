extends Node

@export var screens: Dictionary[String, Node]
@export var camera: Camera2D

var currentScreen = 'bedroom'
var transitionTween: Tween = Tween.new()

# Called when the node enters the scene tree for the first time.
func _ready() -> void:
	currentScreen = 0
	camera.position = screens[currentScreen].position

# Called every frame. 'delta' is the elapsed time since the previous frame.
func _process(delta: float) -> void:
	var animating = transitionTween.is_valid()
	
	screens['cellar'].global_position = screens['bedroom'].global_position + Vector2(0, CameraSize.screenSize.y)
	
	var cameraPos = camera.global_position
	screens['detailedStatsWindow'].global_position = cameraPos
	screens['settingsWindow'].global_position = cameraPos

func toCellar():
	animateTo('cellar')

func toBedroom():
	animateTo('bedroom')
	
func animateTo(screen: String):
	if transitionTween == null and currentScreen == screen:
		return
		
	if transitionTween.is_valid():
		transitionTween.kill()

	currentScreen = screen
	
	transitionTween = create_tween()
	transitionTween \
		.tween_property(camera, "global_position", screens[currentScreen].global_position, 0.25) \
		.set_trans(Tween.TRANS_CUBIC)
