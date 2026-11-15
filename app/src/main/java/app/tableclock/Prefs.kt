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

    companion object {
        const val DEFAULT_BRIGHTNESS = 0.12f
        private const val NAME = "tableclock"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AMBIENT = "ambient"
        private const val KEY_DOCKED = "docked"
        private const val KEY_BRIGHTNESS = "brightness"
    }
}
