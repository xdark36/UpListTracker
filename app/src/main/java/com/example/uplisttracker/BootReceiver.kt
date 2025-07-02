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
                Timber.i("Boot completed, starting position monitoring service")
                startPositionMonitoringService(context)
            }
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Timber.i("App updated, starting position monitoring service")
                startPositionMonitoringService(context)
            }
        }
    }
    
    private fun startPositionMonitoringService(context: Context) {
        try {
            val serviceIntent = Intent(context, PositionMonitorService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            Timber.i("Position monitoring service started successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start position monitoring service")
        }
    }
} 