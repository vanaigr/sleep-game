
class_name AndroidLsq2VelocityTracker
# Port of Android VelocityTracker with default planar strategy (unweighted LSQ2 for X/Y).
# https://android.googlesource.com/platform/frameworks/native/+/4f463a6b1de9198963dc6aff74154a504ba3f8f6/libs/input/VelocityTracker.cpp

class AccumulatingVelocityTrackerStrategy:
	var horizonNanos: int
	# maintainHorizonDuringAdd = true
	var movements: Dictionary[int, Array] # pointerId -> ring buffer
