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
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.jsoup.Jsoup
import timber.log.Timber
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.util.concurrent.TimeUnit
import android.Manifest
import androidx.localbroadcastmanager.content.LocalBroadcastManager

class PositionMonitorService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var isMonitoring = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var httpClient: OkHttpClient? = null

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
        setupNetworkCallback()
        setupHttpClient()
    }

    private fun setupHttpClient() {
        val cookieJar = JavaNetCookieJar(CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        })
        
        httpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private fun setupNetworkCallback() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Timber.i("Network available, resuming monitoring")
                    if (isMonitoring) {
                        scope.launch { checkPositionChange(getUrlFromPrefs()) }
                    }
                }

                override fun onLost(network: Network) {
                    Timber.w("Network lost, pausing monitoring")
                    // Don't stop monitoring, just wait for network to return
                }

                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    val hasWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    Timber.d("Network capabilities changed - WiFi: $hasWifi, Internet: $hasInternet")
                }
            }
            
            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
        }
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

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isOnStoreWifi(context: Context, storeSsid: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !hasLocationPermission()) {
            Timber.w("Location permission not granted; cannot check SSID.")
            return false
        }
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wifiManager.connectionInfo
                val currentSsid = info.ssid?.replace("\"", "")
                if (currentSsid.equals("SSID_UNKNOWN", true) || currentSsid.equals("<unknown ssid>", true)) {
                    Timber.w("WiFi SSID is unknown, retrying...")
                    Thread.sleep(1500)
                    return false
                }
                return currentSsid == storeSsid
            }
        } else {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val currentSsid = info.ssid?.replace("\"", "")
            if (currentSsid.equals("SSID_UNKNOWN", true) || currentSsid.equals("<unknown ssid>", true)) {
                Timber.w("WiFi SSID is unknown, retrying...")
                Thread.sleep(1500)
                return false
            }
            return currentSsid == storeSsid
        }
        return false
    }

    private suspend fun checkPositionChange(url: String) {
        var attempt = 0
        var backoff = 2000L // 2 seconds
        val maxAttempts = 3
        while (attempt < maxAttempts) {
            try {
                Timber.i("Checking position for URL: $url (attempt ${attempt + 1}/$maxAttempts)")
                val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
                val loginUrl = prefs.getString("login_url", "https://selling1.vcfcorp.com/") ?: "https://selling1.vcfcorp.com/"
                val empNumber = prefs.getString("emp_number", "90045") ?: "90045"
                val userPassword = prefs.getString("user_password", "03") ?: "03"
                var sessionCookie = PositionUtils.getSessionCookie(this)
                var loginSuccess = sessionCookie != null
                if (!loginSuccess) {
                    loginSuccess = PositionUtils.loginAndCacheSession(this, loginUrl, empNumber, userPassword)
                    sessionCookie = PositionUtils.getSessionCookie(this)
                }
                if (!loginSuccess || sessionCookie == null) {
                    Timber.e("Login failed; cannot fetch position.")
                    return
                }
                val response = PositionUtils.makeAuthenticatedRequest(this, url)
                if (response == null || !response.isSuccessful) {
                    Timber.e("HTTP request failed: ${response?.code ?: "?"}")
                    response?.close()
                    return
                }
                val html = response.body?.string() ?: ""
                response.close()
                val doc = Jsoup.parse(html)
                val positionElement = doc.selectFirst(POSITION_SELECTOR)
                val newPosition = positionElement?.text() ?: ""
                Timber.i("Parsed position: '$newPosition'")
                val lastPosition = prefs.getString("last_position", null)
                if (newPosition.isNotEmpty() && newPosition != lastPosition) {
                    prefs.edit().putString("last_position", newPosition).apply()
                    showPositionChangeNotification("Position Update", "Position changed: $newPosition")
                    Timber.i("Position changed from '$lastPosition' to '$newPosition'")
                    val broadcastIntent = Intent("com.example.uplisttracker.POSITION_UPDATE")
                    broadcastIntent.putExtra("new_position", newPosition)
                    LocalBroadcastManager.getInstance(this).sendBroadcast(broadcastIntent)
                } else if (newPosition.isNotEmpty()) {
                    Timber.d("Position unchanged: '$newPosition'")
                } else {
                    Timber.w("Position element found but value is empty")
                }
                return // Success, exit retry loop
            } catch (e: Exception) {
                Timber.e(e, "Error checking position (attempt ${attempt + 1}/$maxAttempts)")
                attempt++
                if (attempt < maxAttempts) {
                    Timber.i("Retrying in ${backoff}ms...")
                    delay(backoff)
                    backoff *= 2 // Exponential backoff
                } else {
                    Timber.e("Failed after $maxAttempts attempts, giving up")
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
        val stopIntent = PendingIntent.getService(this, 4, Intent(this, PositionMonitorService::class.java).apply {
            action = ACTION_STOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Position Monitor")
            .setContentText("Monitoring queue position...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(R.drawable.ic_pause_circle, "Pause", pauseIntent)
            .addAction(R.drawable.ic_refresh, "Refresh", refreshIntent)
            .addAction(R.drawable.ic_stop_circle, "Stop", stopIntent)
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
        
        Timber.i("Position change notification sent: $title - $message")
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

    override fun onDestroy() {
        isMonitoring = false
        scope.cancel()
        
        // Unregister network callback
        networkCallback?.let { callback ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 