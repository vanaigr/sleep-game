package com.example.sleepgame

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.sqlite.transaction
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

///------------------------------------------///
/// NO. RETURNS. IN. TRANSACTIONS.           ///
/// RETURNS. ROLLS. BACK. FING. TRANSACTION. ///
/// 🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡🤡 ///
///------------------------------------------///

class Database {
    val db: SQLiteDatabase

    constructor(context: Context) {
        db = SQLiteDatabase.openOrCreateDatabase(
            File(context.filesDir, "sqlite.db").absolutePath,
            null,
        )
        migrate()
    }

    private fun migrate() {
        db.transaction {
            if (db.version == 0) {
                // Type is period_begin, fall_asleep, interruption, wake_up, period_end
                db.execSQL(
                    """
                create table sleep_records(
                    id integer primary key autoincrement,
                    period_id integer not null,
                    type text not null,
                    recorded_time ZonedDateTime not null
                )
            """.trimIndent()
                )
                db.version = 1
            }
            if(db.version == 1) {
                // 0 - dismissed, 1 - no sleep, 2 - terrible, 3 - not good, 4 - not ideal, 5 - ideal
                db.execSQL(
                    """
                create table sleep_quality(
                    period_id integer primary key,
                    quality integer not null
                )
            """.trimIndent()
                )
                db.version = 2
            }
            if(db.version == 2) {
                db.execSQL("create table sleep_data_version(version integer not null)")
                db.execSQL("insert into sleep_data_version(version) values (1)")
                db.version = 3
            }
            if(db.version == 3) {
                db.execSQL("alter table sleep_records add column time_to_fall_asleep_minutes integer not null default 15")
                db.execSQL("alter table sleep_records add column minimum_sleep_duration_minutes integer not null default 10")
                db.version = 4
            }
            /*
            if(db.version == 5) {
                db.execSQL("drop table complete_sleep_periods")
                db.version = 4
            }
             */
            if(db.version == 4) {
                db.execSQL("""
                    create table complete_sleep_periods(
                        period_id integer primary key,
                        fall_asleep_time text,
                        wake_up_time text,
                        sleep_duration text not null,
                        duration_before_falling_asleep text,
                        interruption_count integer not null,
                        sleep_balance_duration text not null
                    )
                """.trimIndent())

                val periodIds = db.rawQuery("select distinct period_id from sleep_records order by period_id", arrayOf()).use {
                    val result = mutableListOf<Int>()
                    while(it.moveToNext()) result.add(it.getInt(0))
                    result
                }
                for(periodId in periodIds) updateSavedSleepPeriod(periodId)

                db.version = 5
            }
        }
    }

    fun updateSavedSleepPeriod(periodId: Int) {
        val records = getAllRecordsForPeriod(periodId)
        records.sortWith { a, b -> a.recordedTime.compareTo(b.recordedTime) }
        val data = calculateSleepPeriodData(records)

        val lastSleepBalance = db.rawQuery(
            "select sleep_balance_duration from complete_sleep_periods where period_id < ? order by period_id desc limit 1",
            arrayOf("" + periodId)
        ).use {
            if(it.moveToNext()) decodeDuration(it.getString(0))
            else Duration.ZERO
        }

        // NOTE: expects that a person records every day, even if they haven't slept
        val sleepBalance = lastSleepBalance + data.totalSleepDuration - Duration.ofHours(8)
        db.insertWithOnConflict(
            "complete_sleep_periods",
            null,
            ContentValues().apply {
                put("period_id", periodId)
                put("fall_asleep_time", data.fallAsleep?.let { encodeInstant(it) })
                put("wake_up_time", data.wakeUp?.let { encodeInstant(it) })
                put("sleep_duration", encodeDuration(data.totalSleepDuration))
                put("duration_before_falling_asleep", encodeDuration(data.durationBeforeFallingAsleep))
                put("interruption_count", data.interruptionCount)
                put("sleep_balance_duration", encodeDuration(sleepBalance))
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun updateSavedSleepPeriodsFrom(periodId: Int) {
        val periodIds = db.rawQuery(
            "select period_id from complete_sleep_periods where period_id > ? order by period_id",
            arrayOf("" + periodId)
        ).use {
            val result = mutableListOf<Int>()
            while(it.moveToNext()) result.add(it.getInt(0))
            result
        }
        updateSavedSleepPeriod(periodId)
        for(periodId in periodIds) updateSavedSleepPeriod(periodId)
    }

    fun getSleepDataVersion(): Long {
        return db.rawQuery("select version from sleep_data_version", arrayOf()).use {
            it.moveToNext()
            it.getLong(0)
        }
    }

    fun bumpSleepDataVersion() {
        db.execSQL("update sleep_data_version set version = version + 1")
    }

    data class SleepRecordInput(
        val recordedAt: ZonedDateTime,
        val timeToFallAsleepMinutes: Long,
        val minimumSleepDurationMinutes: Long
    )

    fun startSleepPeriod(input: SleepRecordInput): Boolean {
        val time = encodeZonedDateTime(input.recordedAt)

        return db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            Log.d(TAG, "Active row $curPeriod")

            if(curPeriod.ended) {
                Log.d(TAG, "Starting new period")

                val id = curPeriod.id + 1
                db.execSQL(
                    "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                    arrayOf(id, "period_begin", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
                )
                bumpSleepDataVersion()
                updateSavedSleepPeriodsFrom(id)

                true
            }
            else {
                Log.d(TAG, "Already started")
                false
            }
        }
    }

    fun endSleepPeriod(input: SleepRecordInput): Boolean {
        val time = input.recordedAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        return db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            Log.d(TAG, "Active row $curPeriod")

            if(!curPeriod.ended) {
                Log.d(TAG, "Ending current period")

                db.execSQL(
                    "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                    arrayOf(curPeriod.id, "period_end", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
                )
                updateSavedSleepPeriodsFrom(curPeriod.id)
                bumpSleepDataVersion()

                true
            }
            else {
                Log.d(TAG, "Already ended")
                false
            }
        }
    }
    fun recordWakeUp(input: SleepRecordInput) {
        Log.d(TAG, "recordFallAsleep")

        val time = input.recordedAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            if(curPeriod.ended) {
                Log.w(TAG, "Current period has ended and this action should not have been accessible")
                // fallthrough
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                arrayOf(curPeriod.id, "wake_up", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
            )
            updateSavedSleepPeriodsFrom(curPeriod.id)
            bumpSleepDataVersion()
        }
    }

    fun recordSleepInterruption(input: SleepRecordInput) {
        Log.d(TAG, "recordSleepInterruption")

        val time = input.recordedAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            if(curPeriod.ended) {
                Log.w(TAG, "Current period has ended and this action should not have been accessible")
                // fallthrough
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                arrayOf(curPeriod.id, "interruption", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
            )
            updateSavedSleepPeriodsFrom(curPeriod.id)
            bumpSleepDataVersion()
        }
    }

    fun recordFallAsleep(input: SleepRecordInput) {
        Log.d(TAG, "recordFallAsleep")

        val time = input.recordedAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            if(curPeriod.ended) {
                Log.w(TAG, "Current period has ended and this action should not have been accessible")
                // fallthrough
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                arrayOf(curPeriod.id, "fall_asleep", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
            )
            updateSavedSleepPeriodsFrom(curPeriod.id)
            bumpSleepDataVersion()
        }
    }

    fun setSleepQuality(periodId: Int, quality: Int) {
        db.execSQL(
            "insert or replace into sleep_quality(period_id, quality) values(?, ?)",
            arrayOf(periodId, quality),
        )
        bumpSleepDataVersion()
        Log.d(TAG, "Inserted quality record for $periodId: $quality")
    }

    fun getAllRecordsForPeriod(periodId: Int): MutableList<SleepRecord> {
        return db.rawQuery(
            "select type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes from sleep_records where period_id = ?",
            arrayOf("" + periodId)
        ).use {
            val result = mutableListOf<SleepRecord>()
            while(it.moveToNext()) {
                result.add(SleepRecord(
                    periodId,
                    it.getString(0),
                    ZonedDateTime.parse(it.getString(1)),
                    Duration.ofMinutes(it.getLong(2)),
                    Duration.ofMinutes(it.getLong(3)),
                ))
            }
            result
        }
    }

    fun getQualityForPeriod(periodId: Int): Int? {
        return db.rawQuery("select quality from sleep_quality where period_id = ?", arrayOf()).use {
            if(it.moveToNext()) it.getInt(0)
            else null
        }
    }

    fun getCurrentSleepPeriod(): SleepPeriod {
        val curPeriodId = db.rawQuery("select max(period_id) from sleep_records", arrayOf()).use {
            it.moveToNext()
            it.getIntOrNull(0)
        }
        if(curPeriodId == null) {
            return SleepPeriod(0, true)
        }

        val curPeriodEnded = db.rawQuery(
            "select count(*) from sleep_records where period_id = ? and type in (?, ?)",
            arrayOf("" + curPeriodId, "wake_up", "period_end")
        ).use {
            it.moveToNext()
            it.getLong(0) != 0L
        }
        return SleepPeriod(curPeriodId, curPeriodEnded)
    }

    companion object {
        val TAG = "Database"
    }
}

data class SleepRecord(val periodId: Int, val type: String, val recordedTime: ZonedDateTime, val timeToFallAsleep: Duration, val minimumSleepDuration: Duration)

data class SleepPeriod(val id: Int, val ended: Boolean)

fun encodeInstant(it: Instant): String {
    return it.toString()
}
fun encodeZonedDateTime(it: ZonedDateTime): String {
    return it.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
}
fun encodeDuration(it: Duration): String {
    return it.toString()
}
fun decodeInstant(it: String): Instant {
    return Instant.parse(it)
}
fun decodeZonedDateTime(it: String): ZonedDateTime {
    return ZonedDateTime.parse(it)
}
fun decodeDuration(it: String): Duration {
    return Duration.parse(it)
}

/*
.use { db ->
db.rawQuery("SELECT id FROM test", null).use { c ->
    var results = ""
    while (c.moveToNext()) {
        val id = c.getLong(0)
        results += "$id"
    }
    Log.d(TAG, results)
}
}
*/