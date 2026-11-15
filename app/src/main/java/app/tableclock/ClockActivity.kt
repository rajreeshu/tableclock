package app.tableclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

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
    val locale = remember { java.util.Locale.getDefault() }
    val timeFormat = remember(is24) {
        DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h:mm", locale)
    }
    val secondFormat = remember { DateTimeFormatter.ofPattern("ss", locale) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale) }
    val periodFormat = remember { DateTimeFormatter.ofPattern("a", locale) }

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

    var controls by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(brightness) }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { controls = !controls }
            },
        contentAlignment = Alignment.Center,
    ) {
        val timeSize = min(maxWidth.value / 4.4f, maxHeight.value * 0.42f)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = now.format(timeFormat),
                    color = Cream,
                    fontSize = timeSize.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.045).em,
                    maxLines = 1,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier.padding(bottom = (timeSize * 0.07f).dp),
                    verticalArrangement = Arrangement.Bottom,
                ) {
                    if (!is24) {
                        Text(
                            text = now.format(periodFormat),
                            color = Cream.copy(alpha = 0.55f),
                            fontSize = (timeSize * 0.16f).sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.08.em,
                        )
                    }
                    Text(
                        text = now.format(secondFormat),
                        color = Cream.copy(alpha = 0.42f),
                        fontSize = (timeSize * 0.22f).sp,
                        fontWeight = FontWeight.Light,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = now.format(dateFormat),
                color = Cream.copy(alpha = 0.62f),
                fontSize = 20.sp,
                letterSpacing = 0.02.em,
                textAlign = TextAlign.Center,
            )
            if (battery >= 0) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (wireless) "$battery%  ·  wireless charging" else "$battery%",
                    color = if (wireless) Amber.copy(alpha = 0.85f) else Cream.copy(alpha = 0.32f),
                    fontSize = 15.sp,
                    letterSpacing = 0.04.em,
                )
            }
        }

        AnimatedVisibility(
            visible = controls,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 36.dp, vertical = 16.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
