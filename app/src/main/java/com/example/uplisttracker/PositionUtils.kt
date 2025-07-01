package com.example.uplisttracker

import org.jsoup.Jsoup

/**
 * Utility class for position-related operations that can be easily tested
 */
object PositionUtils {
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
} 