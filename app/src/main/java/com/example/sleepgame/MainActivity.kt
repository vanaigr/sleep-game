package com.example.sleepgame

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.lang.Math.clamp
import java.time.ZonedDateTime
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.time.toKotlinDuration

object MainActivityTracker : ActivityTracker<MainActivity>()


class MainActivity: ComponentActivity() {
    var overrideTimeS = mutableStateOf<ZonedDateTime?>(null)

    val showQualityDialogForS = mutableStateOf<Int?>(null)
    lateinit var database: Database

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
        createSleepControlsChannel(this)

        database = Database.forApp(this)

        registerReceiver(screenStateReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })

        MainActivityTracker.attach(this)

        setContent {
            CompositionLocalProvider(DbContext provides database) {
                Main(showQualityDialogForS)
            }
        }
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
}


val safeSize = Vector2(1080.0f, 2160.0f)
val maxSize = Vector2(1620.0f, 2520.0f)
val minAspect = 9.0f / 21.0f
val maxAspect = 3.0f / 4.0f


val defaultTimeToFallAsleepMinutes = 15L
val defaultMinimumSleepDurationMinutes = 10L

val DbContext = staticCompositionLocalOf<Database?> { null }

@Composable
inline fun Screen(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val configuration = LocalConfiguration.current

    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    Box(modifier.size(screenWidth, screenHeight).clip(RectangleShape), content = content)
}

@Composable
fun BoxScope.ScreenPositioning(
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current

    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()

    val aspect = clamp(screenWidth / screenHeight, minAspect, maxAspect)
    val safeAspect = safeSize.x / safeSize.y

    val size = if(aspect < safeAspect) Vector2(screenWidth, screenWidth / safeAspect)
    else Vector2(screenHeight * safeAspect, screenHeight)

    FreeLayout(
        safeSize,
        Modifier.size(size.x.dp, size.y.dp)
            .align(Alignment.Center),
        content = content
    )
}

@Composable
fun BedroomScreen(animatable: Float, toCellar: () -> Unit) {
    val db = DbContext.current
    val context = LocalContext.current
    val currentTimezone = getCurrentTime().zone

    Screen(
        Modifier
            .offset(y = (animatable * LocalConfiguration.current.screenHeightDp.dp.value).dp)
    ) {
        ScreenPositioning {
            val bg = painterResource(R.drawable.background_main_screen)
            Image(
                bg,
                contentDescription = null,
                Modifier
                    .freePosition(
                        0.0f,
                        -(maxSize.y - safeSize.y) * 0.5f,
                        null,
                        maxSize.y,
                    )
            )

            val bed = painterResource(R.drawable.bed)
            Image(
                bed,
                contentDescription = null,
                Modifier
                    .freePosition(10.0f, safeSize.y * 0.72f, safeSize.x * 0.6f, null)
                    .aspectRatio(bed.intrinsicSize.width / bed.intrinsicSize.height)
            )

            val spider = painterResource(R.drawable.spider)
            Image(
                spider,
                contentDescription = null,
                Modifier
                    .freePosition(
                        safeSize.x * 0.7f,
                        safeSize.y * 0.72f,
                        safeSize.x * 0.3f,
                        null
                    )
                    .aspectRatio(spider.intrinsicSize.width / spider.intrinsicSize.height)
            )

            val alarm = painterResource(R.drawable.alarm)
            Image(
                alarm,
                contentDescription = null,
                Modifier
                    .freePosition(safeSize.x * 0.7f, safeSize.y * 0.6f, safeSize.x * 0.3f, null)
                    .aspectRatio(alarm.intrinsicSize.width / alarm.intrinsicSize.height)
            )

            val board = painterResource(R.drawable.board)
            Image(
                board,
                contentDescription = null,
                Modifier
                    .freePosition(safeSize.x * 0.5f, safeSize.y * 0.3f, safeSize.x * 0.4f, null)
                    .aspectRatio(board.intrinsicSize.width / board.intrinsicSize.height)
            )

            db?.sleepPeriodsVersion?.value

            val statsText = run {
                if(LocalInspectionMode.current) return@run ""

                val info = db?.getLatestSleepPeriodData() ?: return@run "Пока нет данных"

                // @formatter:off
                return@run arrayOf(
                    "Время пробуждения: "
                        + (
                            info.wakeUp?.let { it.atZone(currentTimezone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }
                                ?: "Н/Д"
                        ),
                    "Время засыпания: "
                        + (
                            info.fallAsleep?.let { it.atZone(currentTimezone).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) }
                                ?: "Н/Д"
                        ),
                    "Качество: " + qualityToString(info.quality),
                    "Продолжительность: " + durationToHHMM(info.totalSleepDuration),
                ).joinToString("\n")
                // @formatter:on
            }

            val offset = 50
            Text(
                statsText,
                Modifier.freePosition(
                    safeSize.x * 0.5f + offset, safeSize.y * 0.3f + offset,
                    safeSize.x * 0.4f - offset * 2, safeSize.y * 0.26f - offset * 2
                ),
                style = TextStyle(fontSize = 9.sp),
            )

            // Click areas

            Box(
                Modifier
                    .freePosition(0f, safeSize.y * 0.7f, safeSize.x * 0.65f, safeSize.y * 0.15f)
                    .clickable {
                        db?.startSleepPeriod(Database.SleepRecordInput(
                            getCurrentTime(),
                            defaultTimeToFallAsleepMinutes,
                            defaultMinimumSleepDurationMinutes
                        ))
                        sleepControlsUpdate(context)
                    }
            ) {}

            Box(
                Modifier
                    .freePosition(safeSize.x * 0.7f, safeSize.y * 0.6f, safeSize.x * 0.3f, safeSize.y * 0.15f)
                    .clickable {
                        db?.endSleepPeriod(Database.SleepRecordInput(
                            getCurrentTime(),
                            defaultTimeToFallAsleepMinutes,
                            defaultMinimumSleepDurationMinutes
                        ))
                        sleepControlsUpdate(context)
                    }
            ) {}
        }

        Column(Modifier.fillMaxSize()) {
            CurrentTimeDebug()

            Column(Modifier.weight(1.0f)) {}

            Column(Modifier.fillMaxWidth()) {
                Button({ toCellar() }, Modifier.fillMaxWidth()) {
                    Text("В подвал")
                }
            }
        }
    }
}

@Composable
fun CurrentTimeDebug() {
    val activity = MainActivityTracker.resumedActivity

    val overrideTime = activity?.overrideTimeS?.value

    val timeTextS = remember { mutableStateOf(TextFieldValue("")) }
    val overridingTimeS = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = ZonedDateTime.now()
            if(!overridingTimeS.value) {
                timeTextS.value = TextFieldValue(
                    now.truncatedTo(ChronoUnit.SECONDS)
                        .format(DateTimeFormatter.ISO_ZONED_DATE_TIME)
                )
            }
            kotlinx.coroutines.delay(
                (Duration.between(now, now.truncatedTo(ChronoUnit.SECONDS)) + Duration.ofSeconds(1)).toKotlinDuration()
            )
        }
    }

    val parsedTime = try { ZonedDateTime.parse(timeTextS.value.text) } catch(_: Exception) { null }
    if(overridingTimeS.value && parsedTime != null) {
        activity?.overrideTimeS?.value = parsedTime
    }
    else {
        activity?.overrideTimeS?.value = null
    }

    Column(Modifier.fillMaxWidth()) {
        Button({ overridingTimeS.value = !overridingTimeS.value }, Modifier.fillMaxWidth()) {
            Text(if(overridingTimeS.value) "Подмена времени включена" else "Подмена времени отключена")
        }
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {

            TextField(
                value = timeTextS.value,
                onValueChange = { it: TextFieldValue ->
                    timeTextS.value = it
                },
                Modifier.weight(1.0f)
            )

            Image(
                if(parsedTime != null) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                Modifier.fillMaxHeight().aspectRatio(1.0f)
            )
        }
    }
}

@Preview
@Composable
fun BedroomPreview() {
    BedroomScreen(0f, {})
}

@Composable
fun DetailsPreview() {
    val records = listOf<SleepRecord>(
        SleepRecord(
            0,
            "period_begin",
            ZonedDateTime.parse("2026-01-01T05:18:01-05:00"),
            Duration.ofMinutes(15),
            Duration.ofMinutes(10),
        ),
        SleepRecord(
            0,
            "period_end",
            ZonedDateTime.parse("2026-01-02T05:06:01-05:00"),
            Duration.ofMinutes(15),
            Duration.ofMinutes(10),
        ),
    )

    val info0 = calculateSleepPeriodData(records)
    val info = SavedSleepPeriodData(
        0,
        1,
        info0.fallAsleep,
        info0.wakeUp,
        info0.totalSleepDuration,
        info0.durationBeforeFallingAsleep,
        info0.interruptionCount,
        Duration.ofHours(8) - info0.totalSleepDuration,
    )


    /*
    Column(Modifier.width(300.dp).height(50.dp).background(Color.Gray)) {
        CellarSleepPeriodListItem(
            Modifier.fillMaxSize(),
            info,
            ZoneId.of("America/Chicago"),
            true,
            {}
        )
    }
     */

    SleepPeriodDetailsScreenDisplay(info, records, { })
}

@Composable
fun CellarSleepPeriodListItem(
    modifier: Modifier = Modifier,
    info: SavedSleepPeriodData, timezone: ZoneId,
    topGap: Boolean,
    openDetails: (info: SavedSleepPeriodData) -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]

    Column(modifier) {
        if(topGap) {
            Row(Modifier.fillMaxWidth().height(1.dp).background(Color.Black)) {
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1.0f)
                    .padding(start = 10.dp, end = 10.dp, top = 5.dp, bottom = 5.dp)
                    .clickable { openDetails(info) }
            ) {
                val dateText = info.wakeUp?.atZone(timezone)?.toLocalDate()?.format(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
                )

                val qualityText = qualityToString(info.quality)
                val sleepDurationText = durationToHHMM(info.sleepBalance)

                Text(dateText ?: "Unknown", Modifier.weight(1.0f).padding(end = 5.dp))
                Text(qualityText, Modifier.padding(end = 5.dp))
                Text(sleepDurationText)
            }
        }
    }
    /*
    fun makeSleepPeriodDataDict(info: SavedSleepPeriodData): Dictionary {
        val currentTimezone = getCurrentTime().zone

        val result = Dictionary()
        result["period_id"] = info.periodId
        result["duration"] = durationSecToString(info.totalSleepDuration.seconds)
        result["quality"] = when(info.quality) {
            1 -> "Не спал"
            2 -> "Ужасно"
            3 -> "Не очень"
            4 -> "Не идеально"
            5 -> "Замечательно"
            else -> "Не записано"
        }
        result["begin_time"] = info.fallAsleep?.atZone(currentTimezone)?.toLocalTime()?.truncatedTo(ChronoUnit.SECONDS).toString()
        result["end_time"] = info.wakeUp?.atZone(currentTimezone)?.toLocalTime()?.truncatedTo(ChronoUnit.SECONDS).toString()
        result["date"] = info.wakeUp?.atZone(currentTimezone)?.toLocalDate()?.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault())
        )
        result["interruption_count"] = info.interruptionCount
        result["duration_before_falling_asleep"] = durationSecToString(info.durationBeforeFallingAsleep.seconds)
        result["sleep_balance"] = durationSecToString(info.sleepBalance.seconds)

        return result
    }
     */
}

fun durationToHHMM(duration: Duration): String {
    var totalMinutes = (duration + Duration.ofSeconds(30)).toMinutes()

    val sign = if(totalMinutes < 0) "-" else ""
    totalMinutes = abs(totalMinutes)

    val hours = totalMinutes / 60
    val minutes = totalMinutes - hours * 60

    return sign + hours + ":" + minutes.toString().padStart(2, '0')
}

fun qualityToString(quality: Int): String {
    return when (quality) {
        1 -> "Не спал"
        2 -> "Ужасно"
        3 -> "Не очень"
        4 -> "Не идеально"
        5 -> "Замечательно"
        else -> "Не записано"
    }
}

@Composable
fun SleepPeriodDetailsScreen(info: SavedSleepPeriodData, close: () -> Unit) {
    val db = DbContext.current
    db?.sleepPeriodsVersion?.value

    val records = remember {
        val records = db?.getAllRecordsForPeriod(info.periodId) ?: mutableListOf()
        records.sortWith { a, b -> a.recordedTime.compareTo(b.recordedTime) }

        records
    }

    SleepPeriodDetailsScreenDisplay(info, records, close)
}

@Composable
fun SleepPeriodDetailsScreenDisplay(info: SavedSleepPeriodData, records: List<SleepRecord>, close: () -> Unit) {
    val TAG = "SleepPeriodDetailsScreenDisplay"

    val db = DbContext.current
    val currentTimezone = remember { getCurrentTime().zone }

    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    Screen(Modifier.background(Color.DarkGray)) {
        Column(Modifier.padding(10.dp)) {
            Text("Ночь на " +
                    info.wakeUp?.atZone(currentTimezone)?.toLocalDate()?.format(
                        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(Locale.getDefault())
                    )
            )
            Column(Modifier.height(10.dp)) {}

            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                val width = size.width
                val height = size.height

                if(records.isEmpty()) {
                    Log.d(TAG, "No records for the given period")
                    return@Canvas
                }

                val tickFontSize = 12.sp
                val tickFontSizePx = with(density) { tickFontSize.toPx() }
                val numbers = Array(24) { i ->
                    textMeasurer.measure("${1 + i}", style = TextStyle(fontSize = tickFontSize))
                }
                val tickWidth = numbers.maxBy { it.size.width }.size.width

                val padding = with(density) { 4.dp.toPx() }
                val maxTicks = floor(width / (tickWidth + padding)).toInt()

                val currentTimezone = getCurrentTime().zone
                val begin = records[0].recordedTime.withZoneSameInstant(currentTimezone)
                val end = records.last().recordedTime.withZoneSameInstant(currentTimezone)

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

                val graphData = calculateSleepPeriodData(records)

                val sleepTimeY = height - padding * 0.5f
                val tickY = sleepTimeY - tickFontSizePx - padding * 0.5f
                val graphBottom = tickY - tickFontSizePx - padding * 0.5f

                val lineWidth = with(density) { 2.dp.toPx() }

                drawLine(
                    SolidColor(Color.Gray),
                    Offset((padding + tickWidth) * 0.5f, graphBottom + lineWidth * 0.5f),
                    Offset(width - (padding + tickWidth) * 0.5f, graphBottom + lineWidth * 0.5f),
                    lineWidth
                )

                val timeToX = fun(time: Instant): Double {
                    val factor = Duration.between(roundedBeginInstant, time).seconds.toDouble() / totalSeconds
                    return (padding + tickWidth) * 0.5 + factor * (width - padding - tickWidth)
                }

                for (tickI in 0 until tickCount) {
                    val time = roundedBegin + Duration.ofHours(tickI * roundingHours)
                    val position = timeToX(time.toInstant()).toFloat()

                    drawLine(
                        SolidColor(Color.Gray),
                        Offset(position, padding * 0.5f),
                        Offset(position, graphBottom + lineWidth),
                        lineWidth
                    )

                    // TODO: this should be drawn in a separate pass to prevent overlap
                    // but I don't think there can be overlap so doesn't matter?
                    val textMeasure = numbers[-1 + time.toLocalTime().hour]
                    drawText(
                        textMeasure,
                        Color.White,
                        Offset(position - textMeasure.size.width * 0.5f, tickY - tickFontSizePx),
                    )
                }

                for(rangeI in 1 until (graphData.nonSleepRanges.size - 1)) {
                    val range = graphData.nonSleepRanges[rangeI]

                    val beginX = timeToX(range.begin).toFloat()
                    val lastBeginX = timeToX(range.lastBegin).toFloat()
                    val endX = timeToX(range.end).toFloat()

                    val points = mutableListOf<Array<Float>>()
                    points.add(arrayOf(beginX, 0.0f))
                    points.add(arrayOf(beginX, 1.0f))
                    points.add(arrayOf(lastBeginX, 1.0f))
                    points.add(arrayOf(endX, 0.0f))

                    var i = 0
                    while (i != points.size) {
                        val prev = if (i == 0) points.last() else points[i - 1]
                        val cur = points[i]
                        if (abs(prev[0] - cur[0]) < 1 && abs(prev[1] - cur[1]) < 0.001) {
                            points.removeAt(i)
                        } else {
                            i++
                        }
                    }

                    if (points.size < 3) continue

                    val path = Path().apply {
                        for ((i, point) in points.withIndex()) {
                            val y = lerp(graphBottom, padding * 0.5f, point[1])
                            if(i == 0) {
                                moveTo(point[0], y)
                            }
                            else {
                                lineTo(point[0], y)
                            }
                        }
                    }

                    drawPath(path, SolidColor(Color(255, 140, 0)))
                }

                if(graphData.fallAsleep != null) {
                    val x = timeToX(graphData.fallAsleep).toFloat()

                    drawLine(
                        SolidColor(Color.Blue),
                        Offset(x, padding * 0.5f),
                        Offset(x, graphBottom),
                        lineWidth
                    )

                    val text = graphData.fallAsleep.atZone(currentTimezone).toLocalTime()
                        .truncatedTo(ChronoUnit.MINUTES).toString()

                    val textMeasure = textMeasurer.measure(text, TextStyle(fontSize = tickFontSize))
                    drawText(
                        textMeasure,
                        Color.Blue,
                        Offset(
                            max(padding * 0.5f, x - textMeasure.size.width * 0.5f),
                            sleepTimeY - tickFontSizePx,
                        ),
                    )
                }
                if(graphData.wakeUp != null) {
                    val x = timeToX(graphData.wakeUp).toFloat()

                    drawLine(
                        SolidColor(Color.Red),
                        Offset(x, padding * 0.5f),
                        Offset(x, graphBottom),
                        lineWidth
                    )

                    val text = graphData.wakeUp.atZone(currentTimezone).toLocalTime()
                        .truncatedTo(ChronoUnit.MINUTES).toString()

                    val textMeasure = textMeasurer.measure(text, TextStyle(fontSize = tickFontSize))
                    drawText(
                        textMeasure,
                        Color.Red,
                        Offset(
                            min(width - padding * 0.5f, x + textMeasure.size.width * 0.5f) - textMeasure.size.width,
                            sleepTimeY - tickFontSizePx,
                        ),
                    )
                }
            }

            Text("Кол-во ночных пробуждений: " + info.interruptionCount)
            Text("Время сна: " + durationToHHMM(info.totalSleepDuration))
            Text("Время лежания в кровати: " + durationToHHMM(info.durationBeforeFallingAsleep))
            Text("Сонный долг: " + durationToHHMM(info.sleepBalance))

            Row(Modifier.weight(1f)) {}

            Button({ db?.deleteSleepPeriod(info.periodId); close() }, Modifier.fillMaxWidth()) {
                Text("Удалить")
            }
        }
    }
}

@Composable
fun CellarScreen(animatable: Float, toBedroom: () -> Unit) {
    val db = DbContext.current
    val sleepPeriods = remember(key1 = db?.sleepPeriodsVersion?.value) { db?.getAllSleepPeriodData() ?: listOf() }
    val currentTimezone = getCurrentTime().zone

    val detailsToShowS = remember { mutableStateOf<SavedSleepPeriodData?>(null) }
    val detailsToShow = detailsToShowS.value

    if(detailsToShow != null) {
        BackHandler {
            detailsToShowS.value = null
        }
        SleepPeriodDetailsScreen(detailsToShow, {
            detailsToShowS.value = null
        })
        return
    }

    Screen(
        Modifier
            .offset(y = (animatable * LocalConfiguration.current.screenHeightDp.dp.value).dp)
            .background(Color.Gray)
    ) {
        Column {
            Button({ toBedroom() }, Modifier.fillMaxWidth()) {
                Text("В спалню")
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(sleepPeriods.size) { index ->
                    CellarSleepPeriodListItem(
                        Modifier,
                        sleepPeriods[index],
                        currentTimezone,
                        index != 0,
                        { info -> detailsToShowS.value = info }
                    )
                }
            }
        }
    }
}

@Composable
fun Main(showQualityDialogForS: MutableState<Int?>) {
    val scope = rememberCoroutineScope()
    val db = DbContext.current

    val bedroomAnimatableS = remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(Animatable(0.0f)) }
    val cellarAnimatableS = remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }

    val bedroomAnimatable = bedroomAnimatableS.value
    val cellarAnimatable = cellarAnimatableS.value

    val showQualityDialogFor = showQualityDialogForS.value

    if(showQualityDialogFor != null) {
        fun selectQuality(quality: Int) {
            db?.setSleepQuality(showQualityDialogFor, quality)
            showQualityDialogForS.value = null
        }

        Dialog(
            onDismissRequest = { selectQuality(0) },
        ) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp,) {
                Column(Modifier.padding(10.dp)) {
                    Text("Как спалось?", Modifier.align(Alignment.CenterHorizontally))
                    Button({ selectQuality(5) }, Modifier.fillMaxWidth()) { Text(qualityToString(5)) }
                    Button({ selectQuality(4) }, Modifier.fillMaxWidth()) { Text(qualityToString(4)) }
                    Button({ selectQuality(3) }, Modifier.fillMaxWidth()) { Text(qualityToString(3)) }
                    Button({ selectQuality(2) }, Modifier.fillMaxWidth()) { Text(qualityToString(2)) }
                    Button({ selectQuality(1) }, Modifier.fillMaxWidth()) { Text(qualityToString(1)) }
                }
            }
        }
    }

    if(bedroomAnimatable != null) {
        BedroomScreen(
            bedroomAnimatable.value,
            toCellar = {
                scope.launch {
                    val ba = Animatable(0.0f)
                    val ca = Animatable(1.0f)
                    bedroomAnimatableS.value = ba
                    cellarAnimatableS.value = ca

                    coroutineScope {
                        launch {
                            ba.animateTo(-1.0f, tween(500))
                        }
                        launch {
                            ca.animateTo(0.0f, tween(500))
                        }
                    }

                    bedroomAnimatableS.value = null
                }
            }
        )
    }

    if(cellarAnimatable != null) {
        CellarScreen(
            cellarAnimatable.value,
            toBedroom = {
                scope.launch {
                    val ba = Animatable(-1.0f)
                    val ca = Animatable(0.0f)
                    bedroomAnimatableS.value = ba
                    cellarAnimatableS.value = ca

                    coroutineScope {
                        launch {
                            ba.animateTo(0f, tween(500))
                        }
                        launch {
                            ca.animateTo(1f, tween(500))
                        }
                    }

                    cellarAnimatableS.value = null
                }
            }
        )
    }
}

fun getCurrentTime(): ZonedDateTime {
    return MainActivityTracker.resumedActivity?.overrideTimeS?.value ?: ZonedDateTime.now()
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