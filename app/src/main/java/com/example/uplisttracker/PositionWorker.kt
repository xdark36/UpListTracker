package com.example.uplisttracker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import timber.log.Timber
import javax.inject.Inject

class PositionWorker(
    private val appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        private const val CHANNEL_ID = "position_updates"
        private const val NOTIFICATION_ID = 1
    }

    override suspend fun doWork(): ListenableWorker.Result = withContext(Dispatchers.IO) {
        try {
            val url = inputData.getString("url") ?: return@withContext ListenableWorker.Result.failure()
            val keyword = inputData.getString("keyword") ?: return@withContext ListenableWorker.Result.failure()

            Timber.d("Checking position for URL: $url, Keyword: $keyword")

            // Simulate position checking - replace with your actual logic
            val searchResults = fetchSearchResults(keyword)
            val position = findUrlPosition(searchResults, url)

            if (position > 0) {
                showNotification("Position Update", "Your site ranks #$position for '$keyword'")
            } else {
                showNotification("Position Update", "Your site is not in top 100 for '$keyword'")
            }

            Timber.d("Position check completed. Position: $position")

            ListenableWorker.Result.success()
        } catch (e: Exception) {
            Timber.e(e, "Error checking position")
            if (runAttemptCount < 3) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure()
            }
        }
    }

    private fun createNotificationChannel() {
        val mgr = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Position Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for position tracking updates"
            }
            mgr.createNotificationChannel(channel)
        }
    }

    private fun showNotification(title: String, message: String) {
        createNotificationChannel()

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val mgr = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notif)
    }

    private suspend fun fetchSearchResults(keyword: String): List<String> {
        return try {
            val doc = Jsoup.connect("https://www.google.com/search?q=$keyword")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()

            doc.select("div.g a[href]")
                .map { it.attr("href") }
                .filter { it.startsWith("http") }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching search results")
            emptyList()
        }
    }

    private fun findUrlPosition(searchResults: List<String>, targetUrl: String): Int {
        return searchResults.indexOfFirst { it.contains(targetUrl, ignoreCase = true) } + 1
    }
}