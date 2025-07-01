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
            val channel = NotificationChannel(
                "up_chan",
                "Up Position Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for position tracking updates"
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr.createNotificationChannel(channel)
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