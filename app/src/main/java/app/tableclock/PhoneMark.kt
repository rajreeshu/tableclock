package app.tableclock

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

enum class PhoneKind { Ultra, Samsung, Other }

fun phoneLabel(market: String, model: String): String {
    val named = market.trim()
    if (named.isNotEmpty() && !named.startsWith("SM-")) return named
    return knownModel(model) ?: model.trim().ifEmpty { "Phone" }
}

fun phoneKind(label: String, model: String): PhoneKind {
    val text = "${label.lowercase()} ${model.lowercase()}"
    return when {
        "ultra" in text || model.startsWith("SM-S928") || model.startsWith("SM-S938") || model.startsWith("SM-S918") ->
            PhoneKind.Ultra
        model.startsWith("SM-") || "galaxy" in text || "samsung" in text -> PhoneKind.Samsung
        else -> PhoneKind.Other
    }
}

fun thisPhoneLabel(): String {
    val market = listOf(
        "ro.product.marketname",
        "ro.product.vendor.marketname",
        "ro.config.marketing_name",
    ).firstNotNullOfOrNull { key ->
        systemProperty(key).trim().takeIf { it.isNotEmpty() }
    }.orEmpty()
    return phoneLabel(market, Build.MODEL)
}

fun thisPhoneKind(label: String): PhoneKind = phoneKind(label, Build.MODEL)

@Composable
fun PhoneSignature(label: String, kind: PhoneKind, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        PhoneGlyph(kind, Modifier.size(width = 46.dp, height = 22.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = Cream.copy(alpha = 0.38f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Light,
            letterSpacing = 0.12.em,
            maxLines = 1,
        )
    }
}

@Composable
private fun PhoneGlyph(kind: PhoneKind, modifier: Modifier) {
    val ink = Cream.copy(alpha = 0.42f)
    Canvas(modifier) {
        val radius = size.minDimension * 0.18f
        drawRoundRect(
            color = ink,
            size = size,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = size.minDimension * 0.045f),
        )
        val island = when (kind) {
            PhoneKind.Ultra -> Size(size.width * 0.28f, size.height * 0.62f)
            PhoneKind.Samsung -> Size(size.width * 0.2f, size.height * 0.46f)
            PhoneKind.Other -> Size(size.minDimension * 0.22f, size.minDimension * 0.22f)
        }
        val left = size.width * 0.08f
        val top = (size.height - island.height) / 2f
        drawRoundRect(
            color = ink,
            topLeft = Offset(left, top),
            size = island,
            cornerRadius = CornerRadius(island.minDimension * 0.28f, island.minDimension * 0.28f),
            style = Stroke(width = size.minDimension * 0.04f),
        )
        val lenses = if (kind == PhoneKind.Other) 1 else 3
        val cx = left + island.width * 0.5f
        for (index in 0 until lenses) {
            val y = top + island.height * ((index + 1f) / (lenses + 1f))
            drawCircle(ink, radius = island.minDimension * 0.1f, center = Offset(cx, y))
        }
    }
}

private fun knownModel(model: String): String? = when {
    model.startsWith("SM-S938") -> "Galaxy S25 Ultra"
    model.startsWith("SM-S936") -> "Galaxy S25+"
    model.startsWith("SM-S931") -> "Galaxy S25"
    model.startsWith("SM-S928") -> "Galaxy S24 Ultra"
    model.startsWith("SM-S926") -> "Galaxy S24+"
    model.startsWith("SM-S921") -> "Galaxy S24"
    model.startsWith("SM-S918") -> "Galaxy S23 Ultra"
    model.startsWith("SM-S916") -> "Galaxy S23+"
    model.startsWith("SM-S911") -> "Galaxy S23"
    else -> null
}

private fun systemProperty(key: String): String = runCatching {
    val properties = Class.forName("android.os.SystemProperties")
    val get = properties.getMethod("get", String::class.java)
    get.invoke(null, key) as String
}.getOrDefault("")
