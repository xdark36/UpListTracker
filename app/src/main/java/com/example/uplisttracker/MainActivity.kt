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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private enum class MonitorState { ACTIVE, PAUSED, OFFLINE }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContentView(R.layout.activity_main)
        
        // Check if monitoring should be started (don't auto-start on launch)
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val monitoringEnabled = prefs.getBoolean("monitoring_active", false)
        
        if (monitoringEnabled) {
            // Start the foreground service for monitoring
            Intent(this, PositionMonitorService::class.java).also { intent ->
                intent.action = PositionMonitorService.ACTION_START
                androidx.core.content.ContextCompat.startForegroundService(this, intent)
            }
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
                val client = OkHttpClient()
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
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
                    positionTextView.text = "Offline\nLast checked: ${getTimestamp()}"
                    showBanner(bannerText, "Error: ${e.localizedMessage}", success = false)
                    onError?.invoke("Error: ${e.localizedMessage}")
                }
            }
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
            bannerText.text = message
            bannerText.setBackgroundColor(if (success) 0xFFB9F6CA.toInt() else 0xFFFFEB3B.toInt())
            bannerText.setTextColor(0xFF000000.toInt())
            bannerText.isVisible = true
            bannerText.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT)
            bannerText.animate().alpha(1f).setDuration(200).withEndAction {
                bannerText.postDelayed({
                    bannerText.animate().alpha(0f).setDuration(400).withEndAction {
                        bannerText.isVisible = false
                        bannerText.alpha = 1f
                    }
                }, 2000)
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
}