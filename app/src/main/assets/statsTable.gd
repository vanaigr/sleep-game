extends Label

var bridgePlugin := Engine.get_singleton("BridgePlugin")

func _process(delta: float) -> void:
	var stats = bridgePlugin.getStats()
	if stats == null:
		text = 'Нет данных'
	else:
		text = '\n'.join([
			'Время пробуждения: ' + stats['end_time'], 
			'Время засыпания: ' + stats['begin_time'],
			'Качество: ' + stats['quality'],
			'Продолжительность: ' + stats['duration'],
		])
	
func find_datetime_end(s: String) -> int:
	var separatorI = indexOf(s, "T")
		
	var baseDtEnd = separatorI + 3 
	if substring(s, baseDtEnd, baseDtEnd + 1) == ":": #minutes exist?
		baseDtEnd += 3
	if substring(s, baseDtEnd, baseDtEnd + 1) == ":": #seconds exist?
		baseDtEnd += 3
	
	return baseDtEnd
	
func datetime_to_iso_msec(s: String) -> int:
	var baseDtEnd = find_datetime_end(s)
	var end = baseDtEnd
	
	var milliseconds = 0
	if substring(s, end, end + 1) == ".": #fractional seconds exist?
		end += 1
		while end < s.length():
			if substring(s, end, end + 1) in "0123456789":
				end += 1
			else:
				break
		
		if end >= baseDtEnd + 4:
			milliseconds = int(substring(s, baseDtEnd + 1, baseDtEnd + 4))
	
	var timezoneOffset = 0
	var offsetSign = substring(s, end, end + 1)
	if offsetSign in "+-":
		var hourStr = substring(s, end + 1, end + 3)
		var minuteStr = substring(s, end + 4, end + 6) \
			if substring(s, end + 3, end + 4) == ':' \
			else substring(s, end + 3, end + 5)
		var hour = int(hourStr) if hourStr.is_valid_int() else 0
		var minute = int(minuteStr) if minuteStr.is_valid_int() else 0
		var sign = 1 if offsetSign == '+' else -1
		timezoneOffset = sign * (hour * 60 + minute) * 60 * 1000
	
	var result = Time.get_unix_time_from_datetime_string(substring(s, 0, end)) * 1000 \
		+ milliseconds \
		- timezoneOffset
		
	return result
	
func indexOf(s: String, what: String, begin: int = 0) -> int:
	var result = s.find(what, begin)
	if result == -1:
		return s.length()
	return result
	
func substring(s: String, begin: int, end: int = s.length()) -> String:
	begin = clamp(begin, 0, s.length())
	end = clamp(end, 0, s.length())
	if begin >= end:
		return ""
	return s.substr(begin, end - begin)
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
