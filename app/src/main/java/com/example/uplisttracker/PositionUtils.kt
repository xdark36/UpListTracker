package com.example.uplisttracker

import android.content.Context
import android.content.SharedPreferences
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
} 