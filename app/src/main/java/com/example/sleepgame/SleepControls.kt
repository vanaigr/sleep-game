package com.example.sleepgame

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

private val TAG = "Sleep Controls"

private val CHANNEL_ID = "sleep_channel_v2"
private val OLD_CHANNEL_IDS = arrayOf("sleep_channel")
private val sleepControlsNotificationId = 1001


fun sleepControlsUpdate(context: Context) {
    val period = Database(context).getLatestSleepPeriod()
    if(period == null || period.ended) sleepControlsHide(context)
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

    val db = Database(activity)
    val periodId = db.shouldShowQualityDialog() ?: return

    activity.showQualityDialogForS.value = periodId

    Log.d(TAG, "Showing")
}

fun createSleepControlsChannel(context: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            context.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
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
    for (oldId in OLD_CHANNEL_IDS) manager.deleteNotificationChannel(oldId)
    manager.createNotificationChannel(channel)
    //}
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

class SleepNotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val db = Database(context)
        when (intent.action) {
            actionRecordWakeUp -> {
                db.recordWakeUp(Database.SleepRecordInput(getCurrentTime(), defaultTimeToFallAsleepMinutes, defaultMinimumSleepDurationMinutes))
                sleepControlsUpdate(context)
            }
            actionRecordSleepInterruption -> {
                db.recordSleepInterruption(Database.SleepRecordInput(getCurrentTime(), defaultTimeToFallAsleepMinutes, defaultMinimumSleepDurationMinutes))
                sleepControlsUpdate(context)
            }
            actionRecordFallAsleep -> {
                db.recordFallAsleep(Database.SleepRecordInput(getCurrentTime(), defaultTimeToFallAsleepMinutes, defaultMinimumSleepDurationMinutes))
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