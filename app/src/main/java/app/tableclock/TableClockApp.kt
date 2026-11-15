package app.tableclock

import android.app.Application

class TableClockApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
    }
}
