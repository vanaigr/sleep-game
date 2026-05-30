extends Node

@export var screens: Array[Node]
@export var anchorScreens: Array[int]
@export var anchorEdges: Array[Edge]
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
	
	var screenSize = get_tree().root.content_scale_size
	for i in range(anchorScreens.size()):
		var otherScreenI = anchorScreens[i]
		var edge = anchorEdges[i]
			
		var p = screens[otherScreenI].global_position
		if edge == Edge.top:
			p += Vector2(0, screenSize.y)
		elif edge == Edge.bottom:
			p -= Vector2(0, screenSize.y)
		elif edge == Edge.left:
			p -= Vector2(screenSize.x, 0)
		elif edge == Edge.right:
			p += Vector2(screenSize.x, 0)
			
		screens[i].global_position = p

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
	
enum Edge { onTop, top, right, bottom, left }
