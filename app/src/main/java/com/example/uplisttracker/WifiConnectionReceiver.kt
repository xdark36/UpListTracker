package com.example.uplisttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import timber.log.Timber

class WifiConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                Timber.i("WiFi network state changed, checking if on store WiFi")
                checkAndStartMonitoring(context)
            }
            ConnectivityManager.CONNECTIVITY_ACTION -> {
                Timber.i("Connectivity changed, checking if on store WiFi")
                checkAndStartMonitoring(context)
            }
        }
    }
    
    private fun checkAndStartMonitoring(context: Context) {
        try {
            val prefs = context.getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
            val storeSsid = prefs.getString("ssid", "Sales") ?: "Sales"
            
            if (isOnStoreWifi(context, storeSsid)) {
                Timber.i("Connected to store WiFi ($storeSsid), starting position monitoring service")
                startPositionMonitoringService(context)
            } else {
                Timber.d("Not on store WiFi (current SSID doesn't match: $storeSsid)")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking WiFi connection")
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
    
    private fun startPositionMonitoringService(context: Context) {
        try {
            val serviceIntent = Intent(context, PositionMonitorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Timber.i("Position monitoring service started from WiFi receiver")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start position monitoring service from WiFi receiver")
        }
    }
} 