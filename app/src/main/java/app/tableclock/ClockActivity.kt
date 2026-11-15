package app.tableclock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

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
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }

    var battery by remember { mutableIntStateOf(-1) }
    var wireless by remember { mutableStateOf(false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                battery = if (level >= 0) level * 100 / scale else -1
                wireless = PhoneSignals.isWireless(intent)
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) receiver.onReceive(context, sticky)
        onDispose { context.unregisterReceiver(receiver) }
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
    var hourMode by remember { mutableStateOf(prefs.hourMode) }
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
                hourMode = prefs.hourMode
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

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { controls = !controls }
            },
        contentAlignment = Alignment.Center,
    ) {
        val scene = sceneNow(weather?.code, now.hour)
        val accent = scene.accent()
        val use24 = when (hourMode) {
            HourMode.System -> is24
            HourMode.Twelve -> false
            HourMode.TwentyFour -> true
        }
        ClockBoard(
            now = now,
            face = face,
            classic = classic,
            digitalStyle = digitalStyle,
            showSeconds = showSeconds,
            showDate = showDate,
            use24 = use24,
            scene = scene,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 40.dp),
            weather = {
                Column {
                    if (showWeather) {
                        WeatherLine(
                            weather = weather,
                            scene = scene,
                            fahrenheit = fahrenheit,
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
                    if (battery >= 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = if (wireless) "$battery%  ·  wireless charging" else "$battery%",
                            color = if (wireless) accent.copy(alpha = 0.9f) else Cream.copy(alpha = 0.32f),
                            fontSize = 15.sp,
                            letterSpacing = 0.04.em,
                        )
                    }
                }
            },
        )

        AnimatedVisibility(
            visible = controls,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 12.dp),
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
    locationOn: Boolean,
    onAllowLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    when {
        weather != null -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WeatherMark(scene, Modifier.size(42.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "${formatTemperature(weather.tempC, fahrenheit)}   ${weatherLabel(weather.code)}",
                        color = Cream,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Light,
                    )
                    Text(
                        text = "${formatTemperature(weather.highC, fahrenheit)}  /  ${formatTemperature(weather.lowC, fahrenheit)}",
                        color = Mute,
                        fontSize = 15.sp,
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
