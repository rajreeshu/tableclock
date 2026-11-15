package app.tableclock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.view.OrientationEventListener
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

data class PhoneReading(
    val magnitudeUt: Float,
    val haveField: Boolean,
    val pose: Pose,
    val landscape: Boolean,
    val stable: Boolean,
    val wirelessPower: Boolean,
)

class PhoneSignals(context: Context) {
    private val app = context.applicationContext
    private val sensors = app.getSystemService(SensorManager::class.java)
    private val magnetometer = sensors.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gravity = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
        ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val hasMagnetometer: Boolean = magnetometer != null

    private val lock = Any()
    private var callback: ((PhoneReading) -> Unit)? = null
    private var running = false
    private var thread: HandlerThread? = null
    private var orientationListener: OrientationEventListener? = null

    private var magnitude = 0f
    private var haveField = false
    private var haveGravity = false
    private var gravitySide: Boolean? = null
    private var orientation = PoseMath.UNKNOWN_ANGLE
    private var rememberedSide = false
    private var gyro = 0f
    private var wireless = false
    private val window = ArrayDeque<Float>()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            synchronized(lock) { wireless = isWireless(intent) }
            dispatch()
        }
    }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            synchronized(lock) {
                when (event.sensor.type) {
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        val v = event.values
                        magnitude = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                        haveField = true
                        window.addLast(magnitude)
                        while (window.size > 5) window.removeFirst()
                    }
                    Sensor.TYPE_GRAVITY, Sensor.TYPE_ACCELEROMETER -> {
                        haveGravity = true
                        gravitySide = PoseMath.fromGravity(event.values)
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        val v = event.values
                        gyro = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                    }
                }
            }
            dispatch()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start(onReading: (PhoneReading) -> Unit) {
        callback = onReading
        if (running) {
            dispatch()
            return
        }
        running = true
        ContextCompat.registerReceiver(
            app,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val worker = HandlerThread("tableclock-sensors").also { it.start() }
        thread = worker
        val handler = Handler(worker.looper)
        magnetometer?.let {
            sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI, handler)
        }
        gravity?.let {
            sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI, handler)
        }
        gyroscope?.let {
            sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_UI, handler)
        }
        val listener = object : OrientationEventListener(app, SensorManager.SENSOR_DELAY_UI) {
            override fun onOrientationChanged(newOrientation: Int) {
                synchronized(lock) { orientation = newOrientation }
                dispatch()
            }
        }
        orientationListener = listener
        if (listener.canDetectOrientation()) listener.enable()
        dispatch()
    }

    fun stop() {
        callback = null
        if (!running) return
        running = false
        runCatching { app.unregisterReceiver(batteryReceiver) }
        sensors.unregisterListener(sensorListener)
        orientationListener?.disable()
        orientationListener = null
        thread?.quitSafely()
        thread = null
        synchronized(lock) {
            window.clear()
            haveField = false
            haveGravity = false
            gravitySide = null
            gyro = 0f
        }
    }

    private fun dispatch() {
        val reading = synchronized(lock) { snapshot() }
        callback?.invoke(reading)
    }

    private fun snapshot(): PhoneReading {
        val spread = if (window.size >= 3) window.max() - window.min() else Float.MAX_VALUE
        val spreadLimit = maxOf(60f, magnitude * 0.35f)
        val fieldSettled = haveField && window.size >= 3 && spread < spreadLimit
        val strongMagnet = haveField && window.size >= 3 && magnitude >= DockDetector.ABSOLUTE_ENGAGE_UT
        val still = gyro < 1.1f && (fieldSettled || strongMagnet || !hasMagnetometer)
        val sideNow = when {
            gravitySide != null -> gravitySide
            orientation != PoseMath.UNKNOWN_ANGLE -> PoseMath.isSideAngle(orientation)
            else -> null
        }
        if (sideNow != null) rememberedSide = sideNow
        val landscape = sideNow ?: rememberedSide
        val pose = when {
            !haveGravity && orientation == PoseMath.UNKNOWN_ANGLE -> Pose.Upright
            gravitySide == true -> Pose.Side
            gravitySide == false -> Pose.Upright
            else -> Pose.Flat
        }
        return PhoneReading(
            magnitudeUt = magnitude,
            haveField = haveField,
            pose = pose,
            landscape = landscape,
            stable = still,
            wirelessPower = wireless,
        )
    }

    companion object {
        fun isWireless(intent: Intent): Boolean {
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            return plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS
        }

        fun isPlugged(intent: Intent): Boolean {
            return intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        }
    }
}
