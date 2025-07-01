package com.example.uplisttracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class UpListTrackerApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize logging
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            Timber.plant(Timber.DebugTree())
        } else {
            // In production, you could plant a custom tree for crash reporting
            // Timber.plant(CrashReportingTree())
        }

        Timber.i("UpListTracker application started")

        // Create notification channel once at app startup
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            val upChan = NotificationChannel(
                "up_chan",
                "Position Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when position changes"
            }
            mgr.createNotificationChannel(upChan)
            val monitorChan = NotificationChannel(
                "position_monitor",
                "Monitoring Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when position monitoring is active"
            }
            mgr.createNotificationChannel(monitorChan)
        }
    }
}

// Custom Timber tree for crash reporting (example for future implementation)
/*
class CrashReportingTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority >= android.util.Log.WARN) {
            // Send to crash reporting service (Firebase Crashlytics, Sentry, etc.)
            // Example: FirebaseCrashlytics.getInstance().log("$tag: $message")
            // if (t != null) FirebaseCrashlytics.getInstance().recordException(t)
        }
    }
}
*/