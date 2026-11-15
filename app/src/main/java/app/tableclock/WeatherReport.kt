package app.tableclock

data class WeatherReport(
    val tempC: Float,
    val highC: Float,
    val lowC: Float,
    val code: Int,
    val fetchedAt: Long,
)

fun weatherLabel(code: Int): String = when (code) {
    0 -> "Clear"
    1 -> "Mostly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55, 56, 57 -> "Drizzle"
    61, 63, 65, 66, 67 -> "Rain"
    71, 73, 75, 77 -> "Snow"
    80, 81, 82 -> "Showers"
    85, 86 -> "Snow showers"
    95, 96, 99 -> "Thunderstorm"
    else -> "Cloudy"
}

fun formatTemperature(celsius: Float, fahrenheit: Boolean): String {
    val value = if (fahrenheit) celsius * 9f / 5f + 32f else celsius
    val unit = if (fahrenheit) "F" else "C"
    return "${kotlin.math.round(value).toInt()}°$unit"
}

fun prefersFahrenheit(country: String): Boolean = country == "US" || country == "LR" || country == "MM"
