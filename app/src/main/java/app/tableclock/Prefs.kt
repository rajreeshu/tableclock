package app.tableclock

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        }

    var calibration: Calibration
        get() = Calibration(
            ambientUt = prefs.getFloat(KEY_AMBIENT, 0f),
            dockedUt = prefs.getFloat(KEY_DOCKED, 0f),
        )
        set(value) {
            prefs.edit()
                .putFloat(KEY_AMBIENT, value.ambientUt)
                .putFloat(KEY_DOCKED, value.dockedUt)
                .apply()
        }

        var brightness: Float
        get() = prefs.getFloat(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS)
        set(value) {
            prefs.edit().putFloat(KEY_BRIGHTNESS, value.coerceIn(0.04f, 0.75f)).apply()
        }

    var face: ClockFace
        get() = ClockFace.from(prefs.getString(KEY_FACE, null))
        set(value) {
            prefs.edit().putString(KEY_FACE, value.name).apply()
        }

    var classic: ClassicStyle
        get() = ClassicStyle.from(prefs.getString(KEY_CLASSIC, null))
        set(value) {
            prefs.edit().putString(KEY_CLASSIC, value.name).apply()
        }

    var digitalStyle: DigitalStyle
        get() = DigitalStyle.from(prefs.getString(KEY_DIGITAL, null))
        set(value) {
            prefs.edit().putString(KEY_DIGITAL, value.name).apply()
        }

    var showSeconds: Boolean
        get() = prefs.getBoolean(KEY_SECONDS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SECONDS, value).apply()
        }

    var showDate: Boolean
        get() = prefs.getBoolean(KEY_DATE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DATE, value).apply()
        }

    var showWeather: Boolean
        get() = prefs.getBoolean(KEY_SHOW_WEATHER, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_WEATHER, value).apply()
        }

    var hourMode: HourMode
        get() = HourMode.from(prefs.getString(KEY_HOURS, null))
        set(value) {
            prefs.edit().putString(KEY_HOURS, value.name).apply()
        }

    var weather: WeatherReport?
        get() {
            if (!prefs.contains(KEY_WEATHER_AT)) return null
            return WeatherReport(
                tempC = prefs.getFloat(KEY_TEMP, 0f),
                highC = prefs.getFloat(KEY_HIGH, 0f),
                lowC = prefs.getFloat(KEY_LOW, 0f),
                code = prefs.getInt(KEY_CODE, 0),
                fetchedAt = prefs.getLong(KEY_WEATHER_AT, 0L),
            )
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_WEATHER_AT).apply()
                return
            }
            prefs.edit()
                .putFloat(KEY_TEMP, value.tempC)
                .putFloat(KEY_HIGH, value.highC)
                .putFloat(KEY_LOW, value.lowC)
                .putInt(KEY_CODE, value.code)
                .putLong(KEY_WEATHER_AT, value.fetchedAt)
                .apply()
        }

    companion object {
        const val DEFAULT_BRIGHTNESS = 0.12f
        private const val NAME = "tableclock"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AMBIENT = "ambient"
        private const val KEY_DOCKED = "docked"
        private const val KEY_BRIGHTNESS = "brightness"
        private const val KEY_TEMP = "weather_temp"
        private const val KEY_HIGH = "weather_high"
        private const val KEY_LOW = "weather_low"
        private const val KEY_CODE = "weather_code"
        private const val KEY_WEATHER_AT = "weather_at"
        private const val KEY_FACE = "face"
        private const val KEY_CLASSIC = "classic"
        private const val KEY_DIGITAL = "digital"
        private const val KEY_SECONDS = "seconds"
        private const val KEY_DATE = "date"
        private const val KEY_SHOW_WEATHER = "show_weather"
        private const val KEY_HOURS = "hours"
    }
}
