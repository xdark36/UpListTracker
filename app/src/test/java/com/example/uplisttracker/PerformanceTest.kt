package com.example.uplisttracker

import org.junit.Test
import org.junit.Assert.*
import kotlin.system.measureTimeMillis

/**
 * Performance tests for UpListTracker.
 * These tests measure execution time and performance characteristics.
 */
class PerformanceTest {
    
    @Test
    fun testPositionExtractionPerformance() {
        val largeHtml = buildString {
            repeat(1000) { i ->
                append("<div>Content $i</div>")
            }
            append("<div id='position-element'>42</div>")
            repeat(1000) { i ->
                append("<div>More content $i</div>")
            }
        }
        
        val executionTime = measureTimeMillis {
            repeat(100) {
                PositionUtils.extractPosition(largeHtml)
            }
        }
        
        // Should complete 100 extractions in under 1 second
        assertTrue("Position extraction took too long: ${executionTime}ms", executionTime < 1000)
    }
    
    @Test
    fun testValidationPerformance() {
        val emails = listOf(
            "test@example.com",
            "user@domain.org",
            "invalid-email",
            "",
            null
        )
        
        val passwords = listOf(
            "password123",
            "securePass",
            "",
            null
        )
        
        val executionTime = measureTimeMillis {
            repeat(1000) {
                emails.forEach { email ->
                    passwords.forEach { password ->
                        PositionUtils.validateCredentials(email, password)
                    }
                }
            }
        }
        
        // Should complete 1000 validation cycles in under 500ms
        assertTrue("Validation took too long: ${executionTime}ms", executionTime < 500)
    }
    
    @Test
    fun testUrlValidationPerformance() {
        val urls = listOf(
            "https://example.com",
            "http://test.org",
            "ftp://invalid.com",
            "not-a-url",
            "",
            null
        )
        
        val executionTime = measureTimeMillis {
            repeat(1000) {
                urls.forEach { url ->
                    PositionUtils.validateUrl(url)
                }
            }
        }
        
        // Should complete 1000 URL validations in under 500ms
        assertTrue("URL validation took too long: ${executionTime}ms", executionTime < 500)
    }
    
    @Test
    fun testInputSanitizationPerformance() {
        val inputs = listOf(
            "normal text",
            "<script>alert('xss')</script>clean text",
            "text with & symbols",
            "",
            null
        )
        
        val executionTime = measureTimeMillis {
            repeat(1000) {
                inputs.forEach { input ->
                    PositionUtils.sanitizeInput(input)
                }
            }
        }
        
        // Should complete 1000 sanitizations in under 500ms
        assertTrue("Input sanitization took too long: ${executionTime}ms", executionTime < 500)
    }
    
    @Test
    fun testPositionFormattingPerformance() {
        val positions = listOf(
            "42",
            "1234",
            "#1234",
            "999999",
            "",
            null
        )
        
        val executionTime = measureTimeMillis {
            repeat(1000) {
                positions.forEach { position ->
                    PositionUtils.formatPosition(position)
                }
            }
        }
        
        // Should complete 1000 formatting operations in under 500ms
        assertTrue("Position formatting took too long: ${executionTime}ms", executionTime < 500)
    }
    
    @Test
    fun testPositionChangeDetectionPerformance() {
        val testCases = listOf(
            Pair("42", "42"),
            Pair("42", "43"),
            Pair(null, "42"),
            Pair("42", null),
            Pair(null, null)
        )
        
        val executionTime = measureTimeMillis {
            repeat(1000) {
                testCases.forEach { (oldPos, newPos) ->
                    PositionUtils.isPositionChanged(oldPos, newPos)
                }
            }
        }
        
        // Should complete 1000 change detections in under 500ms
        assertTrue("Position change detection took too long: ${executionTime}ms", executionTime < 500)
    }
    
    @Test
    fun testMemoryUsage() {
        val initialMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        
        // Perform memory-intensive operations
        repeat(1000) {
            val largeHtml = buildString {
                repeat(100) { i ->
                    append("<div>Content $i</div>")
                }
                append("<div id='position-element'>42</div>")
            }
            PositionUtils.extractPosition(largeHtml)
            PositionUtils.sanitizeInput(largeHtml)
        }
        
        // Force garbage collection
        System.gc()
        
        val finalMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        val memoryIncrease = finalMemory - initialMemory
        
        // Memory increase should be reasonable (less than 10MB)
        val maxAllowedIncrease = 10 * 1024 * 1024 // 10MB
        assertTrue("Memory usage increased too much: ${memoryIncrease} bytes", memoryIncrease < maxAllowedIncrease)
    }
    
    @Test
    fun testConcurrentOperations() {
        val startTime = System.currentTimeMillis()
        
        // Simulate concurrent operations
        val threads = List(10) { threadId ->
            Thread {
                repeat(100) { i ->
                    val html = "<div id='position-element'>$i</div>"
                    PositionUtils.extractPosition(html)
                    PositionUtils.validateCredentials("test@example.com", "password")
                    PositionUtils.validateUrl("https://example.com")
                }
            }
        }
        
        // Start all threads
        threads.forEach { it.start() }
        
        // Wait for all threads to complete
        threads.forEach { it.join() }
        
        val totalTime = System.currentTimeMillis() - startTime
        
        // Should complete all concurrent operations in under 2 seconds
        assertTrue("Concurrent operations took too long: ${totalTime}ms", totalTime < 2000)
    }
} 