package com.example.uplisttracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.CookieManager
import java.net.CookiePolicy

class PositionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "position_updates"
        private const val NOTIFICATION_ID = 42
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Set up cookie jar
            val cookieManager = CookieManager().apply {
                setCookiePolicy(CookiePolicy.ACCEPT_ALL)
            }
            val client = OkHttpClient.Builder()
                .cookieJar(JavaNetCookieJar(cookieManager))
                .build()

            // Fetch and parse
            val response = client.newCall(
                okhttp3.Request.Builder()
                    .url("https://example.com/track")
                    .get().build()
            ).execute()

            val doc = Jsoup.parse(response.body!!.string())
            val positionText = doc.selectFirst(".current-position")?.text() ?: "N/A"

            // Show notification
            showNotification(positionText)

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private fun showNotification(position: String) {
        val mgr = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create channel on O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Position Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            mgr.createNotificationChannel(channel)
        }

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            // Use your own drawable or an Android builtin icon
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Current Position")
            .setContentText(position)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        mgr.notify(NOTIFICATION_ID, notif)
    }
}
