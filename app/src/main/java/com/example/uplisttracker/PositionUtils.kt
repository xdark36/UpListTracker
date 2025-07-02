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
        // 1. Try #btnSalesUpStatus with 'Position # X' format
        val statusDiv = doc.getElementById("btnSalesUpStatus")
        val regex = Regex("""Position\s*#\s*(\d+)""", RegexOption.IGNORE_CASE)
        val text = statusDiv?.text()?.trim() ?: ""
        val match = regex.find(text)
        if (match != null) {
            val position = match.groupValues[1].trim()
            safeLog("PositionUtils", "Extracted position from #btnSalesUpStatus: $position")
            return position
        }
        // 2. Try #position-element (legacy/other formats)
        val positionElement = doc.getElementById("position-element")
        if (positionElement != null) {
            val value = positionElement.text().trim()
            safeLog("PositionUtils", "Extracted position from #position-element: '$value'")
            return value
        }
        // 3. Fallback: search the whole document for 'Position # X'
        val bodyText = doc.body()?.text() ?: ""
        val matchFallback = regex.find(bodyText)
        if (matchFallback != null) {
            val position = matchFallback.groupValues[1].trim()
            safeLog("PositionUtils", "Extracted position from body text: $position")
            return position
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
        // Compose the correct login endpoint
        val loginEndpoint = if (loginUrl.endsWith("/")) loginUrl + "index.php/main/login" else loginUrl + "/index.php/main/login"
        safeLog("PositionUtils", "Attempting login to: $loginEndpoint")
        safeLog("PositionUtils", "Employee number: $empNumber")
        // Build the POST body as the browser does
        val loginRequestBody = FormBody.Builder()
            .add("store", "")
            .add("user_id", "")
            .add("user", "")
            .add("other_id", "")
            .add("emp_number", empNumber)
            .add("setpassword", userPassword)
            .add("isTouchSupported", "false")
            .build()
        // Log POST fields
        val postFields = mutableListOf<Pair<String, String>>()
        for (i in 0 until loginRequestBody.size) {
            postFields.add(loginRequestBody.name(i) to loginRequestBody.value(i))
        }
        safeLog("PositionUtils", "POST fields: ${postFields.joinToString { it.first + "=" + it.second }}")
        val loginRequest = Request.Builder()
            .url(loginEndpoint)
            .post(loginRequestBody)
            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:102.0) Gecko/102.0 Firefox/102.0")
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", loginUrl)
            .build()
        client.newCall(loginRequest).execute().use { response ->
            val postCookies = response.headers("Set-Cookie")
            val responseBody = response.body?.string() ?: ""
            // Log all response headers
            safeLog("PositionUtils", "Login response headers:")
            for ((name, value) in response.headers) {
                safeLog("PositionUtils", "$name: $value")
            }
            // Log response body (truncate if very large)
            val truncatedBody = if (responseBody.length > 1000) responseBody.substring(0, 1000) + "... [truncated]" else responseBody
            safeLog("PositionUtils", "Login response body: $truncatedBody")
            safeLog("PositionUtils", "Login response code: ${response.code}")
            safeLog("PositionUtils", "Cookies received: ${postCookies.size}")
            safeLog("PositionUtils", "Response successful: ${response.isSuccessful}")
            safeLog("PositionUtils", "Response body length: ${responseBody.length}")
            // Parse JSON response for success
            val isLoginSuccess = responseBody.contains("\"success\":true")
            safeLog("PositionUtils", "Login success indicators: $isLoginSuccess")
            if (isLoginSuccess && postCookies.isNotEmpty()) {
                cacheSessionCookie(context, postCookies)
                safeLog("PositionUtils", "Session cached successfully with POST cookies (browser-style login)")
                return true
            } else if (isLoginSuccess) {
                safeLog("PositionUtils", "Login appears successful but no cookies - may use different auth method")
                return true
            } else {
                safeLog("PositionUtils", "Login failed or unexpected response")
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

    private fun isStaleSessionData(html: String): Boolean {
        // Check for common indicators of stale/expired session data
        return html.contains("login") || 
               html.contains("Login") || 
               html.contains("session") || 
               html.contains("expired") ||
               html.contains("timeout") ||
               html.length < 100  // Very short responses often indicate redirects to login
    }

    fun fetchAndCompareUpPosition(context: Context, url: String): Boolean {
        var attempt = 0
        val maxAttempts = 3  // Increased attempts to allow for session refresh
        while (attempt < maxAttempts) {
            try {
                val response = makeAuthenticatedRequest(context, url)
                if (response?.isSuccessful == true) {
                    val html = response.body?.string() ?: ""
                    val newPosition = extractPosition(html)
                    
                    // Store the new position
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val lastPosition = prefs.getString("last_position", "--")
                    
                    // Check for stale session data or empty position with cached session
                    val shouldRefreshSession = (newPosition.isEmpty() || isStaleSessionData(html)) && 
                                             getSessionCookie(context) != null && 
                                             attempt < maxAttempts - 1
                    
                    if (shouldRefreshSession) {
                        safeLog("PositionUtils", "Detected stale session data or empty position, refreshing session...")
                        clearSessionCookie(context)
                        val loginUrl = prefs.getString("login_url", "") ?: ""
                        val empNumber = prefs.getString("emp_number", "") ?: ""
                        val userPassword = prefs.getString("user_password", "") ?: ""
                        
                        if (loginUrl.isNotEmpty() && empNumber.isNotEmpty() && userPassword.isNotEmpty()) {
                            if (loginAndCacheSession(context, loginUrl, empNumber, userPassword)) {
                                safeLog("PositionUtils", "Session refreshed, retrying position fetch")
                                attempt++
                                continue
                            }
                        }
                    }
                    
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