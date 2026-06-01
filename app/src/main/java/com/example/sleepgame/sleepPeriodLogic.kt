package com.example.sleepgame

import android.util.Log
import java.time.Duration
import java.time.Instant
import kotlin.math.max

data class SavedSleepPeriodData(
    val periodId: Int,
    val quality: Int,
    val fallAsleep: Instant?,
    val wakeUp: Instant?,
    val totalSleepDuration: Duration,
    val durationBeforeFallingAsleep: Duration,
    val interruptionCount: Int,
    val sleepBalance: Duration,
)

data class CalculatedSleepPeriodData(
    val fallAsleep: Instant?,
    val wakeUp: Instant?,
    val totalSleepDuration: Duration,
    val durationBeforeFallingAsleep: Duration,
    val interruptionCount: Int,
    val nonSleepRanges: List<NonSleepRange>,
)

data class NonSleepRange(var begin: Instant, var lastBegin: Instant, var end: Instant)

fun calculateSleepPeriodData(records: Iterable<SleepRecord>): CalculatedSleepPeriodData {
    val nonSleepRanges = mutableListOf<NonSleepRange>()
    var totalSleepDuration = Duration.ZERO

    var initialFallAsleepPhase = true // Only "period_begin" or "fall_asleep" were seen before

    val addNonSleepPeriod = fun(begin: Instant, end: Instant, minimumSleepDuration: Duration) {
        if(nonSleepRanges.isEmpty()) {
            nonSleepRanges.add(NonSleepRange(begin, begin, end))
            return
        }

        val lastRange = nonSleepRanges.last()
        val sleepTime = Duration.between(lastRange.end, begin)
        if(sleepTime >= minimumSleepDuration) {
            totalSleepDuration += sleepTime
            nonSleepRanges.add(NonSleepRange(begin, begin, end))
        }
        else {
            lastRange.lastBegin = begin
            lastRange.end = end
        }
    }

    for(record in records) {
        when(record.type) {
            "period_begin", "fall_asleep" -> {
                val time = record.recordedTime.toInstant()
                if(initialFallAsleepPhase) {
                    if(nonSleepRanges.isEmpty()) {
                        nonSleepRanges.add(NonSleepRange(time, time, time + record.timeToFallAsleep))
                    }
                    else {
                        val lastRange = nonSleepRanges.last()
                        lastRange.lastBegin = time
                        lastRange.end = time
                    }
                }
                else {
                    addNonSleepPeriod(time, time + record.timeToFallAsleep, record.minimumSleepDuration)
                }
            }
            "interruption" -> {
                initialFallAsleepPhase = false
                val time = record.recordedTime.toInstant()
                addNonSleepPeriod(time, time + record.timeToFallAsleep, record.minimumSleepDuration)
            }
            "wake_up", "period_end" -> {
                initialFallAsleepPhase = false
                val time = record.recordedTime.toInstant()
                addNonSleepPeriod(time, time, record.minimumSleepDuration)

                if(nonSleepRanges.size >= 2) {
                    return CalculatedSleepPeriodData(
                        nonSleepRanges[0].end,
                        nonSleepRanges.last().begin,
                        totalSleepDuration,
                        Duration.between(nonSleepRanges[0].begin, nonSleepRanges[0].end),
                        nonSleepRanges.size - 2,
                        nonSleepRanges,
                    )
                }

                break
            }
            else -> {
                Log.d("calculateSleepPeriodNonSleepRanges", "Unknown record type $record")
            }
        }
    }

    return CalculatedSleepPeriodData(
        null,
        null,
        totalSleepDuration,
        if(nonSleepRanges.isEmpty()) Duration.ZERO else Duration.between(nonSleepRanges[0].begin, nonSleepRanges[0].end),
        max(0, nonSleepRanges.size - 2),
        nonSleepRanges,
    )
}