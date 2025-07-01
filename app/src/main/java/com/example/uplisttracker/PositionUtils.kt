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

    /**
     * Extracts position value from HTML content
     * @param html The HTML content to parse
     * @return The position value or "--" if not found
     */
    fun extractPosition(html: String): String {
        val positionSelector = "#position-element"
        val doc = Jsoup.parse(html)
        val positionElement = doc.selectFirst(positionSelector)
        return positionElement?.text() ?: "--"
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
        val loginRequestBody = FormBody.Builder()
            .add("emp_number", empNumber)
            .add("user_password", userPassword)
            .build()
        val loginRequest = Request.Builder()
            .url(loginUrl)
            .post(loginRequestBody)
            .build()
        client.newCall(loginRequest).execute().use { response ->
            val cookies = response.headers("Set-Cookie")
            val success = cookies.isNotEmpty() && response.isSuccessful
            if (success) {
                cacheSessionCookie(context, cookies)
            }
            return success
        }
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
                // ... existing fetch logic ...
                return true // success
            } catch (e: Exception) {
                android.util.Log.e("PositionUtils", "Fetch attempt ${attempt + 1} failed", e)
                attempt++
                if (attempt >= maxAttempts) {
                    android.util.Log.e("PositionUtils", "All fetch attempts failed.")
                    return false
                }
            }
        }
        return false
    }
} 