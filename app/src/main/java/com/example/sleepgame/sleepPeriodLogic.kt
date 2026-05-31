package com.example.sleepgame

import android.util.Log
import java.time.Duration
import java.time.Instant

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

data class NonSleepRange(var begin: Instant, var end: Instant)

fun calculateSleepPeriodData(records: Iterable<SleepRecord>): CalculatedSleepPeriodData {
    val nonSleepRanges = mutableListOf<NonSleepRange>()
    var totalSleepDuration = Duration.ZERO

    var initialFallAsleepPhase = true // Only "period_begin" or "fall_asleep" were seen before

    val accumulateSleepUntil = fun(until: Instant, timeToFallAsleep: Duration, minimumSleepDuration: Duration) {
        if(nonSleepRanges.isEmpty()) {
            nonSleepRanges.add(NonSleepRange(until, until + timeToFallAsleep))
            return
        }

        val lastPeriod = nonSleepRanges.last()
        val sleepTime = Duration.between(lastPeriod.end, until)
        if(sleepTime >= minimumSleepDuration) {
            totalSleepDuration += sleepTime
            nonSleepRanges.add(NonSleepRange(until, until + timeToFallAsleep))
        }
        else {
            lastPeriod.end = until
        }
    }

    for(record in records) {
        when(record.type) {
            "period_begin", "fall_asleep" -> {
                val time = record.recordedTime.toInstant()
                if(initialFallAsleepPhase) {
                    if(nonSleepRanges.isEmpty()) {
                        nonSleepRanges.add(NonSleepRange(time, time + record.timeToFallAsleep))
                    }
                    else {
                        nonSleepRanges.last().end = time
                    }
                }
                else {
                    accumulateSleepUntil(time, record.timeToFallAsleep, record.minimumSleepDuration)
                }
            }
            "interruption" -> {
                initialFallAsleepPhase = false
                val time = record.recordedTime.toInstant()
                accumulateSleepUntil(time, record.timeToFallAsleep, record.minimumSleepDuration)
            }
            "wake_up", "period_end" -> {
                initialFallAsleepPhase = false
                val time = record.recordedTime.toInstant()
                accumulateSleepUntil(time, record.timeToFallAsleep, record.minimumSleepDuration)
                break
            }
            else -> {
                Log.d("calculateSleepPeriodNonSleepRanges", "Unknown record type $record")
            }
        }
    }

    if(nonSleepRanges.size < 2) {
        return CalculatedSleepPeriodData(
            null, null,
            Duration.ZERO, Duration.ZERO,
            0,
            listOf()
        )
    }

    return CalculatedSleepPeriodData(
        nonSleepRanges[0].end,
        nonSleepRanges.last().begin,
        totalSleepDuration,
        Duration.between(nonSleepRanges[0].begin, nonSleepRanges[0].end),
        nonSleepRanges.size - 2,
        nonSleepRanges,
    )
}