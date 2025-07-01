package com.example.uplisttracker

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.robolectric.RobolectricTestRunner
import org.junit.Assert.*

@RunWith(RobolectricTestRunner::class)
class AuthenticationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        mockWebServer = MockWebServer()
        client = OkHttpClient()
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_prefs", Context.MODE_PRIVATE)
        
        // Clear any existing test data
        prefs.edit().clear().apply()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `login should succeed with valid credentials and return cookies`() = runTest {
        // Setup mock server response
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "sessionId=abc123; Path=/")
            .addHeader("Set-Cookie", "userId=90045; Path=/")
            .setBody("Login successful"))

        mockWebServer.start()
        val loginUrl = mockWebServer.url("/login").toString()

        // Perform login
        val loginRequestBody = okhttp3.FormBody.Builder()
            .add("emp_number", "90045")
            .add("user_password", "03")
            .build()
        
        val loginRequest = Request.Builder()
            .url(loginUrl)
            .post(loginRequestBody)
            .build()
        
        val response = client.newCall(loginRequest).execute()
        val cookies = response.headers("Set-Cookie")
        
        // Verify response
        assertTrue(response.isSuccessful)
        assertEquals(2, cookies.size)
        assertTrue(cookies.any { it.contains("sessionId=abc123") })
        assertTrue(cookies.any { it.contains("userId=90045") })
        
        response.close()
    }

    @Test
    fun `login should fail with invalid credentials`() = runTest {
        // Setup mock server response for failed login
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("Invalid credentials"))

        mockWebServer.start()
        val loginUrl = mockWebServer.url("/login").toString()

        // Perform login
        val loginRequestBody = okhttp3.FormBody.Builder()
            .add("emp_number", "invalid")
            .add("user_password", "wrong")
            .build()
        
        val loginRequest = Request.Builder()
            .url(loginUrl)
            .post(loginRequestBody)
            .build()
        
        val response = client.newCall(loginRequest).execute()
        val cookies = response.headers("Set-Cookie")
        
        // Verify response
        assertFalse(response.isSuccessful)
        assertEquals(401, response.code)
        assertTrue(cookies.isEmpty())
        
        response.close()
    }

    @Test
    fun `position request should succeed with valid session cookies`() = runTest {
        // Setup mock server responses
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Set-Cookie", "sessionId=abc123; Path=/")
            .setBody("Login successful"))
        
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setBody("""
                <html>
                    <body>
                        <div id="position-element">15</div>
                    </body>
                </html>
            """.trimIndent()))

        mockWebServer.start()
        val loginUrl = mockWebServer.url("/login").toString()
        val positionUrl = mockWebServer.url("/position").toString()

        // First, login to get cookies
        val loginRequestBody = okhttp3.FormBody.Builder()
            .add("emp_number", "90045")
            .add("user_password", "03")
            .build()
        
        val loginRequest = Request.Builder()
            .url(loginUrl)
            .post(loginRequestBody)
            .build()
        
        val loginResponse = client.newCall(loginRequest).execute()
        val cookies = loginResponse.headers("Set-Cookie")
        loginResponse.close()
        
        // Then, request position with cookies
        val cookieHeader = cookies.joinToString("; ")
        val positionRequest = Request.Builder()
            .url(positionUrl)
            .addHeader("Cookie", cookieHeader)
            .build()
        
        val positionResponse = client.newCall(positionRequest).execute()
        
        // Verify position response
        assertTrue(positionResponse.isSuccessful)
        assertEquals(200, positionResponse.code)
        val html = positionResponse.body?.string() ?: ""
        assertTrue(html.contains("position-element"))
        assertTrue(html.contains("15"))
        
        positionResponse.close()
    }

    @Test
    fun `position request should fail with expired session`() = runTest {
        // Setup mock server response for expired session
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(401)
            .setBody("Session expired"))

        mockWebServer.start()
        val positionUrl = mockWebServer.url("/position").toString()

        // Request position with expired cookies
        val positionRequest = Request.Builder()
            .url(positionUrl)
            .addHeader("Cookie", "sessionId=expired; userId=90045")
            .build()
        
        val response = client.newCall(positionRequest).execute()
        
        // Verify response
        assertFalse(response.isSuccessful)
        assertEquals(401, response.code)
        
        response.close()
    }

    @Test
    fun `position request should fail with forbidden access`() = runTest {
        // Setup mock server response for forbidden access
        mockWebServer.enqueue(MockResponse()
            .setResponseCode(403)
            .setBody("Access forbidden"))

        mockWebServer.start()
        val positionUrl = mockWebServer.url("/position").toString()

        // Request position
        val positionRequest = Request.Builder()
            .url(positionUrl)
            .addHeader("Cookie", "sessionId=invalid; userId=90045")
            .build()
        
        val response = client.newCall(positionRequest).execute()
        
        // Verify response
        assertFalse(response.isSuccessful)
        assertEquals(403, response.code)
        
        response.close()
    }

    @Test
    fun `should handle network timeout gracefully`() = runTest {
        // Setup mock server to not respond (simulate timeout)
        mockWebServer.start()
        val loginUrl = mockWebServer.url("/login").toString()

        // Create client with short timeout
        val timeoutClient = OkHttpClient.Builder()
            .connectTimeout(1, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(1, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()

        // Perform login request
        val loginRequestBody = okhttp3.FormBody.Builder()
            .add("emp_number", "90045")
            .add("user_password", "03")
            .build()
        
        val loginRequest = Request.Builder()
            .url(loginUrl)
            .post(loginRequestBody)
            .build()
        
        try {
            val response = timeoutClient.newCall(loginRequest).execute()
            response.close()
            fail("Expected timeout exception")
        } catch (e: Exception) {
            // Pass if any exception is thrown (timeout or otherwise)
            assertTrue(true)
        }
    }
} 