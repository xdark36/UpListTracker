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

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)
        
        // SSID Input
        val ssidLabel = TextView(this)
        ssidLabel.text = "Store WiFi SSID:"
        ssidLabel.textSize = 16f
        layout.addView(ssidLabel)
        
        val ssidInput = EditText(this)
        ssidInput.hint = "Enter store WiFi name"
        ssidInput.setPadding(0, 8, 0, 16)
        layout.addView(ssidInput)
        
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
        monitoringSwitch.text = "Enable continuous monitoring (30s intervals)"
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
        
        // Save Button
        val saveButton = Button(this)
        saveButton.text = "Save Settings"
        saveButton.setPadding(0, 16, 0, 0)
        layout.addView(saveButton)
        
        setContentView(layout)

        ssidInput.setText(prefs.getString("ssid", "Sales"))
        urlInput.setText(prefs.getString("url", "https://selling.vcfcorp.com/"))
        
        // Check if monitoring is currently active
        val isMonitoringActive = prefs.getBoolean("monitoring_active", false)
        monitoringSwitch.isChecked = isMonitoringActive

        saveButton.setOnClickListener {
            val ssid = ssidInput.text.toString()
            val url = urlInput.text.toString()
            val monitoringEnabled = monitoringSwitch.isChecked
            val pollingIntervalMin = intervalPicker.value
            
            prefs.edit()
                .putString("ssid", ssid)
                .putString("url", url)
                .putBoolean("monitoring_active", monitoringEnabled)
                .putInt("polling_interval_min", pollingIntervalMin)
                .apply()
            
            // Start or stop monitoring based on toggle
            val serviceIntent = Intent(this, PositionMonitorService::class.java)
            if (monitoringEnabled) {
                ContextCompat.startForegroundService(this, serviceIntent)
            } else {
                stopService(serviceIntent)
            }
            
            finish()
        }
    }
} 