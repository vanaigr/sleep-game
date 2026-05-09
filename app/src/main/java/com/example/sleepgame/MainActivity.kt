package com.example.sleepgame

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotFragment
import org.godotengine.godot.GodotHost
import org.godotengine.godot.plugin.GodotPlugin
import org.godotengine.godot.plugin.SignalInfo
import org.godotengine.godot.plugin.UsedByGodot

class MainActivity: AppCompatActivity(), GodotHost {
    private lateinit var godotFragment: GodotFragment
    private lateinit var db: Database

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        db = Database(this)

        showSleepControls()

        val currentGodotFragment = supportFragmentManager.findFragmentById(R.id.godot_fragment_container)
        if (currentGodotFragment is GodotFragment) {
            godotFragment = currentGodotFragment
        } else {
            godotFragment = GodotFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.godot_fragment_container, godotFragment!!)
                .commitNowAllowingStateLoss()
        }
    }

    override fun getActivity() = this

    override fun getGodot() = godotFragment.godot

    fun hideSleepControls(context: Context) {
        NotificationManagerCompat.from(context).cancel(sleepControlsNotificationId)
    }

    @SuppressLint("MissingPermission")
    fun showSleepControls() {
        createChannel(this)
        val remoteViews = RemoteViews(this.packageName, R.layout.sleep_controls_notification)

        remoteViews.setOnClickPendingIntent(
            R.id.button_wake_up,
            PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, SleepNotificationActionReceiver::class.java).apply {
                    action = SleepNotificationActionReceiver.actionRecordWakeUp
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        remoteViews.setOnClickPendingIntent(
            R.id.button_sleep_interruption,
            PendingIntent.getBroadcast(
                this,
                1,
                Intent(this, SleepNotificationActionReceiver::class.java).apply {
                    action = SleepNotificationActionReceiver.actionRecordSleepInterruption
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        remoteViews.setOnClickPendingIntent(
            R.id.button_fall_asleep,
            PendingIntent.getBroadcast(
                this,
                2,
                Intent(this, SleepNotificationActionReceiver::class.java).apply {
                    action = SleepNotificationActionReceiver.actionRecordFallAsleep
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(this).notify(sleepControlsNotificationId, notification)
    }

    private fun createChannel(context: Context) {
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
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
        //}
    }

    companion object {
        val TAG = "Native"

        val CHANNEL_ID = "sleep_channel"
        val sleepControlsNotificationId = 1001
    }
}

class BridgePlugin(private val db: Database, godot: Godot) : GodotPlugin(godot) {

    companion object {
    }

    override fun getPluginName() = "BridgePlugin"
    override fun getPluginSignals() = setOf<SignalInfo>()

    /**
     * @returns: whether the current status is sleep
     *  */
    @UsedByGodot
    fun toggleSleep(): Boolean {
        return db.toggleSleep()
    }
}

class SleepNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ACTION_PAUSE" -> {
                // pause logic
            }
            "ACTION_STOP" -> {
                // stop logic, e.g. cancel the notification
                NotificationManagerCompat.from(context).cancel(1001)
            }
        }
    }

    companion object {
        val actionRecordFallAsleep = "ACTION_RECORD_FALL_ASLEEP"
        val actionRecordWakeUp = "ACTION_RECORD_WAKE_UP"
        val actionRecordSleepInterruption = "ACTION_RECORD_SLEEP_INTERRUPTION"
    }
}