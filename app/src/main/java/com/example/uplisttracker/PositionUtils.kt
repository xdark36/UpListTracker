package com.example.uplisttracker

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import okhttp3.*
import java.net.CookieManager
import java.net.CookiePolicy
import org.jsoup.Jsoup

/**
 * Utility class for position-related operations that can be easily tested
 */
object PositionUtils {
    private const val PREFS_NAME = "up_prefs"
    private const val COOKIE_VALID_DURATION = 30 * 60 * 1000L // 30 minutes

    private fun safeLog(tag: String, msg: String) {
        try {
            android.util.Log.d(tag, msg)
        } catch (_: Throwable) {
            // Ignore in unit tests
        }
    }

    /**
     * Extracts position value from HTML content
     * @param html The HTML content to parse
     * @return The position value or "--" if not found
     */
    fun extractPosition(html: String): String {
        val doc = Jsoup.parse(html)
        val bodyText = doc.body()?.text() ?: ""
        
        // Try multiple possible selectors for position
        val possibleSelectors = listOf(
            "#btnSalesUpStatus",
            "#position-element",
            ".position",
            "#position",
            "[data-position]",
            "span:contains(Position)",
            "div:contains(Position)",
            "td:contains(Position)"
        )
        
        val positionPatterns = listOf(
            Regex("Position\\s*#\\s*(\\d+)", RegexOption.IGNORE_CASE),
            Regex("Position:\\s*([^\\s]+)", RegexOption.IGNORE_CASE),
            Regex("Current Position:\\s*([^\\s]+)", RegexOption.IGNORE_CASE),
            Regex("Your Position:\\s*([^\\s]+)", RegexOption.IGNORE_CASE)
        )
        
        for (selector in possibleSelectors) {
            try {
                val element = doc.selectFirst(selector)
                if (element != null) {
                    val text = element.text().trim()
                    if (text.isEmpty()) {
                        return ""
                    }
                    if (selector.contains(":contains(Position)")) {
                        // Only return if text matches a position pattern
                        for (pattern in positionPatterns) {
                            val match = pattern.find(text)
                            if (match != null) {
                                val position = match.groupValues[1].trim()
                                safeLog("PositionUtils", "Found position with pattern in selector '$selector': $position")
                                return position
                            }
                        }
                        continue
                    } else if (text != "Position") {
                        safeLog("PositionUtils", "Found position with selector '$selector': $text")
                        return text
                    }
                }
            } catch (e: Exception) {
                safeLog("PositionUtils", "Selector '$selector' failed: ${e.message}")
            }
        }
        
        // If no specific element found, look for position-like text in the entire document
        safeLog("PositionUtils", "Full page text length: ${bodyText.length}")
        safeLog("PositionUtils", "Page contains 'position': ${bodyText.contains("position", ignoreCase = true)}")
        
        for (pattern in positionPatterns) {
            val match = pattern.find(bodyText)
            if (match != null) {
                val position = match.groupValues[1].trim()
                safeLog("PositionUtils", "Found position with pattern: $position")
                return position
            }
        }
        safeLog("PositionUtils", "No position found in HTML")
        return "--"
    }

    fun getOkHttpClient(): OkHttpClient {
        val cookieJar = JavaNetCookieJar(CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        })
        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .build()
    }

    fun getSessionCookie(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedCookies = prefs.getString("cached_cookies", null)
        val cookieTimestamp = prefs.getLong("cookie_timestamp", 0L)
        val currentTime = System.currentTimeMillis()
        val cookieAge = currentTime - cookieTimestamp
        return if (cachedCookies != null && cookieAge < COOKIE_VALID_DURATION) {
            cachedCookies
        } else {
            null
        }
    }

    fun cacheSessionCookie(context: Context, cookies: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cookieHeader = cookies.joinToString("; ")
        prefs.edit()
            .putString("cached_cookies", cookieHeader)
            .putLong("cookie_timestamp", System.currentTimeMillis())
            .apply()
    }

    fun clearSessionCookie(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove("cached_cookies")
            .remove("cookie_timestamp")
            .apply()
    }

    fun loginAndCacheSession(context: Context, loginUrl: String, empNumber: String, userPassword: String): Boolean {
        val client = getOkHttpClient()
        
        // First, try to get the login page to see if there are any CSRF tokens or other required fields
        val getRequest = Request.Builder()
            .url(loginUrl)
            .get()
            .build()
        
        safeLog("PositionUtils", "Attempting login to: $loginUrl")
        safeLog("PositionUtils", "Employee number: $empNumber")
        
        var sessionCookies = mutableListOf<String>()
        client.newCall(getRequest).execute().use { getResponse ->
            if (getResponse.isSuccessful) {
                sessionCookies.addAll(getResponse.headers("Set-Cookie"))
                safeLog("PositionUtils", "Got login page, session cookies: ${sessionCookies.size}")
            }
        }
        
        // Try different form field names that might be used
        val possibleFieldNames = listOf(
            "emp_number" to "user_password",
            "username" to "password", 
            "user" to "pass",
            "employee_number" to "password",
            "emp" to "pwd"
        )
        
        for ((userField, passField) in possibleFieldNames) {
            safeLog("PositionUtils", "Trying login with fields: $userField, $passField")
            
            val loginRequestBody = FormBody.Builder()
                .add(userField, empNumber)
                .add(passField, userPassword)
                .build()
            
            val loginRequest = Request.Builder()
                .url(loginUrl)
                .post(loginRequestBody)
                .apply {
                    if (sessionCookies.isNotEmpty()) {
                        addHeader("Cookie", sessionCookies.joinToString("; "))
                    }
                }
                .build()
            
            client.newCall(loginRequest).execute().use { response ->
                val postCookies = response.headers("Set-Cookie")
                val responseBody = response.body?.string() ?: ""
                val success = postCookies.isNotEmpty() && response.isSuccessful
                val isLoginSuccess = responseBody.contains("logout", ignoreCase = true) || 
                                   responseBody.contains("welcome", ignoreCase = true) ||
                                   responseBody.contains("dashboard", ignoreCase = true) ||
                                   responseBody.contains("menu", ignoreCase = true) ||
                                   !responseBody.contains("login", ignoreCase = true) ||
                                   response.code == 302
                
                safeLog("PositionUtils", "Login response code: ${response.code}")
                safeLog("PositionUtils", "Cookies received: ${postCookies.size}")
                safeLog("PositionUtils", "Response successful: ${response.isSuccessful}")
                safeLog("PositionUtils", "Response body length: ${responseBody.length}")
                safeLog("PositionUtils", "Login success indicators: $isLoginSuccess")
                
                if (success || (response.isSuccessful && isLoginSuccess)) {
                    if (postCookies.isNotEmpty()) {
                        cacheSessionCookie(context, postCookies)
                        safeLog("PositionUtils", "Session cached successfully with POST cookies: $userField, $passField")
                    } else if (sessionCookies.isNotEmpty()) {
                        cacheSessionCookie(context, sessionCookies)
                        safeLog("PositionUtils", "Session cached successfully with GET cookies: $userField, $passField")
                    } else {
                        safeLog("PositionUtils", "Login appears successful but no cookies - may use different auth method")
                    }
                    return true
                } else {
                    safeLog("PositionUtils", "Failed with fields: $userField, $passField")
                }
            }
        }
        
        safeLog("PositionUtils", "All login attempts failed")
        return false
    }

    fun makeAuthenticatedRequest(context: Context, url: String): Response? {
        val client = getOkHttpClient()
        val cookieHeader = getSessionCookie(context)
        val requestBuilder = Request.Builder().url(url)
        if (cookieHeader != null) {
            requestBuilder.addHeader("Cookie", cookieHeader)
        }
        val request = requestBuilder.build()
        return client.newCall(request).execute()
    }

    fun isOnStoreWifi(context: Context): Boolean {
        val prefs = context.getSharedPreferences("up_prefs", Context.MODE_PRIVATE)
        val storeSsid = prefs.getString("ssid", "Sales") ?: "Sales"
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                val info = wifiManager.connectionInfo
                val currentSsid = info.ssid?.replace("\"", "")
                if (currentSsid.equals("SSID_UNKNOWN", true) || currentSsid.equals("<unknown ssid>", true)) {
                    return false
                }
                return currentSsid == storeSsid
            }
        } else {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val currentSsid = info.ssid?.replace("\"", "")
            if (currentSsid.equals("SSID_UNKNOWN", true) || currentSsid.equals("<unknown ssid>", true)) {
                return false
            }
            return currentSsid == storeSsid
        }
        return false
    }

    fun fetchAndCompareUpPosition(context: Context, url: String): Boolean {
        var attempt = 0
        val maxAttempts = 2
        while (attempt < maxAttempts) {
            try {
                val response = makeAuthenticatedRequest(context, url)
                if (response?.isSuccessful == true) {
                    val html = response.body?.string() ?: ""
                    val newPosition = extractPosition(html)
                    
                    // Store the new position
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastPosition = prefs.getString("last_position", "--")
                    
                    prefs.edit()
                        .putString("last_position", newPosition)
                        .putString("last_checked", getCurrentTimestamp())
                        .apply()
                    
                    // Check if position changed
                    if (lastPosition != newPosition && lastPosition != "--") {
                        safeLog("PositionUtils", "Position changed from $lastPosition to $newPosition")
                        return true
                    }
                    return true // success
                } else {
                    // If response is not successful, try to re-login
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val loginUrl = prefs.getString("login_url", "") ?: ""
                    val empNumber = prefs.getString("emp_number", "") ?: ""
                    val userPassword = prefs.getString("user_password", "") ?: ""
                    
                    if (loginUrl.isNotEmpty() && empNumber.isNotEmpty() && userPassword.isNotEmpty()) {
                        clearSessionCookie(context)
                        if (loginAndCacheSession(context, loginUrl, empNumber, userPassword)) {
                            safeLog("PositionUtils", "Re-login successful, retrying fetch")
                            continue
                        }
                    }
                }
            } catch (e: Exception) {
                safeLog("PositionUtils", "Fetch attempt ${attempt + 1} failed: ${e.message}")
                attempt++
                if (attempt >= maxAttempts) {
                    safeLog("PositionUtils", "All fetch attempts failed.")
                    return false
                }
                // Wait a bit before retrying
                try {
                    Thread.sleep(1000)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return false
    }

    private fun getCurrentTimestamp(): String {
        val dateFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }
} 