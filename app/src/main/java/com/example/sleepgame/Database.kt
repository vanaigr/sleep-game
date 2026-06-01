package com.example.sleepgame

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
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
    private val db: SQLiteDatabase

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
                // Oops... should've been `sleep_periods` since they may actually be incomplete
                // NOTE: sleep_balance_duration is debt, not balance
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

                updateAllSleepPeriods()

                db.version = 5
            }
            if(db.version == 5) {
                db.execSQL("alter table complete_sleep_periods add column deleted integer not null default 0")
                db.version = 6
            }
            if(db.version == 6) {
                db.execSQL("update sleep_records set minimum_sleep_duration_minutes = 15")
                db.execSQL("update sleep_records set time_to_fall_asleep_minutes = 10 where \"type\" = 'interruption'")
                updateAllSleepPeriods()
                db.version = 7
            }
            if(db.version == 7) {
                db.execSQL("create table should_reset_sleep_balance(id integer primary key)")
                db.execSQL("alter table complete_sleep_periods add column reset_sleep_balance integer not null default 0")
                updateAllSleepPeriods()
                db.version = 8
            }
        }
    }

    fun updateSavedSleepPeriod(periodId: Int, creatingLatest: Boolean) {
        val records = getAllRecordsForPeriod(periodId)
        records.sortWith { a, b -> a.recordedTime.compareTo(b.recordedTime) }
        val data = calculateSleepPeriodData(records)

        val lastSleepBalance = db.rawQuery(
            "select sleep_balance_duration from complete_sleep_periods where deleted = 0 and period_id < ? order by period_id desc limit 1",
            arrayOf("" + periodId)
        ).use {
            if(it.moveToNext()) decodeDuration(it.getString(0))
            else Duration.ZERO
        }

        val existingResetSleepBalance = db.rawQuery("select reset_sleep_balance from complete_sleep_periods where period_id = ?", arrayOf("" + periodId)).use {
            if(it.moveToNext()) it.getInt(0) != 0
            else null
        }
        val resetSleepBalance = (fun(): Boolean {
            if (existingResetSleepBalance != null) return existingResetSleepBalance
            if (!creatingLatest) return false

            val shouldResetLatest = db.rawQuery("select 1 from should_reset_sleep_balance", arrayOf()).use {
                it.moveToNext()
            }
            if (!shouldResetLatest) return false

            db.execSQL("delete from should_reset_sleep_balance")
            return true
        })()

        // NOTE: expects that a person records every day, even if they haven't slept
        val sleepBalance = (Duration.ofHours(8) - data.totalSleepDuration) + (if(resetSleepBalance) Duration.ZERO else lastSleepBalance)
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
                put("deleted", 0)
                put("reset_sleep_balance", if(resetSleepBalance) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun updateSavedSleepPeriodsAfter(periodId: Int) {
        val periodIds = db.rawQuery(
            "select period_id, reset_sleep_balance from complete_sleep_periods where deleted = 0 and period_id > ? order by period_id",
            arrayOf("" + periodId)
        ).use {
            val result = mutableListOf<Pair<Int, Boolean>>()
            while(it.moveToNext()) result.add(Pair(it.getInt(0), it.getInt(1) != 0))
            result
        }
        for((periodId, resetSleepBalance) in periodIds) {
            if(resetSleepBalance) break
            updateSavedSleepPeriod(periodId, false)
        }
    }

    fun updateAllSleepPeriods() {
        val periodIds = db.rawQuery("select distinct period_id from sleep_records order by period_id", arrayOf()).use {
            val result = mutableListOf<Int>()
            while(it.moveToNext()) result.add(it.getInt(0))
            result
        }
        for(periodId in periodIds) updateSavedSleepPeriod(periodId, false)
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

    fun resetSleepBalance() {
        db.insertWithOnConflict(
            "should_reset_sleep_balance",
            null,
            ContentValues().apply { put("id", 1) },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    data class SleepRecordInput(
        val recordedAt: ZonedDateTime,
        val timeToFallAsleepMinutes: Long,
        val minimumSleepDurationMinutes: Long
    )

    fun startSleepPeriod(input: SleepRecordInput): Boolean {
        val time = encodeZonedDateTime(input.recordedAt)

        return db.transaction(exclusive = true) {
            val curPeriod = getLatestSleepPeriod()
            Log.d(TAG, "Active row $curPeriod")

            if(curPeriod == null || curPeriod.ended) {
                Log.d(TAG, "Starting new period")

                val id = getNextPeriodId()
                db.execSQL(
                    "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                    arrayOf(id, "period_begin", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
                )
                bumpSleepDataVersion()
                updateSavedSleepPeriod(id, true)

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
            val curPeriod = getLatestSleepPeriod()
            Log.d(TAG, "Active row $curPeriod")

            if(curPeriod != null && !curPeriod.ended) {
                Log.d(TAG, "Ending current period")

                db.execSQL(
                    "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                    arrayOf(curPeriod.id, "period_end", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
                )
                updateSavedSleepPeriod(curPeriod.id, false)
                updateSavedSleepPeriodsAfter(curPeriod.id)
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
            val curPeriod = getLatestSleepPeriod()
            if(curPeriod == null || curPeriod.ended) {
                Log.w(TAG, "Current period has ended/doesn't exist and this action should not have been accessible")
                return
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                arrayOf(curPeriod.id, "wake_up", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
            )
            updateSavedSleepPeriod(curPeriod.id, false)
            updateSavedSleepPeriodsAfter(curPeriod.id)
            bumpSleepDataVersion()
        }
    }

    fun recordSleepInterruption(input: SleepRecordInput) {
        Log.d(TAG, "recordSleepInterruption")

        val time = input.recordedAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        db.transaction(exclusive = true) {
            val curPeriod = getLatestSleepPeriod()
            if(curPeriod == null || curPeriod.ended) {
                Log.w(TAG, "Current period has ended/doesn't exist and this action should not have been accessible")
                return
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                arrayOf(curPeriod.id, "interruption", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
            )
            updateSavedSleepPeriod(curPeriod.id, false)
            updateSavedSleepPeriodsAfter(curPeriod.id)
            bumpSleepDataVersion()
        }
    }

    fun recordFallAsleep(input: SleepRecordInput) {
        Log.d(TAG, "recordFallAsleep")

        val time = input.recordedAt.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        db.transaction(exclusive = true) {
            val curPeriod = getLatestSleepPeriod()
            if(curPeriod == null || curPeriod.ended) {
                Log.w(TAG, "Current period has ended/doesn't exist and this action should not have been accessible")
                return
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time, time_to_fall_asleep_minutes, minimum_sleep_duration_minutes) values(?, ?, ?, ?, ?)",
                arrayOf(curPeriod.id, "fall_asleep", time, input.timeToFallAsleepMinutes, input.minimumSleepDurationMinutes)
            )
            updateSavedSleepPeriod(curPeriod.id, true)
            updateSavedSleepPeriodsAfter(curPeriod.id)
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

    fun deleteSleepPeriod(periodId: Int) {
        // NOTE: if we add manually resetting sleep balance in the future this may overwrite it.
        db.transaction(exclusive = true) {
            db.execSQL("update complete_sleep_periods set deleted = 1 where period_id = ?", arrayOf(periodId))
            updateSavedSleepPeriodsAfter(periodId)
            bumpSleepDataVersion()
        }
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

    fun getLatestSleepPeriod(): SleepPeriod? {
        val curPeriodId = db.rawQuery("select max(period_id) from complete_sleep_periods where deleted = 0", arrayOf()).use {
            it.moveToNext()
            it.getIntOrNull(0)
        }
        if(curPeriodId == null) return null

        val curPeriodEnded = db.rawQuery(
            "select count(*) from sleep_records where period_id = ? and type in (?, ?)",
            arrayOf("" + curPeriodId, "wake_up", "period_end")
        ).use {
            it.moveToNext()
            it.getLong(0) != 0L
        }
        return SleepPeriod(curPeriodId, curPeriodEnded)
    }

    fun getNextPeriodId(): Int {
        val curPeriodId = db.rawQuery("select max(period_id) from complete_sleep_periods", arrayOf()).use {
            it.moveToNext()
            it.getIntOrNull(0)
        }
        return (curPeriodId ?: 0) + 1
    }

    fun getLatestSleepPeriodData(): SavedSleepPeriodData? {
        return db.rawQuery(
            "$completePeriodQuery order by complete_sleep_periods.period_id desc limit 1",
            arrayOf(),
        ).use {
            if(!it.moveToNext()) null
            else decodeCompletePeriod(it)
        }
    }

    fun getAllSleepPeriodData(): List<SavedSleepPeriodData> {
        return db.rawQuery("$completePeriodQuery order by complete_sleep_periods.period_id desc", arrayOf()).use {
            val infos = mutableListOf<SavedSleepPeriodData>()
            while(it.moveToNext()) infos.add(decodeCompletePeriod(it))
            infos
        }
    }

    fun shouldShowQualityDialog(): Int? {
        val curPeriodId = getLatestSleepPeriod()?.id ?: return null

        val alreadyRecorded = db.rawQuery(
            "select 1 from sleep_quality where period_id = ? limit 1",
            arrayOf("" + curPeriodId)
        ).use {
            it.moveToNext()
        }
        if(alreadyRecorded) return null

        val wokeUp = db.rawQuery(
            "select 1 from sleep_records where period_id = ? and type in (?, ?) limit 1",
            arrayOf("" + curPeriodId, "wake_up", "period_end")
        ).use {
            it.moveToNext()
        }
        if(!wokeUp) return null

        return curPeriodId
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

private val completePeriodQuery = """
    select
        complete_sleep_periods.period_id,
        sleep_quality.quality,
        fall_asleep_time,
        wake_up_time,
        sleep_duration,
        duration_before_falling_asleep,
        interruption_count integer,
        sleep_balance_duration
    from complete_sleep_periods
    left join sleep_quality
    on complete_sleep_periods.period_id = sleep_quality.period_id
    where deleted = 0
""".trimIndent()

private fun decodeCompletePeriod(it: Cursor): SavedSleepPeriodData {
    return SavedSleepPeriodData(
        it.getInt(0),
        it.getIntOrNull(1) ?: 0,
        it.getStringOrNull(2)?.let {decodeInstant(it) },
        it.getStringOrNull(3)?.let {decodeInstant(it) },
        decodeDuration(it.getString(4)),
        decodeDuration(it.getString(5)),
        it.getInt(6),
        decodeDuration(it.getString(7)),
    )
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