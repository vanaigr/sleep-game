extends Node

@export var timeToFallAsleepEl: LineEdit
@export var timeToFallAsleepAfterInterruptionEl: LineEdit
@export var normalSleepDurationEl: LineEdit
@export var sleepButtonsOrderEl: LineEdit
@export var sleepButtonsSizeEl: LineEdit
@export var sleepNotificationPeriodEl: LineEdit

@export var showSleepNotificationEl: CheckButton
@export var sleepNotificationSoundEl: CheckButton
@export var sleepNotificationVibrationEl: CheckButton

var showSleepNotification: bool
var sleepNotificationSound: bool
var sleepNotificationVibration: bool

var bridgePlugin := Engine.get_singleton("BridgePlugin")

func _ready() -> void:
	close()
	
	var settings = JSON.parse_string(bridgePlugin.getSettings())
	
	timeToFallAsleepEl.text = settings['timeToFallAsleep']
	timeToFallAsleepAfterInterruptionEl.text = settings['timeToFallAsleepAfterInterruption']
	normalSleepDurationEl.text = settings['normalSleepDuration']
	sleepButtonsOrderEl.text = ''.join(settings['sleepButtonsOrder'].map(func(el): return str(int(el))))
	sleepButtonsSizeEl.text = ' '.join(settings['sleepButtonsSize'].map(func(el): return str(int(el)) + '%'))
	sleepNotificationPeriodEl.text = settings['sleepNotificationPeriod']
	
	showSleepNotification = settings['showSleepNotification']
	sleepNotificationSound = settings['sleepNotificationSound']
	sleepNotificationVibration = settings['sleepNotificationVibration']
	
	timeToFallAsleepEl.text_changed.connect(func(text: String):
		bridgePlugin.setSettings(JSON.stringify({ ['timeToFallAsleep']: text }))
	)
	timeToFallAsleepAfterInterruptionEl.text_changed.connect(func(text: String):
		bridgePlugin.setSettings(JSON.stringify({ ['timeToFallAsleepAfterInterruption']: text }))
	)
	normalSleepDurationEl.text_changed.connect(func(text: String):
		bridgePlugin.setSettings(JSON.stringify({ ['normalSleepDuration']: text }))
	)
	sleepButtonsOrderEl.text_changed.connect(func(text: String):
		bridgePlugin.setSettings(JSON.stringify({ ['sleepButtonsOrder']: text }))
	)
	sleepButtonsSizeEl.text_changed.connect(func(text: String):
		bridgePlugin.setSettings(JSON.stringify({ ['sleepButtonsSize']: text }))
	)
	sleepNotificationPeriodEl.text_changed.connect(func(text: String):
		bridgePlugin.setSettings(JSON.stringify({ ['sleepNotificationPeriod']: text }))
	)

func sleep_balance_reset_pressed() -> void:
	bridgePlugin.resetSleepBalance()

func toggleShowSleepNotification() -> void:
	showSleepNotification = not showSleepNotification
	bridgePlugin.setSettings(JSON.stringify({ ['showSleepNotification']: showSleepNotification }))
	showSleepNotificationEl.button_pressed = showSleepNotification

func toggleSleepNotificationSound() -> void:
	sleepNotificationSound = not sleepNotificationSound
	bridgePlugin.setSettings(JSON.stringify({ ['sleepNotificationSound']: sleepNotificationSound }))
	sleepNotificationSoundEl.button_pressed = sleepNotificationSound

func toggleSleepNotificationVibration() -> void:
	sleepNotificationVibration = not sleepNotificationVibration
	bridgePlugin.setSettings(JSON.stringify({ ['sleepNotificationVibration']: sleepNotificationVibration }))
	sleepNotificationVibrationEl.button_pressed = sleepNotificationVibration


func _on_settings_pressed() -> void:
	open()
	

func open():
	var root = get_parent()
	ScreenStack.push(root, close)
	set_process_recursive(root, true)
	root.show()

func close() -> void:
	var root = get_parent()
	ScreenStack.delete(root)
	set_process_recursive(root, false)
	root.hide()
	
func set_process_recursive(node: Node, enabled: bool) -> void:
	node.set_process(enabled)

	for child in node.get_children():
		set_process_recursive(child, enabled)
