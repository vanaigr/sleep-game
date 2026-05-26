extends Control
class_name SleepGraph

const padding = 8
const hourFontSize = 28

var bridgePlugin := Engine.get_singleton("BridgePlugin")
var periodId: int = 0
var font: Font
var maxWidth: float = 0

# Called when the node enters the scene tree for the first time.
func _ready() -> void:
	font = ThemeDB.fallback_font
	for hour in range(24):
		var width = font.get_string_size(str(hour), HORIZONTAL_ALIGNMENT_CENTER, -1, hourFontSize).x
		maxWidth = max(maxWidth, width)

func _draw():
	var data = bridgePlugin.getSleepPeriodGraph(periodId, size.x, maxWidth)
	
	var sleepTimeY = size.y - padding * 0.5
	var tickY = sleepTimeY - hourFontSize - padding * 0.5
	var graphBottom = tickY - hourFontSize - padding * 0.5
	
	draw_line(
		Vector2((padding + maxWidth) * 0.5, graphBottom + 2), 
		Vector2(size.x - (padding + maxWidth) * 0.5, graphBottom + 2), 
		Color.GRAY, 
		4
	)
	for tickI in range(data["tick_count"]):
		var position = data["tick_" + str(tickI) + "_position"]
		draw_line(Vector2(position, padding * 0.5), Vector2(position, graphBottom + 4), Color.GRAY, 4)

	for tickI in range(data["tick_count"]):
		var position = data["tick_" + str(tickI) + "_position"]
		var label = data["tick_" + str(tickI) + "_label"]
		var textSize = font.get_string_size(label, HORIZONTAL_ALIGNMENT_LEFT, -1, hourFontSize)
		draw_string(
			font, 
			Vector2(position - textSize.x * 0.5, tickY), 
			label, 
			HORIZONTAL_ALIGNMENT_LEFT,
			-1,
			hourFontSize
		)
	
	for polygonI in range(data["non_sleep_polygon_count"]):
		var pointCount = data["non_sleep_polygon_" + str(polygonI) + "_point_count"]
		var points = PackedVector2Array()
		for pointI in range(pointCount):
			var x = data["non_sleep_point_" + str(polygonI) + "_" + str(pointI) + "_x"]
			var y = data["non_sleep_point_" + str(polygonI) + "_" + str(pointI) + "_y"]
			points.append(Vector2(x, lerp(graphBottom, padding * 0.5, y)))
		
		draw_colored_polygon(points, Color.ORANGE)
		
	if data["fall_asleep_position"] != null:
		var x = data["fall_asleep_position"]
		var t = data["fall_asleep_label"]
		draw_line(Vector2(x, padding * 0.5), Vector2(x, graphBottom), Color.BLUE, 4)
		var textSize = font.get_string_size(t, HORIZONTAL_ALIGNMENT_LEFT, -1, hourFontSize).x
		draw_string(
			font,
			Vector2(max(padding * 0.5, x - textSize * 0.5), sleepTimeY),
			t,
			HORIZONTAL_ALIGNMENT_LEFT, 
			-1,
			hourFontSize,
			Color.BLUE
		)
	if data["wake_up_position"] != null:
		var x = data["wake_up_position"]
		var t = data["wake_up_label"]
		draw_line(Vector2(x, padding * 0.5), Vector2(x, graphBottom), Color.RED, 4)
		var textSize = font.get_string_size(t, HORIZONTAL_ALIGNMENT_LEFT, -1, hourFontSize).x
		draw_string(
			font,
			Vector2(min(size.x - padding * 0.5, x + textSize * 0.5) - textSize, sleepTimeY),
			t,
			HORIZONTAL_ALIGNMENT_LEFT, 
			-1,
			hourFontSize,
			Color.RED
		)
		
		
		
		
		
		
		
		
		
		
		
