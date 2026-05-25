package com.example.sleepgame

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.sqlite.transaction
import java.io.File
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
        }
    }

    fun startSleepPeriod(): Boolean {
        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        return db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            Log.d(TAG, "Active row $curPeriod")

            if(curPeriod.ended) {
                Log.d(TAG, "Starting new period")

                val id = curPeriod.id + 1
                db.execSQL(
                    "insert into sleep_records(period_id, type, recorded_time) values(?, ?, ?)",
                    arrayOf(id, "period_begin", time)
                )

                true
            }
            else {
                Log.d(TAG, "Already started")
                false
            }
        }
    }

    fun endSleepPeriod(): Boolean {
        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        return db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            Log.d(TAG, "Active row $curPeriod")

            if(!curPeriod.ended) {
                Log.d(TAG, "Ending current period")

                db.execSQL(
                    "insert into sleep_records(period_id, type, recorded_time) values(?, ?, ?)",
                    arrayOf(curPeriod.id, "period_end", time)
                )

                true
            }
            else {
                Log.d(TAG, "Already ended")
                false
            }
        }
    }
    fun recordWakeUp() {
        Log.d(TAG, "recordFallAsleep")

        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            if(curPeriod.ended) {
                Log.w(TAG, "Current period has ended and this action should not have been accessible")
                // fallthrough
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time) values(?, ?, ?)",
                arrayOf(curPeriod.id, "wake_up", time)
            )
        }
    }

    fun recordSleepInterruption() {
        Log.d(TAG, "recordSleepInterruption")

        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            if(curPeriod.ended) {
                Log.w(TAG, "Current period has ended and this action should not have been accessible")
                // fallthrough
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time) values(?, ?, ?)",
                arrayOf(curPeriod.id, "interruption", time)
            )
        }
    }

    fun recordFallAsleep() {
        Log.d(TAG, "recordFallAsleep")

        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        db.transaction(exclusive = true) {
            val curPeriod = getCurrentSleepPeriod()
            if(curPeriod.ended) {
                Log.w(TAG, "Current period has ended and this action should not have been accessible")
                // fallthrough
            }

            db.execSQL(
                "insert into sleep_records(period_id, type, recorded_time) values(?, ?, ?)",
                arrayOf(curPeriod.id, "fall_asleep", time)
            )
        }
    }

    fun getAllRecordsForPeriod(periodId: Int): MutableList<SleepRecord> {
        return db.rawQuery(
            "select type, recorded_time from sleep_records where period_id = ?",
            arrayOf("" + periodId)
        ).use {
            val result = mutableListOf<SleepRecord>()
            while(it.moveToNext()) {
                result.add(SleepRecord(
                    it.getString(0),
                    ZonedDateTime.parse(it.getString(1))
                ))
            }
            result
        }
    }

    data class SleepRecord(val type: String, val recordedTime: ZonedDateTime)

    data class SleepPeriod(val id: Int, val ended: Boolean)

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