package app.tableclock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import kotlin.math.roundToInt

class ClockActivity : ComponentActivity() {
    private val prefs by lazy { Prefs(this) }
    private var closedByDock = false

    private val dismiss = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            closedByDock = true
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        applyBrightness(prefs.brightness)
        ContextCompat.registerReceiver(
            this,
            dismiss,
            IntentFilter(ACTION_DISMISS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        setContent {
            TableClockTheme {
                ClockScreen(
                    brightness = prefs.brightness,
                    onBrightness = { value ->
                        prefs.brightness = value
                        applyBrightness(value)
                    },
                    onClose = { finish() },
                )
            }
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(dismiss) }
        if (isFinishing && !closedByDock) {
            sendBroadcast(Intent(DockService.ACTION_USER_CLOSED).setPackage(packageName))
        }
        super.onDestroy()
    }

    private fun applyBrightness(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }

    companion object {
        const val ACTION_DISMISS = "app.tableclock.DISMISS"
    }
}

@Composable
private fun ClockScreen(
    brightness: Float,
    onBrightness: (Float) -> Unit,
    onClose: () -> Unit,
) {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as ComponentActivity).window
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, view)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            tick++
            delay(250L)
        }
    }
    val phoneName = remember { thisPhoneLabel() }
    val phoneKind = remember(phoneName) { thisPhoneKind(phoneName) }
    var draftMinutes by remember { mutableFloatStateOf(0f) }
    var timerEndsAt by remember { mutableLongStateOf(0L) }
    var timerSpan by remember { mutableLongStateOf(0L) }
    var timerDone by remember { mutableStateOf(false) }
    val timerLeft = remember(tick, timerEndsAt) {
        val leftMs = timerEndsAt - System.currentTimeMillis()
        if (timerEndsAt == 0L || leftMs <= 0L) 0 else (leftMs / 1000L).toInt()
    }
    val timerRunning = timerLeft > 0
    val timerFraction = if (timerRunning && timerSpan > 0L) {
        (timerLeft * 1000f / timerSpan.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val alarm = remember { timerRingtone(context) }
    DisposableEffect(alarm) {
        onDispose { alarm?.stop() }
    }
    LaunchedEffect(timerDone) {
        if (!timerDone) {
            alarm?.stop()
            return@LaunchedEffect
        }
        val audio = context.getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (audio.getStreamVolume(AudioManager.STREAM_ALARM) < max / 3) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, max / 2, 0)
        }
        alarm?.play()
    }
    LaunchedEffect(timerEndsAt) {
        val ends = timerEndsAt
        if (ends == 0L) return@LaunchedEffect
        val wait = ends - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        if (timerEndsAt != ends) return@LaunchedEffect
        timerEndsAt = 0L
        timerDone = true
    }

    val prefs = remember { Prefs(context) }
    var controls by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(brightness) }
    var face by remember { mutableStateOf(prefs.face) }
    var classic by remember { mutableStateOf(prefs.classic) }
    var digitalStyle by remember { mutableStateOf(prefs.digitalStyle) }
    var showSeconds by remember { mutableStateOf(prefs.showSeconds) }
    var showDate by remember { mutableStateOf(prefs.showDate) }
    var showWeather by remember { mutableStateOf(prefs.showWeather) }
    var showAlarm by remember { mutableStateOf(prefs.showAlarm) }
    var showPhone by remember { mutableStateOf(prefs.showPhone) }
    var hourMode by remember { mutableStateOf(prefs.hourMode) }
    var classicFit by remember { mutableFloatStateOf(prefs.classicFit) }
    var digitalFit by remember { mutableFloatStateOf(prefs.digitalFit) }
    var dateFit by remember { mutableFloatStateOf(prefs.dateFit) }
    var weatherFit by remember { mutableFloatStateOf(prefs.weatherFit) }
    var sideFit by remember { mutableFloatStateOf(prefs.sideFit) }
    DisposableEffect(context) {
        val activity = context as ComponentActivity
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                face = prefs.face
                classic = prefs.classic
                digitalStyle = prefs.digitalStyle
                showSeconds = prefs.showSeconds
                showDate = prefs.showDate
                showWeather = prefs.showWeather
                showAlarm = prefs.showAlarm
                showPhone = prefs.showPhone
                hourMode = prefs.hourMode
                classicFit = prefs.classicFit
                digitalFit = prefs.digitalFit
                dateFit = prefs.dateFit
                weatherFit = prefs.weatherFit
                sideFit = prefs.sideFit
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    val fahrenheit = remember { prefersFahrenheit(java.util.Locale.getDefault().country) }
    var locationOn by remember { mutableStateOf(WeatherLoader.hasLocationPermission(context)) }
    var weather by remember { mutableStateOf(Prefs(context).weather) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        locationOn = granted
    }
    LaunchedEffect(Unit) {
        if (!locationOn) locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    LaunchedEffect(locationOn) {
        if (!locationOn) return@LaunchedEffect
        while (true) {
            val report = withContext(Dispatchers.IO) { WeatherLoader.load(context) }
            if (report != null) weather = report
            delay(30 * 60 * 1000L)
        }
    }

    val showRail = controls || timerRunning || timerDone
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val scene = sceneNow(weather?.code, now.hour)
        val use24 = when (hourMode) {
            HourMode.System -> is24
            HourMode.Twelve -> false
            HourMode.TwentyFour -> true
        }
        val alarmAt = rememberNextAlarmMillis()
        val phone = rememberPhoneStatus()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 72.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (timerDone) {
                            timerDone = false
                            alarm?.stop()
                        }
                        controls = !controls
                        if (controls && timerRunning) {
                            draftMinutes = timerLeft / 60f
                        }
                    }
                },
        ) {
        ClockBoard(
            now = now,
            face = face,
            classic = classic,
            digitalStyle = digitalStyle,
            showSeconds = showSeconds,
            showDate = showDate,
            use24 = use24,
            scene = scene,
            classicFit = classicFit,
            digitalFit = digitalFit,
            dateFit = dateFit,
            margins = showAlarm || showPhone,
            marginFit = sideFit,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(
                    bottom = if (controls) 96.dp else 20.dp,
                    end = if (showRail) 56.dp else 0.dp,
                ),
            leading = {
                if (showAlarm) {
                    MarginNote(
                        MarginKind.Alarm,
                        alarmGlance(alarmAt, now, use24),
                        alignEnd = false,
                        scale = sideFit,
                    )
                }
            },
            trailing = {
                if (showPhone) {
                    MarginNote(
                        MarginKind.Heat,
                        phoneGlance(phone, fahrenheit),
                        alignEnd = true,
                        scale = sideFit,
                    )
                }
            },
            weather = {
                Column {
                    if (showWeather) {
                        WeatherLine(
                            weather = weather,
                            scene = scene,
                            fahrenheit = fahrenheit,
                            scale = weatherFit,
                            locationOn = locationOn,
                            onAllowLocation = {
                                if (locationOn) return@WeatherLine
                                locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            },
                            onOpenSettings = {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.parse("package:${context.packageName}"),
                                    ),
                                )
                            },
                        )
                    }
                }
            },
        )
        }

        if (!controls && !timerRunning && !timerDone) {
            PhoneSignature(
                label = phoneName,
                kind = phoneKind,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, bottom = 16.dp),
            )
        }
        if (!controls && (timerRunning || timerDone)) {
            Text(
                text = if (timerDone) "Time's up" else formatTimer(timerLeft),
                color = if (timerDone) Amber else Cream,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }

        if (showRail) {
            TimerRail(
                fraction = timerFraction,
                label = when {
                    timerDone -> "Done"
                    timerRunning -> formatTimer(timerLeft)
                    draftMinutes.roundToInt() <= 0 -> "Timer"
                    else -> "${draftMinutes.roundToInt()} min"
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp, bottom = if (controls) 108.dp else 36.dp),
                onDrag = { next ->
                    draftMinutes = next * TIMER_MAX
                },
                onCommit = {
                    val minutes = draftMinutes.roundToInt()
                    timerDone = false
                    alarm?.stop()
                    if (minutes <= 0) {
                        timerEndsAt = 0L
                        timerSpan = 0L
                    } else {
                        val span = minutes * 60_000L
                        timerSpan = span
                        timerEndsAt = System.currentTimeMillis() + span
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = controls,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 28.dp, end = 78.dp, bottom = 8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Brightness", color = Mute, fontSize = 13.sp)
                Slider(
                    value = level,
                    onValueChange = {
                        level = it
                        onBrightness(it)
                    },
                    valueRange = 0.04f..0.7f,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onClose) {
                    Text("Close", color = Cream)
                }
            }
        }
    }
}

@Composable
private fun WeatherLine(
    weather: WeatherReport?,
    scene: Scene,
    fahrenheit: Boolean,
    scale: Float,
    locationOn: Boolean,
    onAllowLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when {
        weather != null -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherMark(scene, Modifier.size((42f * scale).dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${formatTemperature(weather.tempC, fahrenheit)}   ${weatherLabel(weather.code)}",
                        color = Cream,
                        fontSize = (24f * scale).sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        text = "${formatTemperature(weather.highC, fahrenheit)}  /  ${formatTemperature(weather.lowC, fahrenheit)}",
                        color = Mute,
                        fontSize = (15f * scale).sp,
                    )
                }
            }
        }
        !locationOn -> {
            TextButton(onClick = onAllowLocation) {
                Text("Allow location for weather", color = Amber)
            }
            TextButton(onClick = onOpenSettings) {
                Text("Or open settings", color = Mute, fontSize = 13.sp)
            }
        }
        else -> {
            Text("Fetching weather…", color = Mute, fontSize = 16.sp)
        }
    }
}

@Composable
private fun TimerRail(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier,
    onDrag: (Float) -> Unit,
    onCommit: () -> Unit,
) {
    val dragNow = rememberUpdatedState(onDrag)
    val commitNow = rememberUpdatedState(onCommit)
    var dragging by remember { mutableStateOf(false) }
    var dragged by remember { mutableFloatStateOf(fraction) }
    val level = (if (dragging) dragged else fraction).coerceIn(0f, 1f)
    Column(
        modifier.zIndex(2f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = Cream.copy(alpha = 0.86f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(44.dp)
                .height(230.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            val next = (1f - offset.y / size.height).coerceIn(0f, 1f)
                            dragged = next
                            dragNow.value(next)
                        },
                        onDragEnd = {
                            dragging = false
                            commitNow.value()
                        },
                        onDragCancel = {
                            dragging = false
                            commitNow.value()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val next = (1f - change.position.y / size.height).coerceIn(0f, 1f)
                            dragged = next
                            dragNow.value(next)
                        },
                    )
                },
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Cream.copy(alpha = 0.2f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .width(3.dp)
                    .fillMaxHeight(level.coerceAtLeast(0.02f))
                    .clip(CircleShape)
                    .background(Cream.copy(alpha = 0.9f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxHeight(level.coerceIn(0.04f, 1f)),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(Cream),
                )
            }
        }
    }
}

private fun formatTimer(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun timerRingtone(context: Context): Ringtone? {
    val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: return null
    return RingtoneManager.getRingtone(context, uri)?.apply {
        audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        isLooping = true
    }
}

private const val TIMER_MAX = 90f
