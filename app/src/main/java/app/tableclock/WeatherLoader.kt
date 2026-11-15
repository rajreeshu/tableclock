package app.tableclock

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object WeatherLoader {
    private const val FRESH_MS = 30 * 60 * 1000L

    fun load(context: Context, force: Boolean = false): WeatherReport? {
        val prefs = Prefs(context)
        val cached = prefs.weather
        if (!force && cached != null && System.currentTimeMillis() - cached.fetchedAt < FRESH_MS) {
            return cached
        }
        if (!hasLocationPermission(context)) return cached
        val location = currentLocation(context) ?: return cached
        return try {
            val report = fetch(location.latitude, location.longitude)
            prefs.weather = report
            report
        } catch (_: Exception) {
            cached
        }
    }

    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun currentLocation(context: Context): Location? {
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val last = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.FUSED_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).firstNotNullOfOrNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
            else -> return last
        }
        val latch = CountDownLatch(1)
        var fresh: Location? = null
        val cancel = CancellationSignal()
        val executor = Executors.newSingleThreadExecutor()
        try {
            manager.getCurrentLocation(provider, cancel, executor) { location ->
                fresh = location
                latch.countDown()
            }
            latch.await(6, TimeUnit.SECONDS)
        } catch (_: Exception) {
            return last
        } finally {
            cancel.cancel()
            executor.shutdown()
        }
        return fresh ?: last
    }

    private fun fetch(latitude: Double, longitude: Double): WeatherReport {
        val lat = String.format(Locale.US, "%.3f", latitude)
        val lon = String.format(Locale.US, "%.3f", longitude)
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&forecast_days=1&timezone=auto",
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "TableClock/1.2")
        }
        try {
            if (connection.responseCode !in 200..299) error("Weather lookup failed")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val current = json.getJSONObject("current")
            val daily = json.getJSONObject("daily")
            return WeatherReport(
                tempC = current.getDouble("temperature_2m").toFloat(),
                highC = daily.getJSONArray("temperature_2m_max").getDouble(0).toFloat(),
                lowC = daily.getJSONArray("temperature_2m_min").getDouble(0).toFloat(),
                code = current.getInt("weather_code"),
                fetchedAt = System.currentTimeMillis(),
            )
        } finally {
            connection.disconnect()
        }
    }
}
