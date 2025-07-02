package com.example.uplisttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.google.android.material.snackbar.Snackbar
import androidx.activity.viewModels
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.LinearLayout
import androidx.core.app.ActivityCompat
import android.provider.Settings
import android.app.AlertDialog

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    sealed class MonitorState {
        object Active : MonitorState()
        object Paused : MonitorState()
        object Offline : MonitorState()
    }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            // Show dialog explaining why notifications are important
            showNotificationPermissionDialog()
        }
    }

    // Permission launcher for location (Android 9+)
    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Location permission granted - monitoring will start automatically when on store WiFi
            Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
        } else {
            // Show dialog explaining why location is needed
            showLocationPermissionDialog()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (!isGranted) {
            showNotificationPermissionDialog()
        }
    }

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Request permissions on startup with better user guidance
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showLocationPermissionDialog()
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            showNotificationPermissionDialog()
        }
        
        // Auto-start the position monitoring service
        startPositionMonitoringService()
        
        // Check if monitoring should be started (don't auto-start on launch)
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val monitoringEnabled = prefs.getBoolean("monitoring_active", false)
        
        // Monitoring is now automatic when connected to store WiFi
        
        try {
            val swipeRefreshLayout = findViewById<SwipeRefreshLayout>(R.id.root_layout)
            val bannerText = findViewById<TextView>(R.id.bannerText)
            val progressBar = findViewById<ProgressBar>(R.id.progressBar)
            val positionTextView = findViewById<TextView>(R.id.positionText)
            val statusText = findViewById<TextView>(R.id.statusText)
            val settingsButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.settingsButton)
            val refreshButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.refreshButton)
            val historyButton = findViewById<com.google.android.material.button.MaterialButton>(R.id.historyButton)
            
            if (swipeRefreshLayout == null || bannerText == null || progressBar == null || 
                positionTextView == null || statusText == null || settingsButton == null || 
                refreshButton == null || historyButton == null) {
                throw Exception("One or more UI elements not found")
            }
            
            val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
            // Initial display
            val lastPosition = prefs.getString("last_position", "--")
            val lastChecked = prefs.getString("last_checked", "Never")
            updatePositionDisplay(positionTextView, lastPosition ?: "--", lastChecked ?: "Never")
            updateStatusIndicator(statusText, MonitorState.Active)
            
            // Listen for changes
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == "last_position" || key == "last_checked") {
                    val newPosition = prefs.getString("last_position", "--")
                    val newChecked = prefs.getString("last_checked", "Never")
                    android.util.Log.d("MainActivity", "SharedPreferences changed - key: $key, position: $newPosition, checked: $newChecked")
                    runOnUiThread {
                        updatePositionDisplay(positionTextView, newPosition ?: "--", newChecked ?: "Never")
                        if (key == "last_position") {
                            showBanner(bannerText, "Position updated: $newPosition", success = true)
                        }
                    }
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)

            // Settings button
            settingsButton.setOnClickListener {
                startActivity(Intent(this, SettingsActivity::class.java))
            }



            // Refresh button
            refreshButton.setOnClickListener {
                fetchAndDisplayPosition(
                    onSuccess = {
                        showBanner(bannerText, "Position refreshed!", success = true)
                        updateStatusIndicator(statusText, MonitorState.Active)
                    },
                    onError = { msg ->
                        showBanner(bannerText, msg, success = false)
                        if (msg.contains("Offline", true)) {
                            updateStatusIndicator(statusText, MonitorState.Offline)
                        } else {
                            updateStatusIndicator(statusText, MonitorState.Paused)
                        }
                    }
                )
            }

            // History button
            historyButton.setOnClickListener {
                showPositionHistory()
            }

            // True pull-to-refresh
            swipeRefreshLayout.setOnRefreshListener {
                fetchAndDisplayPosition(
                    onSuccess = {
                        swipeRefreshLayout.isRefreshing = false
                        showBanner(bannerText, "Refreshed!", success = true)
                        updateStatusIndicator(statusText, MonitorState.Active)
                    },
                    onError = { msg ->
                        swipeRefreshLayout.isRefreshing = false
                        showBanner(bannerText, msg, success = false)
                        if (msg.contains("Offline", true)) {
                            updateStatusIndicator(statusText, MonitorState.Offline)
                        } else {
                            updateStatusIndicator(statusText, MonitorState.Paused)
                        }
                    }
                )
            }

            // Initial Wi-Fi check for status
            val ssid = prefs.getString("ssid", "Sales") ?: "Sales"
            if (!isOnStoreWifi(this, ssid)) {
                updateStatusIndicator(statusText, MonitorState.Offline)
            }
        } catch (e: Exception) {
            // Log the error and show a simple error message
            android.util.Log.e("MainActivity", "Error initializing UI: ${e.message}", e)
            android.widget.Toast.makeText(this, "Error initializing app: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }

        // Note: Position updates are handled via SharedPreferences listener above
        // This ensures UI updates when the service fetches new position data

        checkAndRequestLocationPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
    
    private fun startPositionMonitoringService() {
        try {
            val serviceIntent = Intent(this, PositionMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            android.util.Log.i("MainActivity", "Position monitoring service started")
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start position monitoring service", e)
        }
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
                        val timestamp = getTimestamp()
                        prefs.edit().putString("last_checked", timestamp).apply()
                        updatePositionDisplay(positionTextView, "Offline", timestamp ?: "Never")
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
                        val timestamp = getTimestamp()
                        prefs.edit().putString("last_checked", timestamp).apply()
                        updatePositionDisplay(positionTextView, "Login Failed", timestamp ?: "Never")
                        showBanner(bannerText, "Login failed. Check credentials.", success = false)
                        onError?.invoke("Login failed. Check credentials.")
                    }
                    return@launch
                }
                val response = PositionUtils.makeAuthenticatedRequest(this@MainActivity, url)
                if (response == null || !response.isSuccessful) {
                    runOnUiThread {
                        showSpinner(progressBar, false)
                        val timestamp = getTimestamp()
                        prefs.edit().putString("last_checked", timestamp).apply()
                        updatePositionDisplay(positionTextView, "HTTP Error", timestamp ?: "Never")
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
                    val timestamp = getTimestamp()
                    prefs.edit()
                        .putString("last_position", position)
                        .putString("last_checked", timestamp)
                        .apply()
                    updatePositionDisplay(positionTextView, position ?: "--", timestamp ?: "Never")
                    showBanner(bannerText, "Fetched position!", success = true)
                    onSuccess?.invoke()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSpinner(progressBar, false)
                    val timestamp = getTimestamp()
                    prefs.edit().putString("last_checked", timestamp).apply()
                    updatePositionDisplay(positionTextView, "Error", timestamp ?: "Never")
                    showBanner(bannerText, "Error: ${e.localizedMessage}", success = false)
                    onError?.invoke("Error: ${e.localizedMessage}")
                }
            }
        }
    }

    // Monitoring is now automatic when connected to store WiFi

    private fun isOnStoreWifi(context: Context, storeSsid: String): Boolean {
        if (!hasLocationPermission()) {
            android.util.Log.w("MainActivity", "Location permission not granted; cannot check SSID.")
            return false
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wifiManager.connectionInfo
                val currentSsid = info.ssid?.replace("\"", "")
                
                // Handle unknown SSID cases
                if (currentSsid.equals("SSID_UNKNOWN", true) || currentSsid.equals("<unknown ssid>", true)) {
                    android.util.Log.w("MainActivity", "WiFi SSID is unknown - network may be hidden or not broadcasting")
                    runOnUiThread {
                        val bannerText = findViewById<TextView>(R.id.bannerText)
                        showBanner(bannerText, "WiFi SSID unknown - check if network is hidden", false)
                    }
                    return false
                }
                
                // Check if we're connected to the store WiFi
                val isConnected = currentSsid == storeSsid
                android.util.Log.d("MainActivity", "Current SSID: '$currentSsid', Store SSID: '$storeSsid', Connected: $isConnected")
                return isConnected
            }
        } else {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val currentSsid = info.ssid?.replace("\"", "")
            
            if (currentSsid.equals("SSID_UNKNOWN", true) || currentSsid.equals("<unknown ssid>", true)) {
                android.util.Log.w("MainActivity", "WiFi SSID is unknown - network may be hidden or not broadcasting")
                runOnUiThread {
                    val bannerText = findViewById<TextView>(R.id.bannerText)
                    showBanner(bannerText, "WiFi SSID unknown - check if network is hidden", false)
                }
                return false
            }
            
            val isConnected = currentSsid == storeSsid
            android.util.Log.d("MainActivity", "Current SSID: '$currentSsid', Store SSID: '$storeSsid', Connected: $isConnected")
            return isConnected
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
            val mainLayout = findViewById<android.view.View>(R.id.mainLayout)
            if (mainLayout != null) {
                Snackbar.make(mainLayout, message, Snackbar.LENGTH_LONG).show()
            } else {
                bannerText.text = message
                bannerText.setBackgroundColor(if (success) 0xFFB9F6CA.toInt() else 0xFFFFEB3B.toInt())
                bannerText.setTextColor(0xFF000000.toInt())
                bannerText.isVisible = true
                bannerText.contentDescription = "Status: $message"
                bannerText.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            }
        }
    }

    private fun updateStatusIndicator(statusText: TextView, state: MonitorState) {
        when (state) {
            MonitorState.Active -> {
                statusText.text = "Monitoring: Active"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.status_active))
                statusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check_circle, 0, 0, 0)
                statusText.contentDescription = "Monitoring is active"
            }
            MonitorState.Paused -> {
                statusText.text = "Monitoring: Paused"
                statusText.setTextColor(ContextCompat.getColor(this, R.color.status_paused))
                statusText.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_pause_circle, 0, 0, 0)
                statusText.contentDescription = "Monitoring is paused"
            }
            MonitorState.Offline -> {
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

    private fun updatePositionDisplay(positionTextView: TextView, position: String, lastChecked: String) {
        val displayText = if (position == null || position == "--") {
            "Position: --"
        } else {
            "Position: $position"
        }
        positionTextView.text = displayText
        
        // Store position in history
        storePositionInHistory(position ?: "--")
    }

    private fun storePositionInHistory(position: String) {
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val historyKey = "position_history"
        val timestampKey = "position_timestamps"
        
        // Get existing history
        val history = prefs.getString(historyKey, "")?.split(",")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
        val timestamps = prefs.getString(timestampKey, "")?.split(",")?.filter { it.isNotEmpty() }?.toMutableList() ?: mutableListOf()
        
        // Add new position and timestamp
        history.add(position)
        timestamps.add(System.currentTimeMillis().toString())
        
        // Keep only last 10 entries
        if (history.size > 10) {
            history.removeAt(0)
            timestamps.removeAt(0)
        }
        
        // Save back to preferences
        prefs.edit()
            .putString(historyKey, history.joinToString(","))
            .putString(timestampKey, timestamps.joinToString(","))
            .apply()
    }

    private fun getPositionHistory(): List<Pair<String, String>> {
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val history = prefs.getString("position_history", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        val timestamps = prefs.getString("position_timestamps", "")?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
        
        return history.zip(timestamps).map { (position, timestamp) ->
            val date = Date(timestamp.toLongOrNull() ?: 0)
            val timeString = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
            position to timeString
        }.reversed() // Most recent first
    }

    private fun provideHapticFeedback() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        } catch (e: Exception) {
            // Ignore vibration errors
            android.util.Log.d("MainActivity", "Haptic feedback not available: ${e.message}")
        }
    }

    private fun sharePosition(position: String) {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "My Current Position")
                putExtra(Intent.EXTRA_TEXT, "My current position is: $position")
            }
            
            val chooser = Intent.createChooser(shareIntent, "Share Position")
            if (shareIntent.resolveActivity(packageManager) != null) {
                startActivity(chooser)
                provideHapticFeedback()
            } else {
                Toast.makeText(this, "No apps available to share", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing position: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPositionHistory() {
        val history = getPositionHistory()
        if (history.isEmpty()) {
            Toast.makeText(this, "No position history available", Toast.LENGTH_SHORT).show()
            return
        }
        
        val builder = StringBuilder()
        builder.append("Last ${history.size} position changes:\n\n")
        
        for ((position, timestamp) in history) {
            builder.append("$position at $timestamp\n")
        }
        
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Position History")
            .setMessage(builder.toString())
            .setPositiveButton("OK") { _, _ -> }
            .setNeutralButton("Clear History") { _, _ ->
                clearPositionHistory()
                Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
            }
            .create()
        dialog.show()
    }

    private fun clearPositionHistory() {
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("position_history")
            .remove("position_timestamps")
            .apply()
    }

    private fun checkAndRequestLocationPermissions() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.any { it != PackageManager.PERMISSION_GRANTED }) {
                AlertDialog.Builder(this)
                    .setTitle("Location Permission Required")
                    .setMessage("Location permission is required to access WiFi SSID. Please grant it in settings.")
                    .setPositiveButton("Open Settings") { _, _ ->
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", packageName, null)
                        })
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun promptEnableLocationServicesIfNeeded() {
        val locationManager = getSystemService(LOCATION_SERVICE) as android.location.LocationManager
        val enabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
                      locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        if (!enabled) {
            AlertDialog.Builder(this)
                .setTitle("Enable Location Services")
                .setMessage("Location services are required to access WiFi SSID. Please enable them.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun showNotificationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Notification Permission Required")
            .setMessage("This app needs notification permission to alert you when your position changes. Without this permission, you won't receive important updates about your queue position.")
            .setPositiveButton("Grant Permission") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            .setNegativeButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                })
            }
            .setNeutralButton("Not Now", null)
            .show()
    }

    private fun showLocationPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Location Permission Required")
            .setMessage("This app needs location permission to detect when you're connected to your store's WiFi network. This ensures monitoring only happens when you're at work.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestLocationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            .setNegativeButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.fromParts("package", packageName, null)
                })
            }
            .setNeutralButton("Not Now", null)
            .show()
    }
}