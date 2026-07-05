package com.example.sleepgame

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SleepControlsActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Database.forApp(this)

        setContent {
            CompositionLocalProvider(DbContext provides db) {
                Overlay()
            }
        }

        lifecycleScope.launch {
            snapshotFlow { db.sleepPeriodsVersion.value }
                .collect {
                    if(!isSleepPeriodActive(db)) finish()
                }
        }
    }
}

@Preview
@Composable
fun Preview() {
    Box(Modifier.width(450.dp).height(1000.dp)) {
        Overlay()
    }
}

@Composable
fun Overlay() {
    val context = LocalContext.current
    val db = DbContext.current

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp).align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Button(
                {
                    db?.let { db ->
                        val settings = db.getSettings()
                        db.recordFallAsleep(Database.SleepRecordInput(getCurrentTime(), settings.time_to_fall_asleep_after_interruption_minutes.toLong(), defaultMinimumSleepDurationMinutes))
                        sleepControlsUpdate(context)
                    }
                },
                Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Записать засыпание")
                    Image(painterResource(R.drawable.moon), "", Modifier.height(20.dp))
                }
            }

            Button(
                {
                    db?.let { db ->
                        val settings = db.getSettings()
                        db.recordSleepInterruption(Database.SleepRecordInput(getCurrentTime(), settings.time_to_fall_asleep_after_interruption_minutes.toLong(), defaultMinimumSleepDurationMinutes))
                        sleepControlsUpdate(context)
                    }
                },
                Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Записать ночное пробуждение")
                    //Image(painterResource(R.drawable.moon), "", Modifier.height(20.dp))
                }
            }

            Button(
                {
                    db?.let { db ->
                        val settings = db.getSettings()
                        db.recordWakeUp(Database.SleepRecordInput(getCurrentTime(), settings.time_to_fall_asleep_minutes.toLong(), defaultMinimumSleepDurationMinutes))
                        sleepControlsUpdate(context)
                    }
                },
                Modifier.fillMaxWidth()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Записать пробуждение")
                    Image(painterResource(R.drawable.sun), "", Modifier.height(20.dp))
                }
            }
        }
    }
}

private val channelId = "sleep_channel_v2"
private val OLD_CHANNEL_IDS = arrayOf("sleep_channel")
private val sleepControlsNotificationId = 1001

private fun isSleepPeriodActive(db: Database): Boolean {
    val period = db.getLatestSleepPeriod()
    return !(period == null || period.ended)
}

fun sleepControlsUpdate(context: Context) {
    if(isSleepPeriodActive(Database.forApp(context))) sleepControlsShow(context)
    else sleepControlsHide(context)

    sleepQualityUpdate()
}

private fun sleepQualityUpdate() {
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

    val db = activity.database
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
        channelId,
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

private fun sleepControlsHide(context: Context) {
    NotificationManagerCompat.from(context).cancel(sleepControlsNotificationId)
}

private fun sleepControlsShow(context: Context) {
    if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        return
    }

    val intent = Intent(context, SleepControlsActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        0,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.sun)
        .setContentTitle("Данные о сне записываются")
        .setContentText("Нажмите чтобы открыть быстрые кнопки записи")
        .setOngoing(true)
        .setContentIntent(pendingIntent)
        .setAutoCancel(false)
        .setOnlyAlertOnce(true)
        .build()

    NotificationManagerCompat.from(context).notify(sleepControlsNotificationId, notification)
}