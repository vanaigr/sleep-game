package com.example.sleepgame

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
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
        if (db.version == 0) {
            db.transaction {
                db.execSQL(
                    """
                    create table sleep_periods(
                        id integer primary key autoincrement,
                        begin_time ZonedDateTime not null,
                        end_time ZonedDateTime
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    create table active_sleep_period(
                        id integer
                    )
                """.trimIndent()
                )
                db.execSQL("insert into active_sleep_period(id) values(null)")

                db.execSQL(
                    """
                    create table failed_fall_asleeps(
                        id integer primary key autoincrement,
                        sleep_period_id integer not null,
                        recorded_time ZonedDateTime not null
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    create table failed_wake_ups(
                        id integer primary key autoincrement,
                        sleep_period_id integer not null,
                        recorded_time ZonedDateTime not null
                    )
                """.trimIndent()
                )

                db.execSQL(
                    """
                    create table sleep_interruptions(
                        id integer primary key autoincrement,
                        sleep_period_id integer not null,
                        recorded_time ZonedDateTime not null
                    )
                """.trimIndent()
                )

                db.version = 1
            }
        }
    }

    fun toggleSleepPeriodActivation(): Boolean {
        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        return db.transaction(exclusive = true) {
            val activeId = getActiveId()
            Log.d(TAG, "Active row $activeId")

            if(activeId != null) {
                val endTime = db.rawQuery("select end_time from sleep_periods where id = ?", arrayOf("" + activeId)).use {
                    it.moveToNext()
                    it.getStringOrNull(0)
                }
                if(endTime == null) {
                    db.execSQL(
                        "update sleep_periods set end_time = ? where id = ?",
                        arrayOf(time, activeId)
                    )
                }
                db.execSQL("update active_sleep_period set id = null")

                Log.d(TAG, "Updated row $activeId")
                false
            }
            else {
                val rowid = db.insert("sleep_periods", null, ContentValues().apply {
                    put("begin_time", time)
                })
                val newId = db.rawQuery("select id from sleep_periods where rowid = ?", arrayOf("" + rowid)).use {
                    it.moveToNext()
                    it.getInt(0)
                }
                db.execSQL("update active_sleep_period set id = ?", arrayOf(newId))
                Log.d(TAG, "Inserted new row: $newId ($rowid)")
                true
            }
        }
    }
    fun recordWakeUp(): Boolean {
        Log.d(TAG, "recordFallAsleep")

        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        return db.transaction(exclusive = true) {
            val activeId = getActiveId()
            Log.d(TAG, "Active $activeId")
            if(activeId != null) {
                val prevEndTime = db.rawQuery(
                    "select end_time from sleep_periods where id = ?",
                    arrayOf("" + activeId)
                ).use {
                    it.moveToNext()
                    it.getStringOrNull(0)
                }
                if (prevEndTime != null) {
                    db.execSQL(
                        "insert into failed_wake_ups(sleep_period_id, recorded_time) values (?, ?)",
                        arrayOf<Any>(activeId, prevEndTime)
                    )
                }
                db.execSQL(
                    "update sleep_periods set end_time = ? where id = ?",
                    arrayOf(time, activeId)
                )
                true
            }
            else {
                false
            }
        }
    }

    fun recordSleepInterruption(): Boolean {
        Log.d(TAG, "recordSleepInterruption")

        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        return db.transaction(exclusive = true) {
            val activeId = getActiveId()
            Log.d(TAG, "Active $activeId")
            if(activeId != null) {
                db.execSQL(
                    "insert into sleep_interruptions(sleep_period_id, recorded_time) values (?, ?)",
                    arrayOf<Any>(activeId, time)
                )
                true
            }
            else {
                false
            }
        }
    }

    fun recordFallAsleep(): Boolean {
        Log.d(TAG, "recordFallAsleep")

        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        return db.transaction(exclusive = true) {
            val activeId = getActiveId()
            Log.d(TAG, "Active $activeId")

            if(activeId != null) {
                val prevBeginTime = db.rawQuery(
                    "select begin_time from sleep_periods where id = ?",
                    arrayOf("" + activeId)
                ).use {
                    it.moveToNext()
                    it.getString(0)
                }
                db.execSQL(
                    "insert into failed_fall_asleeps(sleep_period_id, recorded_time) values (?, ?)",
                    arrayOf(activeId, prevBeginTime)
                )
                db.execSQL(
                    "update sleep_periods set begin_time = ? where id = ?",
                    arrayOf(time, activeId)
                )
                true
            }
            else {
                false
            }
        }
    }

    fun getActiveId(): Int? {
        return db.rawQuery("select id from active_sleep_period", arrayOf()).use {
            it.moveToNext()
            it.getIntOrNull(0)
        }
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