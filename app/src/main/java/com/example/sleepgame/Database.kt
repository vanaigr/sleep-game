package com.example.sleepgame

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.database.sqlite.transaction
import java.io.File
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

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

    fun toggleSleep(): Boolean {
        val time = ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        // Option 1: transaction is fun(body: () -> Unit): Boolean
        // Option 2: transaction is <R>fun(body: () -> R): R, but use is actually fun(body: () -> Unit): Boolean
        // Option 3: both transaction and use are <R>fun(body: () -> R): R
        // I may have declared an extension function somewhere in the codebase that overrides one of those btw.
        return db.transaction(exclusive = true) {
            db.rawQuery(
                "select id from sleep_periods where end_time is null limit 1",
                arrayOf()
            ).use { unfinishedSleepCursor ->
                if(unfinishedSleepCursor.moveToNext()) {
                    val id = unfinishedSleepCursor.getInt(0)
                    db.execSQL(
                        "update sleep_periods set end_time = ? where id = ?",
                        arrayOf(time, id),
                    )
                    false
                }
                else {
                    db.execSQL(
                        "insert into sleep_periods(begin_time) values(?)",
                        arrayOf(time),
                    )
                    true
                }
            }
        }
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