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
        
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        
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
        
        // Monitoring Info
        val monitoringLabel = TextView(this)
        monitoringLabel.text = "Real-time Monitoring:"
        monitoringLabel.textSize = 16f
        layout.addView(monitoringLabel)
        
        // Continuous monitoring is always enabled when on store WiFi
        val monitoringInfo = TextView(this)
        monitoringInfo.text = "Continuous monitoring will automatically start when connected to store WiFi"
        monitoringInfo.textSize = 14f
        monitoringInfo.setPadding(0, 8, 0, 16)
        monitoringInfo.setTextColor(0xFF666666.toInt())
        layout.addView(monitoringInfo)
        
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
        
        // Test Connection Button
        val testConnectionButton = Button(this)
        testConnectionButton.text = "Test Connection"
        testConnectionButton.setPadding(0, 8, 0, 0)
        testConnectionButton.setOnClickListener {
            testConnection()
        }
        layout.addView(testConnectionButton)
        
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
        val defaultUrl = "https://selling.vcfcorp.com/position"
        urlInput.setText(prefs.getString("url", defaultUrl))
        
        saveButton.setOnClickListener {
            val ssid = ssidInput.text.toString()
            val loginUrl = loginUrlInput.text.toString()
            val empNumber = empNumberInput.text.toString()
            val password = passwordInput.text.toString()
            val url = urlInput.text.toString()
            
            prefs.edit()
                .putString("ssid", ssid)
                .putString("login_url", loginUrl)
                .putString("emp_number", empNumber)
                .putString("user_password", password)
                .putString("url", url)
                .apply()
            
            Toast.makeText(this, "Settings saved!", Toast.LENGTH_SHORT).show()
            finish()
        }
        
        clearSessionButton.setOnClickListener {
            PositionUtils.clearSessionCookie(this)
            Toast.makeText(this, "Session cleared", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testConnection() {
        val prefs = getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val loginUrl = prefs.getString("login_url", "") ?: ""
        val empNumber = prefs.getString("emp_number", "") ?: ""
        val userPassword = prefs.getString("user_password", "") ?: ""
        val positionUrl = prefs.getString("url", "") ?: ""
        
        if (loginUrl.isEmpty() || empNumber.isEmpty() || userPassword.isEmpty() || positionUrl.isEmpty()) {
            Toast.makeText(this, "Please save your settings first", Toast.LENGTH_LONG).show()
            return
        }
        
        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show()
        
        // Run test in background
        Thread {
            try {
                android.util.Log.d("SettingsActivity", "Testing login with URL: $loginUrl, Emp: $empNumber")
                
                // Test login
                val loginSuccess = PositionUtils.loginAndCacheSession(this, loginUrl, empNumber, userPassword)
                if (!loginSuccess) {
                    android.util.Log.e("SettingsActivity", "Login failed - no cookies received or unsuccessful response")
                    runOnUiThread {
                        Toast.makeText(this, "Login failed - check credentials and URL", Toast.LENGTH_LONG).show()
                    }
                    return@Thread
                }
                
                android.util.Log.d("SettingsActivity", "Login successful, testing position fetch with URL: $positionUrl")
                
                // Test position fetch
                val response = PositionUtils.makeAuthenticatedRequest(this, positionUrl)
                if (response?.isSuccessful == true) {
                    val html = response.body?.string() ?: ""
                    android.util.Log.d("SettingsActivity", "Position response received, length: ${html.length}")
                    val position = PositionUtils.extractPosition(html)
                    android.util.Log.d("SettingsActivity", "Extracted position: $position")
                    runOnUiThread {
                        Toast.makeText(this, "Connection successful! Position: $position", Toast.LENGTH_LONG).show()
                    }
                } else {
                    val errorCode = response?.code ?: "unknown"
                    val errorBody = response?.body?.string() ?: "no body"
                    android.util.Log.e("SettingsActivity", "Position fetch failed - HTTP $errorCode, Body: $errorBody")
                    runOnUiThread {
                        Toast.makeText(this, "Position fetch failed - HTTP $errorCode", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SettingsActivity", "Test connection exception", e)
                runOnUiThread {
                    Toast.makeText(this, "Test failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
} 