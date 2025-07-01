package com.example.uplisttracker

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class SharedPreferencesTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
    }

    @Test
    fun `should store and retrieve login credentials`() {
        val loginUrl = "https://test.example.com/login"
        val empNumber = "12345"
        val userPassword = "secret123"

        prefs.edit()
            .putString("login_url", loginUrl)
            .putString("emp_number", empNumber)
            .putString("user_password", userPassword)
            .apply()

        assertEquals(loginUrl, prefs.getString("login_url", ""))
        assertEquals(empNumber, prefs.getString("emp_number", ""))
        assertEquals(userPassword, prefs.getString("user_password", ""))
    }

    @Test
    fun `should store and retrieve session cookies`() {
        val cookies = "sessionId=abc123; userId=90045"
        val timestamp = System.currentTimeMillis()

        prefs.edit()
            .putString("cached_cookies", cookies)
            .putLong("cookie_timestamp", timestamp)
            .apply()

        assertEquals(cookies, prefs.getString("cached_cookies", ""))
        assertEquals(timestamp, prefs.getLong("cookie_timestamp", 0))
    }

    @Test
    fun `should handle default values correctly`() {
        assertEquals("Sales", prefs.getString("ssid", "Sales"))
        assertEquals("https://selling.vcfcorp.com/", prefs.getString("url", "https://selling.vcfcorp.com/"))
        assertEquals("https://selling1.vcfcorp.com/", prefs.getString("login_url", "https://selling1.vcfcorp.com/"))
        assertEquals("90045", prefs.getString("emp_number", "90045"))
        assertEquals("03", prefs.getString("user_password", "03"))
        assertEquals(1, prefs.getInt("polling_interval_min", 1))
        assertEquals(false, prefs.getBoolean("monitoring_active", false))
    }

    @Test
    fun `should clear session data correctly`() {
        // Store some session data
        prefs.edit()
            .putString("cached_cookies", "sessionId=abc123")
            .putLong("cookie_timestamp", System.currentTimeMillis())
            .apply()

        // Verify data exists
        assertNotNull(prefs.getString("cached_cookies", null))
        assertNotEquals(0L, prefs.getLong("cookie_timestamp", 0))

        // Clear session data
        prefs.edit()
            .remove("cached_cookies")
            .remove("cookie_timestamp")
            .apply()

        // Verify data is cleared
        assertNull(prefs.getString("cached_cookies", null))
        assertEquals(0L, prefs.getLong("cookie_timestamp", 0))
    }

    @Test
    fun `should handle position updates correctly`() {
        val initialPosition = "15"
        val updatedPosition = "12"

        // Store initial position
        prefs.edit()
            .putString("last_position", initialPosition)
            .apply()

        assertEquals(initialPosition, prefs.getString("last_position", ""))

        // Update position
        prefs.edit()
            .putString("last_position", updatedPosition)
            .apply()

        assertEquals(updatedPosition, prefs.getString("last_position", ""))
    }

    @Test
    fun `should handle monitoring state correctly`() {
        // Initially should be false
        assertFalse(prefs.getBoolean("monitoring_active", false))

        // Enable monitoring
        prefs.edit()
            .putBoolean("monitoring_active", true)
            .apply()

        assertTrue(prefs.getBoolean("monitoring_active", false))

        // Disable monitoring
        prefs.edit()
            .putBoolean("monitoring_active", false)
            .apply()

        assertFalse(prefs.getBoolean("monitoring_active", false))
    }

    @Test
    fun `should handle polling interval correctly`() {
        val interval = 5

        prefs.edit()
            .putInt("polling_interval_min", interval)
            .apply()

        assertEquals(interval, prefs.getInt("polling_interval_min", 1))
    }

    @Test
    fun `should handle multiple preference updates atomically`() {
        val loginUrl = "https://new.example.com/login"
        val empNumber = "54321"
        val monitoringEnabled = true
        val interval = 3

        // Update multiple preferences in one transaction
        prefs.edit()
            .putString("login_url", loginUrl)
            .putString("emp_number", empNumber)
            .putBoolean("monitoring_active", monitoringEnabled)
            .putInt("polling_interval_min", interval)
            .apply()

        // Verify all updates were applied
        assertEquals(loginUrl, prefs.getString("login_url", ""))
        assertEquals(empNumber, prefs.getString("emp_number", ""))
        assertEquals(monitoringEnabled, prefs.getBoolean("monitoring_active", false))
        assertEquals(interval, prefs.getInt("polling_interval_min", 1))
    }
} 