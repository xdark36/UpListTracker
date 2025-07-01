package com.example.uplisttracker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid

    val _loginUrl = MutableStateFlow("")
    val loginUrl: StateFlow<String> = _loginUrl

    val _empNumber = MutableStateFlow("")
    val empNumber: StateFlow<String> = _empNumber

    val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url

    val _monitoring = MutableStateFlow(false)
    val monitoring: StateFlow<Boolean> = _monitoring

    val _pollingInterval = MutableStateFlow(1)
    val pollingInterval: StateFlow<Int> = _pollingInterval

    val _autoStart = MutableStateFlow(false)
    val autoStart: StateFlow<Boolean> = _autoStart

    fun loadFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        _ssid.value = prefs.getString("ssid", "Sales") ?: "Sales"
        _loginUrl.value = prefs.getString("login_url", "https://selling1.vcfcorp.com/") ?: "https://selling1.vcfcorp.com/"
        _empNumber.value = prefs.getString("emp_number", "90045") ?: "90045"
        _password.value = prefs.getString("user_password", "03") ?: "03"
        _url.value = prefs.getString("url", "https://selling1.vcfcorp.com/position") ?: "https://selling1.vcfcorp.com/position"
        _monitoring.value = prefs.getBoolean("monitoring_active", false)
        _pollingInterval.value = prefs.getInt("polling_interval_min", 1)
        _autoStart.value = prefs.getBoolean("auto_start_on_boot", false)
    }

    fun saveToPrefs(context: Context) {
        val prefs = context.getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("ssid", _ssid.value)
            .putString("login_url", _loginUrl.value)
            .putString("emp_number", _empNumber.value)
            .putString("user_password", _password.value)
            .putString("url", _url.value)
            .putBoolean("monitoring_active", _monitoring.value)
            .putInt("polling_interval_min", _pollingInterval.value)
            .putBoolean("auto_start_on_boot", _autoStart.value)
            .apply()
    }

    fun clearSession(context: Context) {
        val prefs = context.getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("cached_cookies")
            .remove("cookie_timestamp")
            .apply()
    }
} 