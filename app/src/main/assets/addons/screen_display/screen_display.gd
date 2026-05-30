@tool
class_name ScreenDisplay
extends EditorPlugin

var viewport: SubViewport
var drawPanel: DrawPanel

func _enter_tree() -> void:
	print('Plugin loaded')
	drawPanel = DrawPanel.new()
	drawPanel.plugin = self
	
	viewport = get_editor_interface().get_editor_viewport_2d() #get_editor_interface().get_editor_main_screen().get_child(0).get_child(1).get_child(0).get_child(0).get_child(0)
	viewport.add_child(drawPanel)
	
	update_overlays()

func _exit_tree() -> void:
	drawPanel.queue_free()

class DrawPanel extends Node2D:
	const gizmoGroup = 'Screens'
	var maxSize: Vector2
	var plugin: ScreenDisplay
	
	func _ready() -> void:
		maxSize = CameraSize.safeSize \
			.max(CameraSize.coverSize(CameraSize.safeSize, CameraSize.minAspect)) \
			.max(CameraSize.coverSize(CameraSize.safeSize, CameraSize.maxAspect))
		queue_redraw()
		
	func _process(delta: float):
		queue_redraw()
	
	func _draw():
		var size = get_parent().size
		
		var scene_root := plugin.get_editor_interface().get_edited_scene_root()
		if scene_root == null:
			return
			
		var scale = plugin.get_editor_interface().get_editor_viewport_2d().get_screen_transform().get_scale().x

		for node in plugin.get_tree().get_nodes_in_group(gizmoGroup):
			if not _is_node_in_edited_scene(node, scene_root):
				continue
			if not node is Node2D:
				continue
			if not node.is_inside_tree():
				continue

			var center = node.global_position
			var width = 2 / scale
			
			draw_rect(Rect2(center - CameraSize.safeSize * 0.5, CameraSize.safeSize), Color(0, 1, 0, 0.3), false, width)
			draw_rect(Rect2(center - maxSize * 0.5, maxSize), Color(0, 0, 1, 0.3), false, width)
			
	func _is_node_in_edited_scene(node: Node, scene_root: Node) -> bool:
		return node == scene_root or scene_root.is_ancestor_of(node)
