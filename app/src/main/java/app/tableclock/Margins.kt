package app.tableclock

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

data class Glance(
    val value: String,
    val detail: String,
    val warm: Boolean = false,
    val charging: Boolean = false,
)

data class PhoneStatus(
    val percent: Int = -1,
    val celsius: Float? = null,
    val wireless: Boolean = false,
    val charging: Boolean = false,
)

fun alarmGlance(
    triggerMillis: Long?,
    now: LocalDateTime,
    use24: Boolean,
    zone: ZoneId = ZoneId.systemDefault(),
    locale: java.util.Locale = java.util.Locale.getDefault(),
): Glance {
    if (triggerMillis == null) return Glance("None", "")
    val alarm = java.time.Instant.ofEpochMilli(triggerMillis).atZone(zone).toLocalDateTime()
    val pattern = if (use24) "HH:mm" else "h:mm"
    val time = alarm.format(DateTimeFormatter.ofPattern(pattern, locale))
    val minutes = ChronoUnit.MINUTES.between(now, alarm)
    val sameDay = alarm.toLocalDate() == now.toLocalDate()
    val day = when {
        sameDay -> null
        alarm.toLocalDate() == now.toLocalDate().plusDays(1) -> "Tomorrow"
        else -> alarm.format(DateTimeFormatter.ofPattern("EEE", locale))
    }
    val relative = when {
        minutes < 1 -> "Now"
        minutes < 60 -> "in ${minutes}m"
        minutes < 120 -> {
            val leftover = minutes % 60
            if (leftover == 0L) "in ${minutes / 60}h" else "in ${minutes / 60}h ${leftover}m"
        }
        minutes < 24 * 60 -> "in ${minutes / 60}h"
        else -> null
    }
    val period = if (use24) null else alarm.format(DateTimeFormatter.ofPattern("a", locale))
    val detail = listOfNotNull(period, day, relative).joinToString(" · ")
    return Glance(time, detail)
}

fun phoneGlance(
    status: PhoneStatus,
    fahrenheit: Boolean,
): Glance {
    val warm = status.celsius != null && status.celsius >= 40f
    val value = when {
        status.celsius != null -> formatTemperature(status.celsius, fahrenheit)
        status.percent >= 0 -> "${status.percent}%"
        else -> "—"
    }
    val charge = when {
        status.percent < 0 -> ""
        status.celsius != null -> "${status.percent}%"
        else -> ""
    }
    return Glance(value, charge, warm, charging = status.charging)
}

enum class MarginKind { Alarm, Heat }

@Composable
fun MarginNote(
    kind: MarginKind,
    glance: Glance,
    alignEnd: Boolean,
    scale: Float = 1f,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!alignEnd) {
            MarginMark(kind, glance.warm, scale)
            Spacer(Modifier.width(6.dp))
        }
        Column(horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = glance.value,
                    color = if (glance.warm) Amber else Cream.copy(alpha = 0.88f),
                    fontSize = (14f * scale).sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                )
                if (glance.charging && glance.detail.isEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    ChargeMark(scale)
                }
            }
            if (glance.detail.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(glance.detail, color = Mute, fontSize = (10f * scale).sp, maxLines = 1)
                    if (glance.charging) {
                        Spacer(Modifier.width(3.dp))
                        ChargeMark(scale)
                    }
                }
            }
        }
        if (alignEnd) {
            Spacer(Modifier.width(6.dp))
            MarginMark(kind, glance.warm, scale)
        }
    }
}

@Composable
private fun ChargeMark(scale: Float) {
    Canvas(Modifier.size((9f * scale).dp)) { bolt(Cream.copy(alpha = 0.78f)) }
}

@Composable
private fun MarginMark(kind: MarginKind, warm: Boolean, scale: Float) {
    val color = if (warm) Amber else Cream.copy(alpha = 0.78f)
    Canvas(Modifier.size((16f * scale).dp)) {
        when (kind) {
            MarginKind.Alarm -> bell(color)
            MarginKind.Heat -> thermometer(color, warm)
        }
    }
}

private fun DrawScope.bell(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.09f
    drawArc(
        color = color,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.2f, h * 0.08f),
        size = Size(w * 0.6f, h * 0.72f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    drawLine(
        color,
        Offset(w * 0.14f, h * 0.66f),
        Offset(w * 0.86f, h * 0.66f),
        stroke,
        StrokeCap.Round,
    )
    drawCircle(color, w * 0.08f, Offset(w * 0.5f, h * 0.82f))
}

private fun DrawScope.bolt(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.14f
    drawLine(color, Offset(w * 0.68f, h * 0.02f), Offset(w * 0.28f, h * 0.52f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.28f, h * 0.52f), Offset(w * 0.62f, h * 0.52f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.62f, h * 0.52f), Offset(w * 0.26f, h * 0.98f), stroke, StrokeCap.Round)
}

private fun DrawScope.thermometer(color: Color, warm: Boolean) {
    val w = size.width
    val h = size.height
    val stroke = w * 0.09f
    drawLine(
        color,
        Offset(w * 0.5f, h * 0.08f),
        Offset(w * 0.5f, h * 0.62f),
        stroke,
        StrokeCap.Round,
    )
    drawCircle(
        color = if (warm) Amber else color,
        radius = w * 0.18f,
        center = Offset(w * 0.5f, h * 0.78f),
    )
    if (warm) {
        drawLine(
            Amber,
            Offset(w * 0.5f, h * 0.42f),
            Offset(w * 0.5f, h * 0.64f),
            stroke * 0.7f,
            StrokeCap.Round,
        )
    }
}

@Composable
fun rememberNextAlarmMillis(): Long? {
    val context = LocalContext.current
    var trigger by remember { mutableStateOf(readNextAlarm(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                trigger = readNextAlarm(context)
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return trigger
}

@Composable
fun rememberPhoneStatus(): PhoneStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PhoneStatus()) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                val tenths = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                status = PhoneStatus(
                    percent = if (level >= 0) level * 100 / scale else -1,
                    celsius = if (tenths == Int.MIN_VALUE) null else tenths / 10f,
                    wireless = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS,
                    charging = plugged != 0,
                )
            }
        }
        val sticky = ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        if (sticky != null) receiver.onReceive(context, sticky)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return status
}

private fun readNextAlarm(context: Context): Long? {
    return context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime
}
