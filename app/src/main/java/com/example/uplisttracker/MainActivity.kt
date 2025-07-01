package com.example.uplisttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import android.net.wifi.WifiManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.os.Build
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import android.content.Intent
import android.widget.Button
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.SharedPreferences
import android.widget.ProgressBar
import androidx.core.view.isVisible
import android.widget.Toast
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private enum class MonitorState { ACTIVE, PAUSED, OFFLINE }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notification permission denied", Toast.LENGTH_LONG).show()
        }
    }

    // Permission launcher for location (Android 9+)
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            val intent = Intent(this, PositionMonitorService::class.java)
            intent.action = PositionMonitorService.ACTION_START
            ContextCompat.startForegroundService(this, intent)
        } else {
            Toast.makeText(this, "Location permission is required to monitor Wi-Fi.", Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(this, "Notification permission required for alerts", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        
        // Request permissions on startup
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        // Check if monitoring should be started (don't auto-start on launch)
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val monitoringEnabled = prefs.getBoolean("monitoring_active", false)
        
        if (monitoringEnabled) {
            startMonitoringServiceWithPermissionCheck()
        }
        
        try {
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.swipeRefreshLayout)
            val bannerText = findViewById<TextView>(R.id.bannerText)
            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            val positionTextView = findViewById<TextView>(R.id.positionText)
            val statusText = findViewById<TextView>(R.id.statusText)
            val settingsButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.settingsButton)
            
            if (swipeRefreshLayout == null || bannerText == null || progressBar == null || 
                positionTextView == null || statusText == null || settingsButton == null) {
                throw Exception("One or more UI elements not found")
            }
            
            val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        // Initial display
        val lastPosition = prefs.getString("last_position", "--")
        positionTextView.text = "Position: $lastPosition"
        updateStatusIndicator(statusText, MonitorState.ACTIVE)
        // Listen for changes
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "last_position") {
                val newPosition = prefs.getString("last_position", "--")
                runOnUiThread {
                    positionTextView.text = "Position: $newPosition"
                    showBanner(bannerText, "Position updated!", success = true)
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // Settings button
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // True pull-to-refresh
        swipeRefreshLayout.setOnRefreshListener {
            fetchAndDisplayPosition(
                onSuccess = {
                    swipeRefreshLayout.isRefreshing = false
                    showBanner(bannerText, "Refreshed!", success = true)
                    updateStatusIndicator(statusText, MonitorState.ACTIVE)
                },
                onError = { msg ->
                    swipeRefreshLayout.isRefreshing = false
                    showBanner(bannerText, msg, success = false)
                    if (msg.contains("Offline", true)) {
                        updateStatusIndicator(statusText, MonitorState.OFFLINE)
                    } else {
                        updateStatusIndicator(statusText, MonitorState.PAUSED)
                    }
                }
            )
        }

        // Initial Wi-Fi check for status
        val ssid = prefs.getString("ssid", "Sales") ?: "Sales"
        if (!isOnStoreWifi(this, ssid)) {
            updateStatusIndicator(statusText, MonitorState.OFFLINE)
        }
        } catch (e: Exception) {
            // Log the error and show a simple error message
            android.util.Log.e("MainActivity", "Error initializing UI: ${e.message}", e)
            android.widget.Toast.makeText(this, "Error initializing app: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launch {
            PositionRepository.position.collectLatest { newPosition ->
                val bannerText = findViewById<TextView>(R.id.bannerText)
                showBanner(bannerText, "Position updated: $newPosition", true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun extractPosition(html: String): String {
        return PositionUtils.extractPosition(html)
    }

    private fun fetchAndDisplayPosition(onSuccess: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val url = prefs.getString("url", "https://selling.vcfcorp.com/") ?: "https://selling.vcfcorp.com/"
        val ssid = prefs.getString("ssid", "Sales") ?: "Sales"
        val positionTextView = findViewById<TextView>(R.id.positionText)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        val bannerText = findViewById<TextView>(R.id.bannerText)
        showSpinner(progressBar, true)
        positionTextView.text = "Loading…"
        // Check location permission before WiFi check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !hasLocationPermission()) {
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            showSpinner(progressBar, false)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isOnStoreWifi(this@MainActivity, ssid)) {
                    runOnUiThread {
                        showSpinner(progressBar, false)
                        positionTextView.text = "Offline\nLast checked: ${getTimestamp()}"
                        showBanner(bannerText, "Offline: Not on store WiFi", success = false)
                        onError?.invoke("Offline: Not on store WiFi")
                    }
                    return@launch
                }
                // Use PositionUtils for login/session/auth
                val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
                val loginUrl = prefs.getString("login_url", "https://selling1.vcfcorp.com/") ?: "https://selling1.vcfcorp.com/"
                val empNumber = prefs.getString("emp_number", "90045") ?: "90045"
                val userPassword = prefs.getString("user_password", "03") ?: "03"
                var sessionCookie = PositionUtils.getSessionCookie(this@MainActivity)
                var loginSuccess = sessionCookie != null
                if (!loginSuccess) {
                    loginSuccess = PositionUtils.loginAndCacheSession(this@MainActivity, loginUrl, empNumber, userPassword)
                    sessionCookie = PositionUtils.getSessionCookie(this@MainActivity)
                }
                if (!loginSuccess || sessionCookie == null) {
                    runOnUiThread {
                        showSpinner(progressBar, false)
                        positionTextView.text = "Login Failed\nLast checked: ${getTimestamp()}"
                        showBanner(bannerText, "Login failed. Check credentials.", success = false)
                        onError?.invoke("Login failed. Check credentials.")
                    }
                    return@launch
                }
                val response = PositionUtils.makeAuthenticatedRequest(this@MainActivity, url)
                if (response == null || !response.isSuccessful) {
                    runOnUiThread {
                        showSpinner(progressBar, false)
                        positionTextView.text = "HTTP Error\nLast checked: ${getTimestamp()}"
                        showBanner(bannerText, "Failed to fetch position (HTTP ${response?.code ?: "?"})", success = false)
                        onError?.invoke("Failed to fetch position (HTTP ${response?.code ?: "?"})")
                    }
                    response?.close()
                    return@launch
                }
                val html = response.body?.string() ?: ""
                response.close()
                val position = extractPosition(html)
                runOnUiThread {
                    showSpinner(progressBar, false)
                    positionTextView.text = "Position: $position\nLast checked: ${getTimestamp()}"
                    showBanner(bannerText, "Fetched position!", success = true)
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSpinner(progressBar, false)
                    positionTextView.text = "Error\nLast checked: ${getTimestamp()}"
                    showBanner(bannerText, "Error: ${e.localizedMessage}", success = false)
                    onError?.invoke("Error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun isOnStoreWifi(context: Context, storeSsid: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !hasLocationPermission()) {
            return false // Will trigger permission request
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
                    android.util.Log.w("MainActivity", "WiFi SSID is unknown, retrying...")
                    runOnUiThread {
                        val bannerText = findViewById<TextView>(R.id.bannerText)
                        showBanner(bannerText, "WiFi SSID unknown, retrying...", false)
                    }
                    Thread.sleep(1500) // Wait and let caller retry
                    return false
                }
                return currentSsid == storeSsid
            }
        } else {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val currentSsid = info.ssid?.replace("\"", "")
            if (currentSsid.equals("SSID_UNKNOWN", true) || currentSsid.equals("<unknown ssid>", true)) {
                android.util.Log.w("MainActivity", "WiFi SSID is unknown, retrying...")
                runOnUiThread {
                    val bannerText = findViewById<TextView>(R.id.bannerText)
                    showBanner(bannerText, "WiFi SSID unknown, retrying...", false)
                }
                Thread.sleep(1500)
                return false
            }
            return currentSsid == storeSsid
        }
        return false
    }

    private fun getTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date())
    }

    private fun showSpinner(progressBar: ProgressBar, show: Boolean) {
        runOnUiThread {
            progressBar.isVisible = show
        }
    }

    private fun showBanner(bannerText: TextView, message: String, success: Boolean) {
        runOnUiThread {
            val timestamp = getTimestamp()
            val bannerMessage = "$message\nLast checked at $timestamp"
            bannerText.text = bannerMessage
            bannerText.setBackgroundColor(if (success) 0xFFB9F6CA.toInt() else 0xFFFFEB3B.toInt())
            bannerText.setTextColor(0xFF000000.toInt())
            bannerText.isVisible = true
            bannerText.contentDescription = "Status: $message"
            bannerText.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            
            // Animate in
            bannerText.alpha = 0f
            bannerText.animate().alpha(1f).setDuration(200).withEndAction {
                // Auto-hide after delay, but don't interfere with swipe refresh
                bannerText.postDelayed({
                    if (!bannerText.isVisible) return@postDelayed // Already hidden
                    bannerText.animate().alpha(0f).setDuration(400).withEndAction {
                        bannerText.isVisible = false
                        bannerText.alpha = 1f
                    }
                }, 3000) // Increased delay to allow reading timestamp
            }
        }
    }

    private fun updateStatusIndicator(statusText: TextView, state: MonitorState) {
        when (state) {
            MonitorState.ACTIVE -> {
                statusText.text = "Monitoring: Active"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.status_active))
                statusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0)
                statusText.contentDescription = "Monitoring is active"
            }
            MonitorState.PAUSED -> {
                statusText.text = "Monitoring: Paused"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.status_paused))
                statusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_circle, 0, 0, 0)
                statusText.contentDescription = "Monitoring is paused"
            }
            MonitorState.OFFLINE -> {
                statusText.text = "Monitoring: Offline"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.status_offline))
                statusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_circle, 0, 0, 0)
                statusText.contentDescription = "Monitoring is offline"
            }
        }
        statusText.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_ANNOUNCEMENT)
    }
    
    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                    // Permission already granted
                    android.util.Log.d("MainActivity", "POST_NOTIFICATIONS permission already granted")
                }
                shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale and request permission
                    Toast.makeText(this, "Notification permission is needed for position alerts", Toast.LENGTH_LONG).show()
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    // Request permission directly
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun startMonitoringServiceWithPermissionCheck() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(this, PositionMonitorService::class.java)
            intent.action = PositionMonitorService.ACTION_START
            ContextCompat.startForegroundService(this, intent)
        } else {
            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                Toast.makeText(this, "Location permission is needed to check Wi-Fi SSID.", Toast.LENGTH_LONG).show()
            }
            requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}