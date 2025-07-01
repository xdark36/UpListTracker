package com.example.uplisttracker

import org.junit.Test
import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun extractPosition_parsesCorrectly() {
        val html = """
            <html><body><div id='position-element'>42</div></body></html>
        """
        val expected = "42"
        val actual = PositionUtils.extractPosition(html)
        assertEquals(expected, actual)
    }
    
    @Test
    fun extractPosition_handlesMissingElement() {
        val html = """
            <html><body><div>No position element here</div></body></html>
        """
        val expected = "--"
        val actual = PositionUtils.extractPosition(html)
        assertEquals(expected, actual)
    }
}