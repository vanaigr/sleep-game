package com.example.sleepgame

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.RemoteViews
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.database.getIntOrNull
import com.example.sleepgame.MainActivity.Companion.CHANNEL_ID
import com.example.sleepgame.MainActivity.Companion.sleepControlsNotificationId
import org.godotengine.godot.Dictionary
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotFragment
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot
import java.lang.ref.WeakReference

class MainActivity: AppCompatActivity(), GodotHost {
    private lateinit var godotFragment: GodotFragment
    private var bridgePlugin: BridgePlugin? = null

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

        registerActivityLifecycleCallbacks(MainActivityTracker)
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
        val TAG = "Native"

        val CHANNEL_ID = "sleep_channel_v2"
        val OLD_CHANNEL_IDS = arrayOf("sleep_channel")
        val sleepControlsNotificationId = 1001
    }
}

class BridgePlugin(godot: Godot) : GodotPlugin(godot) {
    companion object {
    }

    override fun getPluginName() = "BridgePlugin"
    override fun getPluginSignals() = setOf<SignalInfo>()

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

    /**
     * @returns: whether the current status is sleep
     *  */
    @UsedByGodot
    fun toggleSleepPeriodActivation(): Boolean {
        val db = Database(context)

        val active = db.toggleSleepPeriodActivation()
        Log.d(MainActivity.TAG, "Active: $active")
        sleepControlsUpdate(activity ?: context)

        return active
    }
}

class SleepNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val db = Database(context)
        when (intent.action) {
            actionRecordWakeUp -> {
                db.recordWakeUp()
                sleepControlsUpdate(context)
            }
            actionRecordSleepInterruption -> {
                db.recordSleepInterruption()
                sleepControlsUpdate(context)
            }
            actionRecordFallAsleep -> {
                db.recordFallAsleep()
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
        val db = Database(activity).db

        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.sleep_quality)

        val setupSelectionButton = { quality: Int, button: View ->
            button.setOnClickListener {
                db.execSQL(
                    "insert or replace into sleep_quality(period_id, quality) values(?, ?)",
                    arrayOf(curPeriodId, quality),
                )
                Log.d(TAG, "Inserted quality record for $curPeriodId: $quality")
                dialog.dismiss()
            }
        }
        setupSelectionButton(5, dialog.findViewById(R.id.ideal))
        setupSelectionButton(4, dialog.findViewById(R.id.not_ideal))
        setupSelectionButton(3, dialog.findViewById(R.id.not_good))
        setupSelectionButton(2, dialog.findViewById(R.id.terrible))
        setupSelectionButton(1, dialog.findViewById(R.id.no_sleep))
        setupSelectionButton(0, dialog.findViewById(R.id.cancel))

        dialog.show()
    }

    Log.d(TAG, "Showing")
}

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


object MainActivityTracker : Application.ActivityLifecycleCallbacks {
    private var current: WeakReference<Activity>? = null

    val resumedActivity: Activity? get() = current?.get()

    override fun onActivityResumed(activity: Activity) {
        current = WeakReference(activity)
    }
    override fun onActivityPaused(activity: Activity) {
        if (current?.get() === activity) current = null
    }

    override fun onActivityCreated(a: Activity, b: Bundle?) {}
    override fun onActivityStarted(a: Activity) {}
    override fun onActivityStopped(a: Activity) {}
    override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
    override fun onActivityDestroyed(a: Activity) {}
}