package com.example.sleepgame

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.database.getIntOrNull
import androidx.fragment.app.DialogFragment
import com.example.sleepgame.MainActivity.Companion.CHANNEL_ID
import com.example.sleepgame.MainActivity.Companion.sleepControlsNotificationId
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotFragment
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.round

class MainActivity: AppCompatActivity(), GodotHost {
    private lateinit var godotFragment: GodotFragment
    private var bridgePlugin: BridgePlugin? = null

    var overrideTime: ZonedDateTime? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF
                || intent.action == Intent.ACTION_USER_PRESENT) {
                sleepControlsUpdate(context)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        createSleepControlsChannel(this)

        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })

        val currentGodotFragment = supportFragmentManager.findFragmentById(R.id.godot_fragment_container)
        if (currentGodotFragment is GodotFragment) {
            godotFragment = currentGodotFragment
        } else {
            godotFragment = GodotFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.godot_fragment_container, godotFragment)
                .commitNowAllowingStateLoss()
        }

        // Cool engine. It exits, but doesn't think it's necessary to notify anyone
        // So pressing back button just turns it into a glorified ImageView.
        @Suppress("UNCHECKED_CAST")
        (
            godotFragment.godot::class.java.getDeclaredField("runOnTerminate")
                .apply { isAccessible = true }
                .get(godotFragment.godot) as java.util.concurrent.atomic.AtomicReference<Runnable>
        ).set {
            finish()
        }

        MainActivityTracker.attach(this)
    }

    override fun onResume() {
        super.onResume()
        sleepControlsUpdate(this)
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenStateReceiver) } catch (_: IllegalArgumentException) {}
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        sleepControlsUpdate(this)
    }

    override fun getActivity() = this
    override fun getGodot() = godotFragment.godot
    override fun getHostPlugins(godot: Godot): Set<GodotPlugin> {
        if (bridgePlugin == null) {
            bridgePlugin = BridgePlugin(godot)
        }
        return setOf(bridgePlugin!!)
    }

    private fun createSleepControlsChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
            }
        }

        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Кнопки записи сна",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Кнопки записи сна"
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        for (oldId in MainActivity.OLD_CHANNEL_IDS) manager.deleteNotificationChannel(oldId)
        manager.createNotificationChannel(channel)
        //}
    }

    companion object {
        private val TAG = "Native"

        val CHANNEL_ID = "sleep_channel_v2"
        val OLD_CHANNEL_IDS = arrayOf("sleep_channel")
        val sleepControlsNotificationId = 1001
    }
}

data class SleepPeriodInfo(val periodId: Int, val quality: Int, val records: MutableList<Database.SleepRecord>)

val minimumSleepTime = Duration.ofMinutes(10)
val timeToFallAsleep = Duration.ofMinutes(15)

fun calculateSleepPeriodStats(info: SleepPeriodInfo): Dictionary? {
    var totalSleepDuration = Duration.ZERO
    var lastFallAsleepTime: Instant? = null
    var initialFallAsleepPhase = true // Only "period_begin" or "fall_asleep" were seen before
    var latestInitialFallAsleep: ZonedDateTime? = null
    var wakeUp: ZonedDateTime? = null

    val accumulateSleepUntil = fun(until: Instant) {
        val fallAsleep = lastFallAsleepTime
        if(fallAsleep == null) {
            Log.w("calculateSleepPeriodStats", "Invalid state at $until")
            return
        }

        val begin = fallAsleep + timeToFallAsleep
        val sleepTime = Duration.between(begin, until)
        if(sleepTime >= minimumSleepTime) {
            totalSleepDuration += sleepTime
        }
    }

    for(record in info.records) {
        when(record.type) {
            "period_begin", "fall_asleep" -> {
                if(initialFallAsleepPhase) {
                    lastFallAsleepTime = record.recordedTime.toInstant()
                    latestInitialFallAsleep = record.recordedTime
                }
                else {
                    val until = record.recordedTime.toInstant()
                    accumulateSleepUntil(until)
                    lastFallAsleepTime = until
                }
            }
            "interruption" -> {
                initialFallAsleepPhase = false
                val until = record.recordedTime.toInstant()
                accumulateSleepUntil(until)
                lastFallAsleepTime = until
            }
            "wake_up", "period_end" -> {
                initialFallAsleepPhase = false
                wakeUp = record.recordedTime
                val until = record.recordedTime.toInstant()
                accumulateSleepUntil(until)
                break
            }
            else -> {
                Log.d("calculateSleepPeriodStats", "Unknown record type $record")
            }
        }
    }

    if(latestInitialFallAsleep == null || wakeUp == null) return null

    val currentTimezone = getCurrentTime().zone

    val result = Dictionary()
    result["period_id"] = info.periodId
    result["duration"] = durationSecToString(totalSleepDuration.seconds)
    result["quality"] = when(info.quality) {
        1 -> "Не спал"
        2 -> "Ужасно"
        3 -> "Не очень"
        4 -> "Не идеально"
        5 -> "Замечательно"
        else -> "Не записано"
    }
    result["begin_time"] = latestInitialFallAsleep.withZoneSameInstant(currentTimezone) .toLocalTime().truncatedTo(ChronoUnit.SECONDS).toString()
    result["end_time"] = wakeUp.withZoneSameInstant(currentTimezone).toLocalTime().truncatedTo(ChronoUnit.SECONDS).toString()
    result["date"] = wakeUp.withZoneSameInstant(currentTimezone).toLocalDate().format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault())
    )

    return result
}

data class SleepPeriodGraphData(
    val latestInitialFallAsleep: ZonedDateTime?,
    val wakeUp: ZonedDateTime?,
    val nonSleepRanges: MutableList<Array<Instant>>,
)

fun calculateSleepPeriodGraphData(records: Iterable<Database.SleepRecord>): SleepPeriodGraphData {
    val nonSleepRanges = mutableListOf<Array<Instant>>()

    var totalSleepDuration = Duration.ZERO
    var lastFallAsleepTime: Instant? = null
    var initialFallAsleepPhase = true // Only "period_begin" or "fall_asleep" were seen before
    var latestInitialFallAsleep: ZonedDateTime? = null
    var wakeUp: ZonedDateTime? = null

    val accumulateSleepUntil = fun(until: Instant) {
        val fallAsleep = lastFallAsleepTime
        if(fallAsleep == null) {
            Log.w("calculateSleepPeriodNonSleepRanges", "Invalid state at $until")
            return
        }

        val begin = fallAsleep + timeToFallAsleep
        val sleepTime = Duration.between(begin, until)
        if(sleepTime >= minimumSleepTime) {
            totalSleepDuration += sleepTime
            nonSleepRanges.add(arrayOf(until, until))
        }
        else {
            nonSleepRanges.last()[1] = until
        }
    }

    for(record in records) {
        when(record.type) {
            "period_begin", "fall_asleep" -> {
                if(initialFallAsleepPhase) {
                    lastFallAsleepTime = record.recordedTime.toInstant()
                    latestInitialFallAsleep = record.recordedTime

                    if(nonSleepRanges.isEmpty()) {
                        nonSleepRanges.add(arrayOf(lastFallAsleepTime, lastFallAsleepTime))
                    }
                    else {
                        nonSleepRanges.last()[1] = lastFallAsleepTime
                    }
                }
                else {
                    val until = record.recordedTime.toInstant()
                    accumulateSleepUntil(until)
                    lastFallAsleepTime = until
                }
            }
            "interruption" -> {
                initialFallAsleepPhase = false
                val until = record.recordedTime.toInstant()
                accumulateSleepUntil(until)
                lastFallAsleepTime = until
            }
            "wake_up", "period_end" -> {
                initialFallAsleepPhase = false
                wakeUp = record.recordedTime
                val until = record.recordedTime.toInstant()
                accumulateSleepUntil(until)
                break
            }
            else -> {
                Log.d("calculateSleepPeriodNonSleepRanges", "Unknown record type $record")
            }
        }
    }

    return SleepPeriodGraphData(latestInitialFallAsleep, wakeUp, nonSleepRanges)
}

fun roundByHours(time: ZonedDateTime, roundByHours: Int, ceil: Boolean): ZonedDateTime {
    val startOfDay = time.truncatedTo(ChronoUnit.DAYS)
    val oldOffset = Duration.between(startOfDay, time)
    val floorOffset = Duration.ofHours(oldOffset.toHours() / roundByHours * roundByHours)
    val newOffset = run {
        if(!ceil) floorOffset
        else if(oldOffset == floorOffset) floorOffset
        else floorOffset + Duration.ofHours(roundByHours.toLong())
    }
    return startOfDay.plus(newOffset)
}

class BridgePlugin(godot: Godot) : GodotPlugin(godot) {
    companion object {
        private val TAG = "BridgePlugin"
    }

    override fun getPluginName() = "BridgePlugin"
    override fun getPluginSignals() = setOf<SignalInfo>()

    @UsedByGodot
    fun getSleepPeriodGraph(periodId: Int, width: Float, tickWidth: Float): Dictionary? {
        Log.d(TAG, "getSleepPeriodGraph")
        val db = Database(context)

        val records = db.getAllRecordsForPeriod(periodId)
        records.sortWith { a, b -> a.recordedTime.compareTo(b.recordedTime) }
        if(records.isEmpty()) {
            Log.d(TAG, "No records for the given period")
            return null
        }

        val padding = 4
        val maxTicks = floor(width / (tickWidth + padding)).toInt()

        val currentTimezone = getCurrentTime().zone
        val begin = records[0].recordedTime.withZoneSameInstant(currentTimezone)
        val end = records.last().recordedTime.withZoneSameInstant(currentTimezone)
        val endInstant = end.toInstant()


        var roundingHours = 1
        var roundedBegin: ZonedDateTime
        var roundedEnd: ZonedDateTime
        var tickCount: Long
        while(true) {
            roundedBegin = roundByHours(begin, roundingHours, false)
            val difference = Duration.between(roundedBegin, end)
            var totalHours = difference.toHours()
            if(difference != Duration.ofHours(totalHours)) totalHours++
            tickCount = Math.ceilDiv(totalHours, roundingHours) + 1

            if(tickCount <= maxTicks) break
            if(roundingHours >= 24) break
            roundingHours *= 2
        }
        roundedEnd = roundedBegin + Duration.ofHours((tickCount - 1) * roundingHours)
        val totalSeconds = Duration.between(roundedBegin, roundedEnd).seconds.toDouble()
        val roundedBeginInstant = roundedBegin.toInstant()

        val graphData = calculateSleepPeriodGraphData(records)

        val result = Dictionary()

        val timeToX = fun(time: Instant): Double {
            val factor = Duration.between(roundedBeginInstant, time).seconds.toDouble() / totalSeconds
            return (padding + tickWidth) * 0.5 + factor * (width - padding - tickWidth)
        }

        result["tick_count"] = tickCount
        for (tickI in 0 until tickCount) {
            val time = roundedBegin + Duration.ofHours(tickI * roundingHours)
            result["tick_${tickI}_label"] = "" + time.toLocalTime().hour
            result["tick_${tickI}_position"] = timeToX(time.toInstant())
        }

        var nonSleepPolygonCount = 0
        for(range in graphData.nonSleepRanges) {
            val rangeBegin = range[0]
            val rangeEnd = range[1]

            var smoothEnd = rangeEnd + timeToFallAsleep
            var smoothEndValue = 0.0
            // NOTE: currently, last record is always a wake up/period end record,
            // and this logic relies on that.
            if(smoothEnd > endInstant) {
                if(endInstant == rangeEnd) {
                    smoothEndValue = 0.0
                    smoothEnd = endInstant
                }
                else {
                    smoothEndValue =
                        Duration.between(rangeEnd, smoothEnd).seconds.toDouble() / Duration.between(
                            rangeEnd,
                            endInstant
                        ).seconds.toDouble()
                    smoothEnd = endInstant
                }
            }

            val beginX = timeToX(rangeBegin)
            val endX = timeToX(rangeEnd)
            val smoothEndX = timeToX(smoothEnd)

            val points = mutableListOf<Array<Double>>()
            points.add(arrayOf(beginX, 0.0))
            points.add(arrayOf(beginX, 1.0))
            points.add(arrayOf(endX, 1.0))
            points.add(arrayOf(smoothEndX, smoothEndValue))
            if(smoothEndValue != 0.0) points.add(arrayOf(smoothEndX, 0.0))

            var i = 0
            while(i != points.size) {
                val prev = if(i == 0) points.last() else points[i - 1]
                val cur = points[i]
                if(abs(prev[0] - cur[0]) < 1 && abs(prev[1] - cur[1]) < 0.001) {
                    points.removeAt(i)
                }
                else {
                    i++
                }
            }

            if(points.size < 3) continue

            for((i, point) in points.withIndex()) {
                result["non_sleep_point_${nonSleepPolygonCount}_${i}_x"] = point[0]
                result["non_sleep_point_${nonSleepPolygonCount}_${i}_y"] = point[1]
            }
            result["non_sleep_polygon_${nonSleepPolygonCount}_point_count"] = points.size
            nonSleepPolygonCount++
        }
        result["non_sleep_polygon_count"] = nonSleepPolygonCount

        if(graphData.latestInitialFallAsleep != null) {
            result["fall_asleep_position"] = timeToX(graphData.latestInitialFallAsleep.toInstant())
            result["fall_asleep_label"] = graphData.latestInitialFallAsleep.withZoneSameInstant(currentTimezone).toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES).toString()
        }
        if(graphData.wakeUp != null) {
            result["wake_up_position"] = timeToX(graphData.wakeUp.toInstant())
            result["wake_up_label"] = graphData.wakeUp.withZoneSameInstant(currentTimezone).toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES).toString()
        }

        Log.d(TAG, "done")
        return result
    }

    @UsedByGodot
    fun getLastCompletePeriodStats(): Dictionary? {
        val db = Database(context)

        val lastCompletedPeriod = db.db.rawQuery(
            "select max(period_id) from sleep_records where type = 'wake_up' or type = 'period_end' ",
            arrayOf(),
        ).use {
            if(!it.moveToNext()) null
            else it.getInt(0)
        }
        if(lastCompletedPeriod == null) return null

        val quality = db.db.rawQuery(
            "select quality from sleep_quality where period_id = ? limit 1",
            arrayOf("" + lastCompletedPeriod),
        ).use {
            if(!it.moveToNext()) 0
            else it.getInt(0)
        }

        val records = db.getAllRecordsForPeriod(lastCompletedPeriod)
        records.sortWith { a, b -> a.recordedTime.compareTo(b.recordedTime) }

        return calculateSleepPeriodStats(SleepPeriodInfo(lastCompletedPeriod, quality, records))
    }

    @UsedByGodot
    fun getSleepDataVersion(): Long {
        return Database(context).getSleepDataVersion()
    }

    @UsedByGodot
    fun getAllPeriodsStats(): Array<Dictionary> {
        val db = Database(context)

        val allQuality = db.getQualityByPeriodId()
        val allRecords = db.getAllRecords()
        val recordsByPeriod = HashMap<Int, MutableList<Database.SleepRecord>>()
        for(record in allRecords) {
            val list = recordsByPeriod.getOrPut(record.periodId) { mutableListOf() }
            list.add(record)
        }

        val periodIds = recordsByPeriod.keys.toIntArray()
        periodIds.sortDescending()

        val infos = mutableListOf<Dictionary>()
        for(periodId in periodIds) {
            val quality = allQuality[periodId] ?: 0
            val records = recordsByPeriod[periodId]!!
            records.sortWith { a, b -> a.recordedTime.compareTo(b.recordedTime) }
            val stats = calculateSleepPeriodStats(SleepPeriodInfo(periodId, quality, records))
            if(stats != null) infos.add(stats)
        }

        return infos.toTypedArray()
    }

    @UsedByGodot
    fun query(sql: String, args: Array<String>): Array<Dictionary> {
        val db = Database(context)
        return db.db.rawQuery(sql, args).use {
            val result = mutableListOf<Dictionary>()
            while(it.moveToNext()) {
                val row = Dictionary()
                for (i in 0 until it.columnCount) {
                    row[it.getColumnName(i)] = when (it.getType(i)) {
                        Cursor.FIELD_TYPE_NULL    -> null
                        Cursor.FIELD_TYPE_INTEGER -> it.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT   -> it.getDouble(i)
                        Cursor.FIELD_TYPE_STRING  -> it.getString(i)
                        Cursor.FIELD_TYPE_BLOB    -> it.getBlob(i)
                        else -> it.getString(i)
                    }
                }
                result.add(row)
            }
            result.toTypedArray()
        }
    }

    @UsedByGodot
    fun clickBed() {
        val db = Database(context)
        db.startSleepPeriod(getCurrentTime())
        sleepControlsUpdate(activity ?: context)
    }
    @UsedByGodot
    fun clickAlarmClock() {
        val db = Database(context)
        db.endSleepPeriod(getCurrentTime())
        sleepControlsUpdate(activity ?: context)
    }

    @UsedByGodot
    fun _debugGetCurrentTime(): String {
        return ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
    }

    @UsedByGodot
    fun _debugSetCurrentTime(time: String, debugging: Boolean): Boolean/*is time valid*/ {
        val parsedTime = try {
            ZonedDateTime.parse(time)
        } catch (e: Exception) {
            null
        }

        if(debugging) {
            if (parsedTime != null) {
                MainActivityTracker.resumedActivity?.overrideTime = parsedTime
            }
        }
        else {
            MainActivityTracker.resumedActivity?.overrideTime = null
        }

        return parsedTime != null
    }
}

fun durationSecToString(value: Long): String {
    val sign = if(value >= 0) "" else "-"

    val totalSeconds = abs(value)
    val totalMinutes = totalSeconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes - hours * 60
    val seconds = totalSeconds - totalMinutes * 60

    return "$sign$hours ч. $minutes мин. $seconds сек."
}

class SleepNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val db = Database(context)
        when (intent.action) {
            actionRecordWakeUp -> {
                db.recordWakeUp(getCurrentTime())
                sleepControlsUpdate(context)
            }
            actionRecordSleepInterruption -> {
                db.recordSleepInterruption(getCurrentTime())
                sleepControlsUpdate(context)
            }
            actionRecordFallAsleep -> {
                db.recordFallAsleep(getCurrentTime())
                sleepControlsUpdate(context)
            }
        }
    }

    companion object {
        val actionRecordFallAsleep = "ACTION_RECORD_FALL_ASLEEP"
        val actionRecordWakeUp = "ACTION_RECORD_WAKE_UP"
        val actionRecordSleepInterruption = "ACTION_RECORD_SLEEP_INTERRUPTION"
    }
}

fun sleepControlsUpdate(context: Context) {
    if(Database(context).getCurrentSleepPeriod().ended) sleepControlsHide(context)
    else sleepControlsShow(context)

    sleepQualityUpdate()
}

fun sleepQualityUpdate() {
    val TAG = "Q Check"

    Log.d(TAG, "Begin")

    val activity = MainActivityTracker.resumedActivity
    if(activity == null) {
        Log.d(TAG, "Skipping: activity is not opened")
        return
    }
    if (activity.isFinishing || activity.isDestroyed) {
        Log.d(TAG, "Skipping: activity is finishing (${activity.isFinishing}) or destroyed (${activity.isDestroyed})")
        return
    }

    val db = Database(activity).db

    val curPeriodId = db.rawQuery("select max(period_id) from sleep_records", arrayOf()).use {
        it.moveToNext()
        it.getIntOrNull(0)
    }
    if(curPeriodId == null) {
        Log.d(TAG, "Skipping: no current period")
        return
    }
    Log.d(TAG, "Checking $curPeriodId")

    val alreadyRecorded = db.rawQuery(
        "select 1 from sleep_quality where period_id = ? limit 1",
        arrayOf("" + curPeriodId)
    ).use {
        it.moveToNext()
    }
    if(alreadyRecorded) {
        Log.d(TAG, "Skipping: already recorded")
        return
    }

    val wokeUp = db.rawQuery(
        "select 1 from sleep_records where period_id = ? and type in (?, ?) limit 1",
        arrayOf("" + curPeriodId, "wake_up", "period_end")
    ).use {
        it.moveToNext()
    }
    if(!wokeUp) {
        Log.d(TAG, "Skipping: haven't woken up")
        return
    }

    activity.runOnUiThread {
        val fm = activity.supportFragmentManager

        val oldDialog = fm.findFragmentByTag(SleepQualityDialogFragment.TAG) as? DialogFragment
        oldDialog?.dismiss()
        fm.executePendingTransactions()

        SleepQualityDialogFragment(curPeriodId)
            .show(fm, SleepQualityDialogFragment.TAG)
    }

    Log.d(TAG, "Showing")
}

object MainActivityTracker : ActivityTracker<MainActivity>()

fun sleepControlsHide(context: Context) {
    NotificationManagerCompat.from(context).cancel(sleepControlsNotificationId)
}

@SuppressLint("MissingPermission")
fun sleepControlsShow(context: Context) {
    val remoteViews = RemoteViews(context.packageName, R.layout.sleep_controls_notification)

    remoteViews.setOnClickPendingIntent(
        R.id.button_wake_up,
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SleepNotificationActionReceiver::class.java).apply {
                action = SleepNotificationActionReceiver.actionRecordWakeUp
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )
    remoteViews.setOnClickPendingIntent(
        R.id.button_sleep_interruption,
        PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, SleepNotificationActionReceiver::class.java).apply {
                action = SleepNotificationActionReceiver.actionRecordSleepInterruption
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )
    remoteViews.setOnClickPendingIntent(
        R.id.button_fall_asleep,
        PendingIntent.getBroadcast(
            context,
            2,
            Intent(context, SleepNotificationActionReceiver::class.java).apply {
                action = SleepNotificationActionReceiver.actionRecordFallAsleep
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    )

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(remoteViews)
        .setCustomBigContentView(remoteViews)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .build()

    NotificationManagerCompat.from(context).notify(sleepControlsNotificationId, notification)
}


class SleepQualityDialogFragment(private val periodId: Int) : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()

        val dialog = Dialog(context)
        dialog.setContentView(R.layout.sleep_quality)

        val setupSelectionButton = { quality: Int, button: View ->
            button.setOnClickListener { selectQuality(quality) }
        }
        setupSelectionButton(5, dialog.findViewById(R.id.ideal))
        setupSelectionButton(4, dialog.findViewById(R.id.not_ideal))
        setupSelectionButton(3, dialog.findViewById(R.id.not_good))
        setupSelectionButton(2, dialog.findViewById(R.id.terrible))
        setupSelectionButton(1, dialog.findViewById(R.id.no_sleep))
        setupSelectionButton(0, dialog.findViewById(R.id.cancel))

        return dialog
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        selectQuality(0)
    }

    private fun selectQuality(quality: Int) {
        val db = Database(requireContext())
        db.setSleepQuality(periodId, quality)
        this.dismiss()
    }

    companion object {
        const val TAG = "MyDialogFragment"
    }
}

fun getCurrentTime(): ZonedDateTime {
    return MainActivityTracker.resumedActivity?.overrideTime ?: ZonedDateTime.now()
}