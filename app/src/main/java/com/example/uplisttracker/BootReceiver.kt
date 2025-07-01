package com.example.uplisttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Timber.i("Boot completed, checking if monitoring should auto-start")
                autoStartMonitoringIfEnabled(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Timber.i("App updated, checking if monitoring should auto-start")
                autoStartMonitoringIfEnabled(context)
            }
        }
    }
    
    private fun autoStartMonitoringIfEnabled(context: Context) {
        val prefs: SharedPreferences = context.getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val autoStartEnabled = prefs.getBoolean("auto_start_on_boot", false)
        
        if (autoStartEnabled) {
            Timber.i("Auto-start enabled, starting position monitoring service")
            PositionMonitorService.startMonitoring(context)
        } else {
            Timber.d("Auto-start disabled, not starting monitoring service")
        }
    }
} 