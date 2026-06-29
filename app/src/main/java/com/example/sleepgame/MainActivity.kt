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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toKotlinDuration

object MainActivityTracker : ActivityTracker<MainActivity>()


class MainActivity: ComponentActivity() {
    var overrideTimeS = mutableStateOf<ZonedDateTime?>(null)

    val showQualityDialogForS = mutableStateOf<Int?>(null)

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

        MainActivityTracker.attach(this)

        setContent {
            Main(showQualityDialogForS)
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
    val context = LocalContext.current

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

            // Click areas

            Box(
                Modifier
                    .freePosition(0f, safeSize.y * 0.7f, safeSize.x * 0.65f, safeSize.y * 0.15f)
                    .clickable {
                        Database(context).startSleepPeriod(Database.SleepRecordInput(
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
                        Database(context).endSleepPeriod(Database.SleepRecordInput(
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
    val activity = MainActivityTracker.resumedActivity!!

    val overrideTime = activity.overrideTimeS.value

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
        activity.overrideTimeS.value = parsedTime
    }
    else {
        activity.overrideTimeS.value = null
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

    SleepPeriodDetailsScreenDisplay(info, records)
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

                val qualityText = when (info.quality) {
                    1 -> "Не спал"
                    2 -> "Ужасно"
                    3 -> "Не очень"
                    4 -> "Не идеально"
                    5 -> "Замечательно"
                    else -> "Не записано"
                }
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

@Composable
fun SleepPeriodDetailsScreen(info: SavedSleepPeriodData) {
    val context = LocalContext.current

    val records = remember {
        val db = Database(context)

        val records = db.getAllRecordsForPeriod(info.periodId)
        records.sortWith { a, b -> a.recordedTime.compareTo(b.recordedTime) }

        records
    }

    SleepPeriodDetailsScreenDisplay(info, records)
}

@Composable
fun SleepPeriodDetailsScreenDisplay(info: SavedSleepPeriodData, records: List<SleepRecord>) {
    val TAG = "SleepPeriodDetailsScreenDisplay"

    val context = LocalContext.current
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

            Button({ Database(context).deleteSleepPeriod(info.periodId) }, Modifier.fillMaxWidth()) {
                Text("Удалить")
            }
        }
    }
}

@Composable
fun CellarScreen(animatable: Float, toBedroom: () -> Unit) {
    val context = LocalContext.current
    val sleepPeriods = remember { Database(context).getAllSleepPeriodData() }
    val currentTimezone = getCurrentTime().zone

    val detailsToShowS = remember { mutableStateOf<SavedSleepPeriodData?>(null) }
    val detailsToShow = detailsToShowS.value

    if(detailsToShow != null) {
        BackHandler {
            detailsToShowS.value = null
        }
        SleepPeriodDetailsScreen(detailsToShow)
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val bedroomAnimatableS = remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(Animatable(0.0f)) }
    val cellarAnimatableS = remember { mutableStateOf<Animatable<Float, AnimationVector1D>?>(null) }

    val bedroomAnimatable = bedroomAnimatableS.value
    val cellarAnimatable = cellarAnimatableS.value

    val showQualityDialogFor = showQualityDialogForS.value

    if(showQualityDialogFor != null) {
        fun selectQuality(quality: Int) {
            val db = Database(context)
            db.setSleepQuality(showQualityDialogFor, quality)
            showQualityDialogForS.value = null
        }

        Dialog(
            onDismissRequest = { selectQuality(0) },
        ) {
            Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 8.dp,) {
                Column(Modifier.padding(10.dp)) {
                    Text("Как спалось?", Modifier.align(Alignment.CenterHorizontally))
                    Button({ selectQuality(5) }, Modifier.fillMaxWidth()) { Text("Замечательно") }
                    Button({ selectQuality(4) }, Modifier.fillMaxWidth()) { Text("Не идеально") }
                    Button({ selectQuality(3) }, Modifier.fillMaxWidth()) { Text("Не очень") }
                    Button({ selectQuality(2) }, Modifier.fillMaxWidth()) { Text("Ужасно") }
                    Button({ selectQuality(1) }, Modifier.fillMaxWidth()) { Text("Не спал") }
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
        for(rangeI in 1 until (graphData.nonSleepRanges.size - 1)) {
            val range = graphData.nonSleepRanges[rangeI]

            val beginX = timeToX(range.begin)
            val lastBeginX = timeToX(range.lastBegin)
            val endX = timeToX(range.end)

            val points = mutableListOf<Array<Double>>()
            points.add(arrayOf(beginX, 0.0))
            points.add(arrayOf(beginX, 1.0))
            points.add(arrayOf(lastBeginX, 1.0))
            points.add(arrayOf(endX, 0.0))

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

        if(graphData.fallAsleep != null) {
            result["fall_asleep_position"] = timeToX(graphData.fallAsleep)
            result["fall_asleep_label"] = graphData.fallAsleep.atZone(currentTimezone).toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES).toString()
        }
        if(graphData.wakeUp != null) {
            result["wake_up_position"] = timeToX(graphData.wakeUp)
            result["wake_up_label"] = graphData.wakeUp.atZone(currentTimezone).toLocalTime()
                .truncatedTo(ChronoUnit.MINUTES).toString()
        }

        Log.d(TAG, "done")
        return result
    }

    @UsedByGodot
    fun getLastCompletePeriodStats(): Dictionary? {
        val db = Database(context)
        val lastCompletedPeriod = db.getLatestSleepPeriodData() ?: return null
        return makeSleepPeriodDataDict(lastCompletedPeriod)
    }

    @UsedByGodot
    fun getSleepDataVersion(): Long {
        return Database(context).getSleepDataVersion()
    }

    @UsedByGodot
    fun getAllPeriodsStats(): Array<Dictionary> {
        val db = Database(context)
        return db.getAllSleepPeriodData().map { makeSleepPeriodDataDict(it) }.toTypedArray()
    }

    @UsedByGodot
    fun clickBed() {
        val db = Database(context)
        db.startSleepPeriod(Database.SleepRecordInput(getCurrentTime(), defaultTimeToFallAsleepMinutes, defaultMinimumSleepDurationMinutes))
        sleepControlsUpdate(activity ?: context)
    }
    @UsedByGodot
    fun clickAlarmClock() {
        val db = Database(context)
        db.endSleepPeriod(Database.SleepRecordInput(getCurrentTime(), defaultTimeToFallAsleepMinutes, defaultMinimumSleepDurationMinutes))
        sleepControlsUpdate(activity ?: context)
    }
    @UsedByGodot
    fun deleteSleepPeriod(periodId: Int) {
        val db = Database(context)
        db.deleteSleepPeriod(periodId)
    }
    @UsedByGodot
    fun resetSleepBalance() {
        val db = Database(context)
        db.resetSleepBalance()
    }

    @UsedByGodot
    fun getSettings(): String {
        val result = JSONObject()

        result.put("timeToFallAsleep", 15)
        result.put("timeToFallAsleepAfterInterruption", 10)
        result.put("normalSleepDuration", 8)
        result.put("sleepButtonsOrder", JSONArray(arrayOf(1, 2, 3)))
        result.put("sleepButtonsSize", JSONArray(arrayOf(20, 60, 20)))
        result.put("sleepNotificationPeriod", "")
        result.put("showSleepNotification", true)
        result.put("sleepNotificationSound", true)
        result.put("sleepNotificationVibration", true)

        return result.toString()
    }
    fun setSettings(newSettingsJson: String): Boolean {
        return true
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

    activity.runOnUiThread {
        val fm = activity.supportFragmentManager

        val oldDialog = fm.findFragmentByTag(SleepQualityDialogFragment.TAG) as? DialogFragment
        oldDialog?.dismiss()
        fm.executePendingTransactions()

        SleepQualityDialogFragment(periodId)
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
 */