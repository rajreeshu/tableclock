package app.tableclock

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0E0E0C)
val Cream = Color(0xFFF4F0E6)
val Mute = Color(0xFF9C958A)
val Amber = Color(0xFFE0A85C)
val Hairline = Color(0xFF2C2A26)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = Ink,
    secondary = Cream,
    onSecondary = Ink,
    background = Ink,
    onBackground = Cream,
    surface = Ink,
    onSurface = Cream,
)

@Composable
fun TableClockTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
