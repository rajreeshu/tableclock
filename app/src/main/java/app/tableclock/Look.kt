package app.tableclock

enum class ClockFace(val label: String) {
    Analog("Classic"),
    Digital("Digital"),
    Both("Both"),
    ;

    companion object {
        fun from(raw: String?): ClockFace = entries.find { it.name == raw } ?: Both
    }
}

enum class ClassicStyle(val label: String) {
    Fine("Fine"),
    Numbers("Numbers"),
    Index("Index"),
    Dots("Dots"),
    ;

    companion object {
        fun from(raw: String?): ClassicStyle = entries.find { it.name == raw } ?: Fine
    }
}

enum class DigitalStyle(val label: String) {
    Light("Light"),
    Bold("Bold"),
    Stacked("Stacked"),
    Contrast("Contrast"),
    ;

    companion object {
        fun from(raw: String?): DigitalStyle = entries.find { it.name == raw } ?: Light
    }
}

enum class Scene {
    Sunny,
    Cloudy,
    Rain,
    Snow,
    Storm,
    Fog,
    Night,
}

fun sceneForCode(code: Int): Scene = when (code) {
    0, 1 -> Scene.Sunny
    2, 3 -> Scene.Cloudy
    45, 48 -> Scene.Fog
    51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> Scene.Rain
    71, 73, 75, 77, 85, 86 -> Scene.Snow
    95, 96, 99 -> Scene.Storm
    else -> Scene.Cloudy
}

enum class HourMode(val label: String) {
    System("System"),
    Twelve("12-hour"),
    TwentyFour("24-hour"),
    ;

    companion object {
        fun from(raw: String?): HourMode = entries.find { it.name == raw } ?: System
    }
}

fun sceneNow(code: Int?, hour: Int): Scene = when {
    code != null -> sceneForCode(code)
    hour in 6..18 -> Scene.Sunny
    else -> Scene.Night
}
