package com.example.uplisttracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import timber.log.Timber
import java.util.concurrent.TimeUnit

class PositionMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isMonitoring = false

    companion object {
        private const val NOTIFICATION_ID = 100
        private const val CHANNEL_ID = "position_monitor"
        const val ACTION_START = "com.example.uplisttracker.action.START"
        const val ACTION_STOP  = "com.example.uplisttracker.action.STOP"
        const val ACTION_PAUSE = "com.example.uplisttracker.action.PAUSE"
        const val ACTION_REFRESH = "com.example.uplisttracker.action.REFRESH"
        private const val ALERT_CHANNEL_ID = "up_chan"
        private const val POSITION_SELECTOR = "#position-element"
        
        fun startMonitoring(context: Context) {
            val intent = Intent(context, PositionMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopMonitoring(context: Context) {
            val intent = Intent(context, PositionMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_START -> {
                if (!isMonitoring) {
                    startForegroundMonitoring()
                }
                START_STICKY
            }
            ACTION_STOP -> {
                isMonitoring = false
                stopSelf()
                START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                isMonitoring = false
                showPausedNotification()
                START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                scope.launch { checkPositionChange(getUrlFromPrefs()) }
                START_STICKY
            }
            else -> START_NOT_STICKY
        }
    }

    private fun startForegroundMonitoring() {
        isMonitoring = true
        startForeground(NOTIFICATION_ID, buildMonitoringNotification())
        
        scope.launch {
            while (isActive && isMonitoring) {
                try {
                    val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
                    val url = prefs.getString("url", "https://selling.vcfcorp.com/") ?: "https://selling.vcfcorp.com/"
                    val ssid = prefs.getString("ssid", "Sales") ?: "Sales"
                    val intervalMin = prefs.getInt("polling_interval_min", 1)
                    val pollingIntervalMs = intervalMin * 60_000L
                    
                    if (isOnStoreWifi(applicationContext, ssid)) {
                        checkPositionChange(url)
                    } else {
                        Timber.d("Not on store WiFi, waiting...")
                    }
                    
                    delay(pollingIntervalMs)
                } catch (e: Exception) {
                    Timber.e(e, "Error in monitoring loop")
                    delay(60_000) // fallback delay
                }
            }
        }
    }

    private suspend fun checkPositionChange(url: String) {
        var attempt = 0
        var backoff = 2000L // 2 seconds
        val maxAttempts = 3
        while (attempt < maxAttempts) {
            try {
                Timber.d("Checking position for URL: $url (attempt ${attempt + 1})")

                val client = OkHttpClient()

                // --- LOGIN LOGIC START ---
                val loginUrl = "https://selling1.vcfcorp.com/"
                val loginRequestBody = okhttp3.FormBody.Builder()
                    .add("emp_number", "90045")
                    .add("user_password", "03")
                    .build()
                val loginRequest = Request.Builder()
                    .url(loginUrl)
                    .post(loginRequestBody)
                    .build()
                val loginResponse = client.newCall(loginRequest).execute()
                val cookies = loginResponse.headers("Set-Cookie")
                val loginSuccess = cookies.isNotEmpty() && loginResponse.isSuccessful
                loginResponse.close()
                if (!loginSuccess) {
                    Timber.e("Login failed: no cookies or bad response")
                    android.os.Handler(mainLooper).post {
                        android.widget.Toast.makeText(this, "Login failed. Check credentials.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return
                }
                val cookieHeader = cookies.joinToString("; ")
                // --- LOGIN LOGIC END ---

                // Fetch page with OkHttp, using session cookies
                val positionRequest = Request.Builder()
                    .url(url)
                    .addHeader("Cookie", cookieHeader)
                    .build()
                val response = client.newCall(positionRequest).execute()

                if (!response.isSuccessful) {
                    Timber.e("HTTP request failed: ${response.code}")
                    android.os.Handler(mainLooper).post {
                        android.widget.Toast.makeText(this, "Failed to fetch position (HTTP ${response.code})", android.widget.Toast.LENGTH_LONG).show()
                    }
                    response.close()
                    throw Exception("HTTP error")
                }

                val html = response.body?.string() ?: ""
                response.close()

                // Parse with Jsoup
                val doc = Jsoup.parse(html)
                val positionElement = doc.selectFirst(POSITION_SELECTOR)
                val newPosition = positionElement?.text() ?: ""
                Timber.d("Parsed position: $newPosition")

                if (positionElement == null) {
                    Timber.e("Selector $POSITION_SELECTOR not found in HTML")
                    android.os.Handler(mainLooper).post {
                        android.widget.Toast.makeText(this, "Could not find position on page.", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return
                }

                // Compare to last value in SharedPreferences
                val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
                val lastPosition = prefs.getString("last_position", null)

                if (newPosition.isNotEmpty() && newPosition != lastPosition) {
                    // Save new value
                    prefs.edit().putString("last_position", newPosition).apply()
                    showPositionChangeNotification("Position Update", "Position changed: $newPosition")
                    Timber.d("Position changed from $lastPosition to $newPosition")
                } else {
                    Timber.d("Position unchanged or empty.")
                }
                return // Success, exit retry loop
            } catch (e: Exception) {
                Timber.e(e, "Error checking position (attempt ${attempt + 1})")
                attempt++
                if (attempt < maxAttempts) {
                    delay(backoff)
                    backoff *= 2 // Exponential backoff
                } else {
                    android.os.Handler(mainLooper).post {
                        android.widget.Toast.makeText(this, "Failed after $maxAttempts attempts.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun buildMonitoringNotification(): Notification {
        val pauseIntent = PendingIntent.getService(this, 1, Intent(this, PositionMonitorService::class.java).apply {
            action = ACTION_PAUSE
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val refreshIntent = PendingIntent.getService(this, 2, Intent(this, PositionMonitorService::class.java).apply {
            action = ACTION_REFRESH
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Position Monitor")
            .setContentText("Monitoring queue position...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.ic_pause_circle, "Pause", pauseIntent)
            .addAction(R.drawable.ic_refresh, "Refresh", refreshIntent)
            .build()
    }

    private fun showPausedNotification() {
        val resumeIntent = PendingIntent.getService(this, 3, Intent(this, PositionMonitorService::class.java).apply {
            action = ACTION_START
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Position Monitor")
            .setContentText("Monitoring paused")
            .setSmallIcon(R.drawable.ic_pause_circle)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(false)
            .addAction(R.drawable.ic_play_arrow, "Resume", resumeIntent)
            .build()
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mgr.notify(NOTIFICATION_ID, notif)
    }

    private fun getUrlFromPrefs(): String {
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        return prefs.getString("url", "https://selling.vcfcorp.com/") ?: "https://selling.vcfcorp.com/"
    }

    private fun showPositionChangeNotification(title: String, message: String) {
        // Check POST_NOTIFICATIONS permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                Timber.w("POST_NOTIFICATIONS permission not granted; notification not shown.")
                android.os.Handler(mainLooper).post {
                    android.widget.Toast.makeText(this, "Notification permission denied", android.widget.Toast.LENGTH_SHORT).show()
                }
                return
            }
        }

        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Position Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when position monitoring is active"
            }
            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Position Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when position changes"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    private fun isOnStoreWifi(context: Context, storeSsid: String): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wifiManager.connectionInfo
                val currentSsid = info.ssid?.replace("\"", "")
                return currentSsid == storeSsid
            }
        } else {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val currentSsid = info.ssid?.replace("\"", "")
            return currentSsid == storeSsid
        }
        return false
    }

    override fun onDestroy() {
        isMonitoring = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 