package app.tableclock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.min

@Composable
fun ClockBoard(
    now: LocalDateTime,
    face: ClockFace,
    classic: ClassicStyle,
    digitalStyle: DigitalStyle,
    showSeconds: Boolean,
    showDate: Boolean,
    use24: Boolean,
    scene: Scene,
    modifier: Modifier = Modifier,
    weather: @Composable () -> Unit = {},
) {
    val accent = scene.accent()
    val locale = java.util.Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern(if (use24) "HH:mm" else "h:mm", locale))
    val seconds = now.format(DateTimeFormatter.ofPattern("ss", locale))
    val period = if (use24) null else now.format(DateTimeFormatter.ofPattern("a", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    BoxWithConstraints(modifier) {
        val showAnalog = face != ClockFace.Digital
        val showDigital = face != ClockFace.Analog
        val compact = maxHeight < 320.dp
        val analog = when (face) {
            ClockFace.Analog -> min(maxHeight.value * 0.72f, maxWidth.value * 0.42f)
            ClockFace.Both -> min(maxHeight.value * 0.78f, maxWidth.value * 0.34f)
            ClockFace.Digital -> 0f
        }
        val timeSize = when (face) {
            ClockFace.Digital -> min(maxWidth.value / 5.4f, maxHeight.value * if (compact) 0.34f else 0.42f)
            ClockFace.Both -> min(
                (maxWidth.value - analog - if (compact) 48f else 120f).coerceAtLeast(80f) / 4.8f,
                maxHeight.value * if (compact) 0.28f else 0.34f,
            )
            ClockFace.Analog -> 0f
        }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (showAnalog) AnalogClock(now, accent, classic, Modifier.size(analog.dp))
            if (showAnalog && showDigital) Spacer(Modifier.width(if (compact) 12.dp else 36.dp))
            Column(
                horizontalAlignment = if (face == ClockFace.Both) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                if (showDigital) {
                    DigitalTime(
                        time = time,
                        seconds = if (showSeconds) seconds else null,
                        period = period,
                        size = timeSize,
                        style = digitalStyle,
                        accent = accent,
                    )
                    Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
                }
                if (showDate) {
                    Text(
                        text = date,
                        color = Cream.copy(alpha = 0.62f),
                        fontSize = if (compact) 11.sp else 18.sp,
                    )
                    Spacer(Modifier.height(if (compact) 6.dp else 16.dp))
                }
                weather()
            }
        }
    }
}

@Composable
fun DigitalTime(
    time: String,
    seconds: String?,
    period: String?,
    size: Float,
    style: DigitalStyle,
    accent: Color,
) {
    val weight = if (style == DigitalStyle.Bold) FontWeight.Normal else FontWeight.Light
    val tracking = if (style == DigitalStyle.Bold) (-0.03).em else (-0.045).em
    val parts = time.split(':')
    if (style == DigitalStyle.Stacked) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = parts.first(),
                color = Cream,
                fontSize = (size * 0.62f).sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.04).em,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = parts.getOrElse(1) { "" },
                    color = Cream.copy(alpha = 0.82f),
                    fontSize = (size * 0.62f).sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = (-0.04).em,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                )
                if (seconds != null) {
                    Text(
                        text = seconds,
                        color = Cream.copy(alpha = 0.4f),
                        fontSize = (size * 0.18f).sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
            if (period != null) {
                Text(period, color = Cream.copy(alpha = 0.5f), fontSize = (size * 0.14f).sp)
            }
        }
        return
    }
    val minuteColor = if (style == DigitalStyle.Contrast) accent else Cream
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = parts.first(),
            color = Cream,
            fontSize = size.sp,
            fontWeight = weight,
            letterSpacing = tracking,
            maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        Text(
            text = ":",
            color = Cream.copy(alpha = 0.45f),
            fontSize = size.sp,
            fontWeight = weight,
        )
        Text(
            text = parts.getOrElse(1) { "" },
            color = minuteColor,
            fontSize = size.sp,
            fontWeight = weight,
            letterSpacing = tracking,
            maxLines = 1,
            style = TextStyle(fontFeatureSettings = "tnum"),
        )
        if (period != null || seconds != null) {
            Column(modifier = Modifier.padding(start = 8.dp, bottom = (size * 0.08f).dp)) {
                if (period != null) {
                    Text(
                        text = period,
                        color = Cream.copy(alpha = 0.55f),
                        fontSize = (size * 0.16f).sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (seconds != null) {
                    Text(
                        text = seconds,
                        color = Cream.copy(alpha = 0.45f),
                        fontSize = (size * 0.22f).sp,
                        fontWeight = weight,
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
    }
}
