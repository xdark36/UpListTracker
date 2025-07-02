package com.example.uplisttracker

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Integration tests for PositionMonitorService.
 * Note: These tests require a running Android environment.
 */
@RunWith(AndroidJUnit4::class)
class ServiceIntegrationTest {
    
    private val context: Context = ApplicationProvider.getApplicationContext()
    
    @Test
    fun testServiceCanBeStarted() {
        val intent = Intent(context, PositionMonitorService::class.java)
        val result = context.startService(intent)
        
        // Service should start successfully
        assertNotNull(result)
    }
    
    @Test
    fun testServiceIntentExtras() {
        val intent = Intent(context, PositionMonitorService::class.java).apply {
            putExtra("test_key", "test_value")
        }
        
        val result = context.startService(intent)
        assertNotNull(result)
    }
    
    @Test
    fun testServiceStop() {
        val intent = Intent(context, PositionMonitorService::class.java)
        context.startService(intent)
        
        val stopResult = context.stopService(intent)
        assertTrue(stopResult)
    }
    
    @Test
    fun testServiceMultipleStarts() {
        val intent = Intent(context, PositionMonitorService::class.java)
        
        // Multiple starts should be handled gracefully
        val result1 = context.startService(intent)
        val result2 = context.startService(intent)
        
        assertNotNull(result1)
        assertNotNull(result2)
        
        context.stopService(intent)
    }
    
    @Test
    fun testServiceWithInvalidIntent() {
        val intent = Intent(context, PositionMonitorService::class.java).apply {
            // Add invalid extras
            putExtra("invalid_data", null)
        }
        
        val result = context.startService(intent)
        assertNotNull(result)
        
        context.stopService(intent)
    }
} 