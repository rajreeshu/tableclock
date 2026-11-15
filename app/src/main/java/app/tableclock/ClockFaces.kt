package app.tableclock

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import java.time.LocalDateTime
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

fun Scene.accent(): Color = when (this) {
    Scene.Sunny -> Color(0xFFE0A85C)
    Scene.Cloudy -> Color(0xFFC8C2B6)
    Scene.Rain -> Color(0xFF8FB4D6)
    Scene.Snow -> Color(0xFFE8EEF4)
    Scene.Storm -> Color(0xFFC9B6E8)
    Scene.Fog -> Color(0xFFB7B2A8)
    Scene.Night -> Color(0xFFD9C7A2)
}

@Composable
fun AnalogClock(
    now: LocalDateTime,
    accent: Color,
    style: ClassicStyle,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = Cream.copy(alpha = 0.55f),
            radius = radius - 2f,
            center = center,
            style = Stroke(width = if (style == ClassicStyle.Index) 4f else 2.5f),
        )
        when (style) {
            ClassicStyle.Fine -> fineTicks(center, radius, accent)
            ClassicStyle.Numbers -> {
                hourTicks(center, radius, accent, inner = 0.84f, outer = 0.93f, stroke = 2.4f)
                hourNumbers(center, radius * 0.68f, Cream.copy(alpha = 0.9f).toArgb())
            }
            ClassicStyle.Index -> hourTicks(center, radius, accent, inner = 0.72f, outer = 0.93f, stroke = 5f)
            ClassicStyle.Dots -> dotTicks(center, radius, accent)
        }
        val hour = (now.hour % 12) + now.minute / 60f + now.second / 3600f
        val minute = now.minute + now.second / 60f
        val hourLength = if (style == ClassicStyle.Index) 0.46f else 0.48f
        val minuteLength = if (style == ClassicStyle.Index) 0.68f else 0.70f
        hand(center, hour * 30.0 - 90.0, radius * hourLength, if (style == ClassicStyle.Index) 8f else 7f, Cream)
        hand(center, minute * 6.0 - 90.0, radius * minuteLength, if (style == ClassicStyle.Index) 5f else 4.5f, Cream)
        if (style != ClassicStyle.Index) {
            hand(center, now.second * 6.0 - 90.0, radius * 0.76f, 2f, accent)
        }
        drawCircle(color = accent, radius = 7f, center = center)
        drawCircle(color = Color.Black, radius = 3f, center = center)
    }
}

@Composable
fun WeatherMark(scene: Scene, modifier: Modifier = Modifier) {
    val color = scene.accent()
    Canvas(modifier) {
        when (scene) {
            Scene.Sunny -> sun(color)
            Scene.Cloudy -> cloud(color)
            Scene.Rain -> {
                cloud(color)
                drops(color)
            }
            Scene.Snow -> {
                cloud(color)
                flakes(color)
            }
            Scene.Storm -> {
                cloud(color)
                bolt(color)
            }
            Scene.Fog -> mist(color)
            Scene.Night -> moon(color)
        }
    }
}

private fun DrawScope.sun(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val r = size.minDimension * 0.22f
    drawCircle(color, r, c)
    for (i in 0 until 8) {
        val angle = i * 45.0 * PI / 180.0
        drawLine(
            color,
            c + polar(angle, r * 1.55f),
            c + polar(angle, r * 2.15f),
            strokeWidth = size.minDimension * 0.045f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.cloud(color: Color) {
    val w = size.minDimension
    val y = size.height * 0.42f
    drawCircle(color, w * 0.16f, Offset(size.width * 0.38f, y))
    drawCircle(color, w * 0.22f, Offset(size.width * 0.52f, y - w * 0.06f))
    drawCircle(color, w * 0.14f, Offset(size.width * 0.66f, y + w * 0.02f))
}

private fun DrawScope.drops(color: Color) {
    val y = size.height * 0.62f
    listOf(0.36f, 0.52f, 0.66f).forEachIndexed { index, x ->
        drawLine(
            color,
            Offset(size.width * x, y + index * 3f),
            Offset(size.width * x - 4f, y + size.minDimension * 0.16f),
            strokeWidth = 2.4f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.flakes(color: Color) {
    listOf(0.36f to 0.68f, 0.52f to 0.76f, 0.66f to 0.66f).forEach { (x, y) ->
        drawCircle(color, 3.2f, Offset(size.width * x, size.height * y))
    }
}

private fun DrawScope.bolt(color: Color) {
    val x = size.width * 0.52f
    val y = size.height * 0.58f
    drawLine(color, Offset(x + 8f, y), Offset(x - 2f, y + 16f), 3f, StrokeCap.Round)
    drawLine(color, Offset(x - 2f, y + 16f), Offset(x + 6f, y + 16f), 3f, StrokeCap.Round)
    drawLine(color, Offset(x + 6f, y + 16f), Offset(x - 6f, y + 34f), 3f, StrokeCap.Round)
}

private fun DrawScope.mist(color: Color) {
    listOf(0.32f, 0.48f, 0.64f).forEachIndexed { index, y ->
        val inset = if (index == 1) 0.22f else 0.16f
        drawLine(
            color,
            Offset(size.width * inset, size.height * y),
            Offset(size.width * (1f - inset), size.height * y),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(color: Color) {
    val c = Offset(size.width * 0.48f, size.height * 0.5f)
    val r = size.minDimension * 0.28f
    drawCircle(color, r, c, style = Stroke(width = 3f))
    drawCircle(Color.Black, r * 0.82f, Offset(c.x + r * 0.45f, c.y - r * 0.15f))
}

private fun DrawScope.dotTicks(center: Offset, radius: Float, accent: Color) {
    for (tick in 0 until 60) {
        val major = tick % 5 == 0
        val angle = (tick * 6.0 - 90.0) * PI / 180.0
        drawCircle(
            color = if (tick == 0) accent else Cream.copy(alpha = if (major) 0.9f else 0.28f),
            radius = if (major) size.minDimension * 0.018f else size.minDimension * 0.007f,
            center = center + polar(angle, radius * 0.86f),
        )
    }
}

private fun DrawScope.fineTicks(center: Offset, radius: Float, accent: Color) {
    for (tick in 0 until 60) {
        val major = tick % 5 == 0
        val angle = (tick * 6.0 - 90.0) * PI / 180.0
        val outer = radius * 0.93f
        val inner = radius * if (major) 0.78f else 0.87f
        val color = when {
            tick == 0 -> accent
            major -> Cream.copy(alpha = 0.85f)
            else -> Cream.copy(alpha = 0.22f)
        }
        drawLine(
            color = color,
            start = center + polar(angle, inner),
            end = center + polar(angle, outer),
            strokeWidth = if (major) 3.2f else 1.4f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.hourTicks(
    center: Offset,
    radius: Float,
    accent: Color,
    inner: Float,
    outer: Float,
    stroke: Float,
) {
    for (hour in 0 until 12) {
        val angle = (hour * 30.0 - 90.0) * PI / 180.0
        drawLine(
            color = if (hour == 0) accent else Cream.copy(alpha = 0.9f),
            start = center + polar(angle, radius * inner),
            end = center + polar(angle, radius * outer),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.hourNumbers(center: Offset, radius: Float, color: Int) {
    val paint = Paint().apply {
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = size.minDimension * 0.11f
        this.color = color
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }
    for (hour in 1..12) {
        val angle = (hour * 30.0 - 90.0) * PI / 180.0
        val pos = center + polar(angle, radius)
        val baseline = pos.y - (paint.ascent() + paint.descent()) / 2f
        drawContext.canvas.nativeCanvas.drawText(hour.toString(), pos.x, baseline, paint)
    }
}

private fun DrawScope.hand(center: Offset, degrees: Double, length: Float, stroke: Float, color: Color) {
    val angle = degrees * PI / 180.0
    drawLine(color, center, center + polar(angle, length), stroke, StrokeCap.Round)
}

private fun polar(angle: Double, length: Float): Offset {
    return Offset((cos(angle) * length).toFloat(), (sin(angle) * length).toFloat())
}
