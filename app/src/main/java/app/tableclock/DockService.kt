package app.tableclock

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.content.ContextCompat

class DockService : Service() {
    private lateinit var prefs: Prefs
    private lateinit var signals: PhoneSignals
    private lateinit var detector: DockDetector
    private val main = Handler(Looper.getMainLooper())
    private val gate = Any()
    private var clockUp = false
    private var suppressed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        detector = DockDetector(prefs.calibration)
        signals = PhoneSignals(this)
        Notifications.createChannels(this)
        startForeground(
            NOTE_ID,
            Notifications.watching(this, openAppIntent()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        ContextCompat.registerReceiver(
            this,
            userClosed,
            android.content.IntentFilter(ACTION_USER_CLOSED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sendBroadcast(dismissIntent())
        signals.start { reading -> onReading(reading) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                prefs.enabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                detector.calibration = prefs.calibration
                return START_STICKY
            }
            else -> {
                prefs.enabled = true
                detector.calibration = prefs.calibration
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        signals.stop()
        runCatching { unregisterReceiver(userClosed) }
        getSystemService(NotificationManager::class.java).cancel(CLOCK_NOTE)
        if (clockUp) sendBroadcast(dismissIntent())
        super.onDestroy()
    }

    private fun onReading(reading: PhoneReading) {
        val nowDocked = synchronized(gate) {
            detector.onSample(
                DockSample(
                    timeMs = SystemClock.elapsedRealtime(),
                    magnitudeUt = reading.magnitudeUt,
                    landscape = reading.pose != Pose.Upright,
                    stable = reading.stable,
                    wirelessPower = reading.wirelessPower,
                ),
            )
        }
        main.post { apply(nowDocked) }
    }

    private fun apply(nowDocked: Boolean) {
        if (nowDocked) {
            if (!suppressed && !clockUp) showClock()
        } else {
            suppressed = false
            if (clockUp) hideClock()
        }
    }

    private fun showClock() {
        clockUp = true
        val launch = clockIntent()
        val power = getSystemService(PowerManager::class.java)
        val asleep = power?.isInteractive == false
        if (android.provider.Settings.canDrawOverlays(this)) {
            if (asleep) wakeScreen()
            startActivity(launch)
        }
        if (asleep || !android.provider.Settings.canDrawOverlays(this)) {
            getSystemService(NotificationManager::class.java).notify(
                CLOCK_NOTE,
                Notifications.clock(this, clockPending(launch)),
            )
        }
    }

    private fun hideClock() {
        clockUp = false
        getSystemService(NotificationManager::class.java).cancel(CLOCK_NOTE)
        sendBroadcast(dismissIntent())
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val power = getSystemService(PowerManager::class.java) ?: return
        if (power.isInteractive) return
        val lock = power.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "tableclock:wake",
        )
        lock.acquire(3_000L)
    }

    private fun clockIntent(): Intent {
        return Intent(this, ClockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    private fun clockPending(launch: Intent): PendingIntent {
        return PendingIntent.getActivity(
            this,
            1,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent {
        val launch = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(): Intent {
        return Intent(ClockActivity.ACTION_DISMISS).setPackage(packageName)
    }

    private val userClosed = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (!clockUp) return
            suppressed = true
            clockUp = false
            getSystemService(NotificationManager::class.java).cancel(CLOCK_NOTE)
        }
    }

    companion object {
        private const val NOTE_ID = 7
        private const val CLOCK_NOTE = 8
        const val ACTION_STOP = "app.tableclock.STOP"
        const val ACTION_RELOAD = "app.tableclock.RELOAD"
        const val ACTION_USER_CLOSED = "app.tableclock.USER_CLOSED"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DockService::class.java))
        }

        fun reload(context: Context) {
            context.startService(Intent(context, DockService::class.java).setAction(ACTION_RELOAD))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, DockService::class.java).setAction(ACTION_STOP))
        }
    }
}
