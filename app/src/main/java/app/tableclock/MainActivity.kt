package app.tableclock

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val prefs = Prefs(this)
        setContent {
            TableClockTheme {
                AppRoot(prefs)
            }
        }
    }
}

private enum class Page { Home, Calibrate }

private enum class Grant { Notifications, Overlay, FullScreen, Battery, Location }

private data class Grants(
    val notifications: Boolean,
    val overlay: Boolean,
    val fullScreen: Boolean,
    val battery: Boolean,
    val location: Boolean,
)

@Composable
private fun AppRoot(prefs: Prefs) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val readingState = remember { mutableStateOf<PhoneReading?>(null) }
    var hasMagnetometer by remember { mutableStateOf(true) }
    var page by remember { mutableStateOf(Page.Home) }
    var enabled by remember { mutableStateOf(prefs.enabled) }
    var calibration by remember { mutableStateOf(prefs.calibration) }
    var brightness by remember { mutableFloatStateOf(prefs.brightness) }
    var face by remember { mutableStateOf(prefs.face) }
    var classic by remember { mutableStateOf(prefs.classic) }
    var digitalStyle by remember { mutableStateOf(prefs.digitalStyle) }
    var showSeconds by remember { mutableStateOf(prefs.showSeconds) }
    var showDate by remember { mutableStateOf(prefs.showDate) }
    var showWeather by remember { mutableStateOf(prefs.showWeather) }
    var hourMode by remember { mutableStateOf(prefs.hourMode) }
    var notice by remember { mutableStateOf<String?>(null) }
    var resumeTick by remember { mutableIntStateOf(0) }
    var armWhenAllowed by remember { mutableStateOf(false) }

    DisposableEffect(activity) {
        val signals = PhoneSignals(activity)
        hasMagnetometer = signals.hasMagnetometer
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> signals.start { reading ->
                    activity.runOnUiThread { readingState.value = reading }
                }
                Lifecycle.Event.ON_STOP -> signals.stop()
                Lifecycle.Event.ON_RESUME -> {
                    face = prefs.face
                    classic = prefs.classic
                    digitalStyle = prefs.digitalStyle
                    showSeconds = prefs.showSeconds
                    showDate = prefs.showDate
                    showWeather = prefs.showWeather
                    hourMode = prefs.hourMode
                    resumeTick++
                }
                else -> Unit
            }
        }
        activity.lifecycle.addObserver(observer)
        if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            signals.start { reading ->
                activity.runOnUiThread { readingState.value = reading }
            }
        }
        onDispose {
            activity.lifecycle.removeObserver(observer)
            signals.stop()
        }
    }

    LaunchedEffect(Unit) {
        if (prefs.enabled && !startListening(context)) {
            enabled = false
            notice = "Couldn't start listening. Allow notifications, then switch it on again."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && armWhenAllowed) {
            if (startListening(context)) {
                enabled = true
                prefs.enabled = true
            } else {
                enabled = false
                notice = "Couldn't start listening. Allow notifications, then switch it on again."
            }
        }
        armWhenAllowed = false
        resumeTick++
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { resumeTick++ }

    val grants = remember(resumeTick) { readGrants(context) }

    when (page) {
        Page.Home -> HomeScreen(
            reading = readingState.value,
            hasMagnetometer = hasMagnetometer,
            enabled = enabled,
            calibration = calibration,
            brightness = brightness,
            face = face,
            classic = classic,
            digitalStyle = digitalStyle,
            showSeconds = showSeconds,
            showDate = showDate,
            showWeather = showWeather,
            hourMode = hourMode,
            previewWeather = prefs.weather,
            grants = grants,
            notice = notice,
            onEnabled = { on ->
                notice = null
                if (!on) {
                    enabled = false
                    prefs.enabled = false
                    runCatching { DockService.stop(context) }
                } else if (!grants.notifications) {
                    armWhenAllowed = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else if (startListening(context)) {
                    enabled = true
                    prefs.enabled = true
                } else {
                    enabled = false
                    notice = "Couldn't start listening. Allow notifications, then switch it on again."
                }
            },
            onBrightness = {
                brightness = it
                prefs.brightness = it
            },
            onFace = {
                face = it
                prefs.face = it
            },
            onClassic = {
                classic = it
                prefs.classic = it
            },
            onDigitalStyle = {
                digitalStyle = it
                prefs.digitalStyle = it
            },
            onShowSeconds = {
                showSeconds = it
                prefs.showSeconds = it
            },
            onShowDate = {
                showDate = it
                prefs.showDate = it
            },
            onShowWeather = {
                showWeather = it
                prefs.showWeather = it
            },
            onHourMode = {
                hourMode = it
                prefs.hourMode = it
            },
            onCalibrate = { page = Page.Calibrate },
            onPreview = {
                context.startActivity(Intent(context, ClockActivity::class.java))
            },
            onGrant = { grant ->
                when (grant) {
                    Grant.Notifications -> {
                        armWhenAllowed = false
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    Grant.Overlay -> openSettings(
                        context,
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                    Grant.FullScreen -> openSettings(
                        context,
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                    Grant.Battery -> openSettings(
                        context,
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                    Grant.Location -> locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            },
        )
        Page.Calibrate -> CalibrateScreen(
            readingState = readingState,
            onClose = { page = Page.Home },
            onSave = { saved ->
                prefs.calibration = saved
                calibration = saved
                if (prefs.enabled) DockService.reload(context)
                page = Page.Home
            },
        )
    }
}

private fun startListening(context: Context): Boolean {
    return try {
        DockService.start(context)
        true
    } catch (_: Exception) {
        Prefs(context).enabled = false
        false
    }
}

private fun openSettings(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        })
    }
}

private fun readGrants(context: Context): Grants {
    val notifications = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    val overlay = Settings.canDrawOverlays(context)
    val fullScreen = context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    val battery = context.getSystemService(PowerManager::class.java)
        .isIgnoringBatteryOptimizations(context.packageName)
    val location = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    return Grants(notifications, overlay, fullScreen, battery, location)
}

@Composable
private fun HomeScreen(
    reading: PhoneReading?,
    hasMagnetometer: Boolean,
    enabled: Boolean,
    calibration: Calibration,
    brightness: Float,
    face: ClockFace,
    classic: ClassicStyle,
    digitalStyle: DigitalStyle,
    showSeconds: Boolean,
    showDate: Boolean,
    showWeather: Boolean,
    hourMode: HourMode,
    previewWeather: WeatherReport?,
    grants: Grants,
    notice: String?,
    onEnabled: (Boolean) -> Unit,
    onBrightness: (Float) -> Unit,
    onFace: (ClockFace) -> Unit,
    onClassic: (ClassicStyle) -> Unit,
    onDigitalStyle: (DigitalStyle) -> Unit,
    onShowSeconds: (Boolean) -> Unit,
    onShowDate: (Boolean) -> Unit,
    onShowWeather: (Boolean) -> Unit,
    onHourMode: (HourMode) -> Unit,
    onCalibrate: () -> Unit,
    onPreview: () -> Unit,
    onGrant: (Grant) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            text = "Table Clock",
            color = Cream,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.03).sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Lay the phone on the magnetic charger, on its side. The screen becomes a clock, and it goes away when you pick the phone up.",
            color = Mute,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Listen for the charger", color = Cream, fontSize = 18.sp)
                Text(
                    text = if (enabled) "It will open the clock on its own" else "Off",
                    color = Mute,
                    fontSize = 14.sp,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabled)
        }
        if (notice != null) {
            Spacer(Modifier.height(10.dp))
            Text(notice, color = Amber, fontSize = 14.sp)
        }
        Spacer(Modifier.height(22.dp))
        SignalLine(reading, hasMagnetometer, calibration)
        Spacer(Modifier.height(28.dp))
        Text("Magnets", color = Cream, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = magnetSummary(hasMagnetometer, calibration),
            color = Mute,
            fontSize = 15.sp,
            lineHeight = 21.sp,
        )
        TextButton(onClick = onCalibrate, modifier = Modifier.padding(start = 0.dp)) {
            Text(if (calibration.usable) "Calibrate again" else "Calibrate", color = Amber)
        }
        Spacer(Modifier.height(8.dp))
        ClockDemo(
            face = face,
            classic = classic,
            digitalStyle = digitalStyle,
            showSeconds = showSeconds,
            showDate = showDate,
            showWeather = showWeather,
            hourMode = hourMode,
            weather = previewWeather,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "The preview uses the same clock as the dock. Weather still comes from the forecast.",
            color = Mute,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        Text("Show", color = Cream, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            options = ClockFace.entries,
            selected = face,
            label = { it.label },
            onSelect = onFace,
        )
        Spacer(Modifier.height(16.dp))
        Text("Classic", color = Cream, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            options = ClassicStyle.entries,
            selected = classic,
            label = { it.label },
            onSelect = onClassic,
        )
        Spacer(Modifier.height(16.dp))
        Text("Digital", color = Cream, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            options = DigitalStyle.entries,
            selected = digitalStyle,
            label = { it.label },
            onSelect = onDigitalStyle,
        )
        Spacer(Modifier.height(16.dp))
        Text("Hours", color = Cream, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        ChoiceChips(
            options = HourMode.entries,
            selected = hourMode,
            label = { it.label },
            onSelect = onHourMode,
        )
        Spacer(Modifier.height(8.dp))
        OptionSwitch("Seconds", showSeconds, onShowSeconds)
        OptionSwitch("Date", showDate, onShowDate)
        OptionSwitch("Weather", showWeather, onShowWeather)
        Spacer(Modifier.height(8.dp))
        Text("Clock brightness", color = Cream, fontSize = 16.sp)
        Slider(
            value = brightness,
            onValueChange = onBrightness,
            valueRange = 0.04f..0.7f,
        )
        Text(
            "Kept low so the screen isn't another source of heat.",
            color = Mute,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Ink),
        ) {
            Text("Preview the clock", fontSize = 16.sp)
        }
        Spacer(Modifier.height(32.dp))
        Text("So it can open on its own", color = Cream, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        GrantRow(
            title = "Notifications",
            detail = "A quiet ongoing notice while it listens",
            granted = grants.notifications,
            onAllow = { onGrant(Grant.Notifications) },
        )
        Hairline()
        GrantRow(
            title = "Show over other apps",
            detail = "Opens the clock the moment the phone lands, even if the screen is on",
            granted = grants.overlay,
            onAllow = { onGrant(Grant.Overlay) },
        )
        Hairline()
        GrantRow(
            title = "Full-screen alerts",
            detail = "Wakes the clock when the screen is off",
            granted = grants.fullScreen,
            onAllow = { onGrant(Grant.FullScreen) },
        )
        Hairline()
        GrantRow(
            title = "Unrestricted battery",
            detail = "Stops Samsung from pausing the listener overnight",
            granted = grants.battery,
            onAllow = { onGrant(Grant.Battery) },
        )
        Hairline()
        GrantRow(
            title = "Location",
            detail = "Approximate location, so the clock can show the weather",
            granted = grants.location,
            onAllow = { onGrant(Grant.Location) },
        )
        if (reading?.wirelessPower == true) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "The pad is powered, so the phone is charging. That is what warms it. Unplug the cable if you only want a stand — after calibration, the magnet is enough.",
                color = Amber.copy(alpha = 0.9f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SignalLine(reading: PhoneReading?, hasMagnetometer: Boolean, calibration: Calibration) {
    val field = when {
        !hasMagnetometer -> "No magnetic sensor"
        reading?.haveField != true -> "Reading the magnetic sensor…"
        else -> "${reading.magnitudeUt.roundToInt()} µT"
    }
    val pose = when (reading?.pose) {
        Pose.Side -> "On its side"
        Pose.Upright -> "Upright"
        Pose.Flat -> if (reading.landscape) "Flat, sideways" else "Flat, upright"
        null -> "—"
    }
    val motion = when {
        reading == null -> ""
        reading.stable -> "Still"
        else -> "Moving"
    }
    val power = if (reading?.wirelessPower == true) "Wireless power" else "No wireless power"
    Text(field, color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Light)
    Spacer(Modifier.height(4.dp))
    Text(
        text = listOf(pose, motion, power).filter { it.isNotEmpty() }.joinToString("  ·  "),
        color = Mute,
        fontSize = 14.sp,
    )
    Spacer(Modifier.height(12.dp))
    val ceiling = maxOf(
        calibration.dockedUt,
        reading?.magnitudeUt ?: 0f,
        80f,
    ).coerceAtLeast(1f)
    val fraction = ((reading?.magnitudeUt ?: 0f) / ceiling).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(Hairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .fillMaxHeight()
                .background(Amber),
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        text = magnetStatus(reading),
        color = Mute,
        fontSize = 13.sp,
    )
}

private fun magnetStatus(reading: PhoneReading?): String {
    if (reading?.haveField != true) {
        return "Lay it flat or on its side. A strong magnet opens the clock without the cable."
    }
    val strong = reading.magnitudeUt >= DockDetector.ABSOLUTE_ENGAGE_UT
    val horizontal = reading.pose != Pose.Upright
    return when {
        strong && horizontal && reading.stable ->
            "Magnet is holding it horizontally. The clock should open."
        strong && !horizontal ->
            "Magnet is strong. Turn the phone flat or on its side."
        horizontal && !strong ->
            "Horizontal, but the magnet is still weak (${reading.magnitudeUt.roundToInt()} µT)."
        else ->
            "A strong magnet, with the phone horizontal, opens the clock. The cable can stay unplugged."
    }
}

private fun magnetSummary(hasMagnetometer: Boolean, calibration: Calibration): String {
    if (!hasMagnetometer) {
        return "This phone has no magnetometer. The clock can still open when the pad is powered and the phone is on its side."
    }
    if (!calibration.usable) {
        return "Optional. A strong magnet already opens the clock when the phone is horizontal, cable or not. Calibrate only if a case magnet sets it off in your hand."
    }
    return "In hand ${calibration.ambientUt.roundToInt()} µT, on the charger ${calibration.dockedUt.roundToInt()} µT."
}

@Composable
private fun GrantRow(title: String, detail: String, granted: Boolean, onAllow: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Cream, fontSize = 16.sp)
            Text(detail, color = Mute, fontSize = 13.sp, lineHeight = 18.sp)
        }
        if (granted) {
            Text("On", color = Amber, fontSize = 14.sp)
        } else {
            TextButton(onClick = onAllow) { Text("Allow", color = Amber) }
        }
    }
}

@Composable
private fun Hairline() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Hairline))
}

@Composable
private fun CalibrateScreen(
    readingState: androidx.compose.runtime.MutableState<PhoneReading?>,
    onClose: () -> Unit,
    onSave: (Calibration) -> Unit,
) {
    var phase by remember { mutableIntStateOf(0) }
    var ambient by remember { mutableStateOf<Float?>(null) }
    var docked by remember { mutableStateOf<Float?>(null) }
    var countdown by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(phase) {
        if (phase != 1 && phase != 3) return@LaunchedEffect
        error = null
        val values = mutableListOf<Float>()
        val started = android.os.SystemClock.elapsedRealtime()
        while (android.os.SystemClock.elapsedRealtime() - started < 2800) {
            val left = ((2800 - (android.os.SystemClock.elapsedRealtime() - started)) / 1000) + 1
            countdown = "Hold still · ${left}s"
            val current = readingState.value
            if (current != null && current.haveField) values.add(current.magnitudeUt)
            delay(80)
        }
        countdown = ""
        if (values.size < 8) {
            error = "The sensor didn't get a steady reading. Hold still, away from other magnets, and try again."
            phase = if (phase == 1) 0 else 2
            return@LaunchedEffect
        }
        val value = median(values)
        if (phase == 1) {
            ambient = value
            phase = 2
        } else {
            docked = value
            phase = 4
        }
    }

    val live = readingState.value
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        TextButton(onClick = onClose) { Text("Back", color = Mute) }
        Text("Calibrate", color = Cream, fontSize = 36.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(8.dp))
        Text(
            text = when (phase) {
                0, 1 -> "Hold the phone in your hand, away from the charger and other magnets."
                2, 3 -> "Lay it on the charger the way you want the clock, and leave it still."
                else -> "Here's the gap between your hand and the charger."
            },
            color = Mute,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = if (live?.haveField == true) "${live.magnitudeUt.roundToInt()} µT" else "Reading…",
            color = Cream,
            fontSize = 42.sp,
            fontWeight = FontWeight.Light,
        )
        if (countdown.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(countdown, color = Amber, fontSize = 16.sp)
        }
        if (ambient != null) {
            Spacer(Modifier.height(8.dp))
            Text("In hand  ${ambient!!.roundToInt()} µT", color = Mute, fontSize = 15.sp)
        }
        if (docked != null) {
            Text("On the charger  ${docked!!.roundToInt()} µT", color = Mute, fontSize = 15.sp)
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error!!, color = Amber, fontSize = 15.sp, lineHeight = 21.sp)
        }
        Spacer(Modifier.height(24.dp))
        when (phase) {
            0 -> CreamButton("Measure in hand") { phase = 1 }
            2 -> CreamButton("Measure on the charger") { phase = 3 }
            4 -> {
                val hand = ambient ?: 0f
                val pad = docked ?: 0f
                val result = Calibration(hand, pad)
                if (result.usable) {
                    Text(
                        "The magnet is clear enough. The clock will open on its side from ${result.engageUt.roundToInt()} µT, cable or no cable.",
                        color = Cream,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    CreamButton("Save") { onSave(result) }
                    TextButton(onClick = {
                        ambient = null
                        docked = null
                        phase = 0
                    }) { Text("Try again", color = Mute) }
                } else {
                    Text(
                        "Those two readings are too close. A thick case, or a charger with a weak magnet, does that. You can still use a powered pad: the clock opens when the phone is on its side and wireless power is there. Or measure again with the phone closer to the magnet.",
                        color = Cream,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(18.dp))
                    CreamButton("Try again") {
                        ambient = null
                        docked = null
                        phase = 0
                    }
                    TextButton(onClick = onClose) { Text("Skip", color = Mute) }
                }
            }
        }
    }
}

@Composable
private fun ClockDemo(
    face: ClockFace,
    classic: ClassicStyle,
    digitalStyle: DigitalStyle,
    showSeconds: Boolean,
    showDate: Boolean,
    showWeather: Boolean,
    hourMode: HourMode,
    weather: WeatherReport?,
) {
    val context = LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000L - (System.currentTimeMillis() % 1000L))
        }
    }
    val use24 = when (hourMode) {
        HourMode.System -> android.text.format.DateFormat.is24HourFormat(context)
        HourMode.Twelve -> false
        HourMode.TwentyFour -> true
    }
    val scene = sceneNow(weather?.code, now.hour)
    val fahrenheit = prefersFahrenheit(java.util.Locale.getDefault().country)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.Black),
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
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            weather = {
                if (showWeather && weather != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WeatherMark(scene, Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "${formatTemperature(weather.tempC, fahrenheit)}  ${weatherLabel(weather.code)}",
                                color = Cream,
                                fontSize = 14.sp,
                            )
                            Text(
                                text = "${formatTemperature(weather.highC, fahrenheit)}  /  ${formatTemperature(weather.lowC, fahrenheit)}",
                                color = Mute,
                                fontSize = 11.sp,
                            )
                        }
                    }
                } else if (showWeather) {
                    Text("Weather shows here", color = Mute, fontSize = 12.sp)
                }
            },
        )
    }
}

@Composable
private fun OptionSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Cream, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun CreamButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Cream, contentColor = Ink),
    ) {
        Text(label, fontSize = 16.sp)
    }
}
