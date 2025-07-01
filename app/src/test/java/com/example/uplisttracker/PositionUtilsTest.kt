package com.example.uplisttracker

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PositionUtilsTest {

    @Test
    fun `extractPosition should return position when selector exists`() {
        val html = """
            <html>
                <body>
                    <div id="position-element">15</div>
                </body>
            </html>
        """.trimIndent()
        
        val result = PositionUtils.extractPosition(html)
        assertEquals("15", result)
    }

    @Test
    fun `extractPosition should return empty string when selector not found`() {
        val html = """
            <html>
                <body>
                    <div id="other-element">15</div>
                </body>
            </html>
        """.trimIndent()
        
        val result = PositionUtils.extractPosition(html)
        assertEquals("--", result)
    }

    @Test
    fun `extractPosition should handle empty HTML`() {
        val result = PositionUtils.extractPosition("")
        assertEquals("--", result)
    }

    @Test
    fun `extractPosition should handle malformed HTML`() {
        val html = "<html><body><div id=\"position-element\">25</div>"
        val result = PositionUtils.extractPosition(html)
        assertEquals("25", result)
    }

    @Test
    fun `extractPosition should handle whitespace in position value`() {
        val html = """
            <html>
                <body>
                    <div id="position-element">  42  </div>
                </body>
            </html>
        """.trimIndent()
        
        val result = PositionUtils.extractPosition(html)
        assertEquals("42", result)
    }

    @Test
    fun `extractPosition should handle complex HTML structure`() {
        val html = """
            <html>
                <head><title>Queue Position</title></head>
                <body>
                    <div class="container">
                        <div class="header">Queue Status</div>
                        <div class="content">
                            <span id="position-element">7</span>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()
        
        val result = PositionUtils.extractPosition(html)
        assertEquals("7", result)
    }

    @Test
    fun `extractPosition should handle multiple position elements`() {
        val html = """
            <html>
                <body>
                    <div id="position-element">10</div>
                    <div id="position-element">20</div>
                </body>
            </html>
        """.trimIndent()
        
        val result = PositionUtils.extractPosition(html)
        assertEquals("10", result) // Should return first match
    }

    @Test
    fun `extractPosition should handle special characters in position`() {
        val html = """
            <html>
                <body>
                    <div id="position-element">#1</div>
                </body>
            </html>
        """.trimIndent()
        
        val result = PositionUtils.extractPosition(html)
        assertEquals("#1", result)
    }
} 