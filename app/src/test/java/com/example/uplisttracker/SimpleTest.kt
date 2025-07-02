package com.example.uplisttracker

import org.junit.Test
import org.junit.Assert.*

/**
 * Simple tests for basic functionality.
 */
class SimpleTest {
    
    @Test
    fun testBasicMath() {
        assertEquals(4, 2 + 2)
    }
    
    @Test
    fun testStringOperations() {
        val testString = "Hello World"
        assertTrue(testString.contains("Hello"))
        assertTrue(testString.contains("World"))
    }
    
    @Test
    fun testPositionExtraction() {
        val html = "<div id='position-element'>42</div>"
        val result = PositionUtils.extractPosition(html)
        assertEquals("42", result)
    }
    
    @Test
    fun testPositionExtractionWithFullHtml() {
        val html = """
            <html>
                <body>
                    <div id='position-element'>123</div>
                </body>
            </html>
        """
        val result = PositionUtils.extractPosition(html)
        assertEquals("123", result)
    }
    
    @Test
    fun testPositionExtractionMissingElement() {
        val html = "<div>No position element here</div>"
        val result = PositionUtils.extractPosition(html)
        assertEquals("--", result)
    }
    
    @Test
    fun testPositionExtractionEmptyHtml() {
        val result = PositionUtils.extractPosition("")
        assertEquals("--", result)
    }
} 