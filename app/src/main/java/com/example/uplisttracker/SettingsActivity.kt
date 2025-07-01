package com.example.uplisttracker

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.widget.SwitchCompat
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.NumberPicker
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)
        
        // Store Wi-Fi SSID Input
        val ssidLabel = TextView(this)
        ssidLabel.text = "Store Wi-Fi SSID:"
        ssidLabel.textSize = 16f
        layout.addView(ssidLabel)
        
        val ssidInput = EditText(this)
        ssidInput.hint = "Enter store WiFi name"
        ssidInput.setPadding(0, 8, 0, 16)
        layout.addView(ssidInput)
        
        // Login URL Input
        val loginUrlLabel = TextView(this)
        loginUrlLabel.text = "Login URL:"
        loginUrlLabel.textSize = 16f
        layout.addView(loginUrlLabel)
        
        val loginUrlInput = EditText(this)
        loginUrlInput.hint = "Enter login page URL"
        loginUrlInput.setPadding(0, 8, 0, 16)
        layout.addView(loginUrlInput)
        
        // Employee Number Input
        val empNumberLabel = TextView(this)
        empNumberLabel.text = "Employee Number:"
        empNumberLabel.textSize = 16f
        layout.addView(empNumberLabel)
        
        val empNumberInput = EditText(this)
        empNumberInput.hint = "Enter employee number"
        empNumberInput.setPadding(0, 8, 0, 16)
        layout.addView(empNumberInput)
        
        // Password Input
        val passwordLabel = TextView(this)
        passwordLabel.text = "Password:"
        passwordLabel.textSize = 16f
        layout.addView(passwordLabel)
        
        val passwordInput = EditText(this)
        passwordInput.hint = "Enter password"
        passwordInput.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        passwordInput.setPadding(0, 8, 0, 16)
        layout.addView(passwordInput)
        
        // URL Input
        val urlLabel = TextView(this)
        urlLabel.text = "Position URL:"
        urlLabel.textSize = 16f
        layout.addView(urlLabel)
        
        val urlInput = EditText(this)
        urlInput.hint = "Enter position page URL"
        urlInput.setPadding(0, 8, 0, 16)
        layout.addView(urlInput)
        
        // Monitoring Toggle
        val monitoringLabel = TextView(this)
        monitoringLabel.text = "Real-time Monitoring:"
        monitoringLabel.textSize = 16f
        layout.addView(monitoringLabel)
        
        val monitoringSwitch = SwitchCompat(this)
        monitoringSwitch.text = "Enable continuous monitoring"
        monitoringSwitch.setPadding(0, 8, 0, 16)
        layout.addView(monitoringSwitch)
        
        // Polling Interval Picker
        val intervalLabel = TextView(this)
        intervalLabel.text = "Polling Interval (minutes):"
        intervalLabel.textSize = 16f
        layout.addView(intervalLabel)

        val intervalPicker = NumberPicker(this)
        intervalPicker.minValue = 1
        intervalPicker.maxValue = 15
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val savedInterval = prefs.getInt("polling_interval_min", 1)
        intervalPicker.value = savedInterval
        layout.addView(intervalPicker)
        
        // Add a label to show the current interval value
        val intervalValueLabel = TextView(this)
        intervalValueLabel.text = "Current: ${savedInterval} minute${if (savedInterval > 1) "s" else ""}"
        intervalValueLabel.textSize = 14f
        intervalValueLabel.setPadding(0, 8, 0, 16)
        layout.addView(intervalValueLabel)
        
        // Update the label when picker value changes
        intervalPicker.setOnValueChangedListener { _, _, newVal ->
            intervalValueLabel.text = "Current: $newVal minute${if (newVal > 1) "s" else ""}"
        }
        
        // Auto-start toggle
        val autoStartToggle = SwitchCompat(this)
        autoStartToggle.text = "Auto-start on boot"
        autoStartToggle.setPadding(0, 8, 0, 16)
        layout.addView(autoStartToggle)
        
        // Show current Wi-Fi SSID
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val currentSsid = wifiManager.connectionInfo.ssid?.replace("\"", "") ?: "<unknown>"
        val ssidStatus = TextView(this)
        ssidStatus.text = "Current Wi-Fi: $currentSsid"
        ssidStatus.setPadding(0, 0, 0, 16)
        layout.addView(ssidStatus)
        
        // Save Button
        val saveButton = Button(this)
        saveButton.text = "Save Settings"
        saveButton.setPadding(0, 16, 0, 0)
        layout.addView(saveButton)
        
        // Clear Session Button
        val clearSessionButton = Button(this)
        clearSessionButton.text = "Clear Cached Session"
        clearSessionButton.setPadding(0, 8, 0, 0)
        layout.addView(clearSessionButton)
        
        // Test Notification Button
        val testNotifButton = Button(this)
        testNotifButton.text = "Test Notification"
        testNotifButton.setPadding(0, 8, 0, 0)
        testNotifButton.setOnClickListener {
            val notif = androidx.core.app.NotificationCompat.Builder(this, "up_chan")
                .setSmallIcon(R.drawable.ic_check_circle)
                .setContentTitle("Test Notification")
                .setContentText("This is a test notification from UpListTracker.")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .build()
            val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            mgr.notify(999, notif)
        }
        layout.addView(testNotifButton)
        
        // Debug: Force Error Button (only in debug builds)
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val forceErrorButton = Button(this)
            forceErrorButton.text = "Force Error (Debug)"
            forceErrorButton.setPadding(0, 8, 0, 0)
            forceErrorButton.setOnClickListener {
                // Simulate an error by trying to access a non-existent URL
                val intent = Intent(this, PositionMonitorService::class.java)
                intent.action = PositionMonitorService.ACTION_REFRESH
                ContextCompat.startForegroundService(this, intent)
                Toast.makeText(this, "Forced error scenario triggered", Toast.LENGTH_SHORT).show()
            }
            layout.addView(forceErrorButton)
        }
        
        setContentView(layout)

        // Load existing values
        ssidInput.setText(prefs.getString("ssid", "Sales"))
        loginUrlInput.setText(prefs.getString("login_url", "https://selling1.vcfcorp.com/"))
        empNumberInput.setText(prefs.getString("emp_number", "90045"))
        passwordInput.setText(prefs.getString("user_password", "03"))
        val defaultUrl = "https://selling1.vcfcorp.com/position"
        urlInput.setText(prefs.getString("url", defaultUrl))
        
        // Check if monitoring is currently active
        val isMonitoringActive = prefs.getBoolean("monitoring_active", false)
        monitoringSwitch.isChecked = isMonitoringActive
        
        // Auto-start toggle
        autoStartToggle.isChecked = prefs.getBoolean("auto_start_on_boot", false)

        saveButton.setOnClickListener {
            val ssid = ssidInput.text.toString()
            val loginUrl = loginUrlInput.text.toString()
            val empNumber = empNumberInput.text.toString()
            val password = passwordInput.text.toString()
            val url = urlInput.text.toString()
            val monitoringEnabled = monitoringSwitch.isChecked
            val pollingIntervalMin = intervalPicker.value
            val autoStart = autoStartToggle.isChecked
            
            prefs.edit()
                .putString("ssid", ssid)
                .putString("login_url", loginUrl)
                .putString("emp_number", empNumber)
                .putString("user_password", password)
                .putString("url", url)
                .putBoolean("monitoring_active", monitoringEnabled)
                .putInt("polling_interval_min", pollingIntervalMin)
                .putBoolean("auto_start_on_boot", autoStart)
                .apply()
            
            // Start or stop monitoring based on toggle
            val serviceIntent = Intent(this, PositionMonitorService::class.java)
            if (monitoringEnabled) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                stopService(serviceIntent)
            }
            
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        clearSessionButton.setOnClickListener {
            prefs.edit()
                .remove("cached_cookies")
                .remove("cookie_timestamp")
                .apply()
            Toast.makeText(this, "Cached session cleared!", Toast.LENGTH_SHORT).show()
        }
    }
} 