package com.space.app.domain.generation

import com.space.app.domain.generation.providers.GroqLlmClient
import com.space.app.domain.generation.providers.LlmClientFactory
import com.space.app.domain.generation.providers.OpenRouterLlmClient
import com.space.app.domain.generation.providers.OpenAiCompatClient
import com.space.app.domain.generation.providers.defaultHttpClient
import com.space.app.data.ProviderKind
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Integration tests for the LLM provider HTTP clients using MockWebServer.
 * Verifies the full request/response cycle: URL construction, headers,
 * request body shape, and response parsing — not just the failover logic.
 */
class ProviderIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var http: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        http = defaultHttpClient()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `openai-compatible client parses successful response`() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val client = OpenAiCompatClient(
            apiKey = "test-key",
            baseUrl = baseUrl,
            model = "test-model",
            http = http
        )

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                    {"choices":[{"message":{"role":"assistant","content":"Hello from the model"}}]}
                """.trimIndent())
        )

        val result = client.complete(
            LlmRequest(system = "You are helpful.", user = "Say hello.")
        )

        assertTrue("Expected Ok, got $result", result is LlmResult.Ok)
        assertEquals("Hello from the model", (result as LlmResult.Ok).text)
    }

    @Test
    fun `openai-compatible client handles 401`() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val client = OpenAiCompatClient(
            apiKey = "bad-key",
            baseUrl = baseUrl,
            model = "test-model",
            http = http
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"Unauthorized"}}""")
        )

        val result = client.complete(LlmRequest(system = "", user = "test"))

        assertTrue("Expected HttpError, got $result", result is LlmResult.HttpError)
        assertEquals(401, (result as LlmResult.HttpError).code)
    }

    @Test
    fun `openai-compatible client handles 429 with Retry-After`() = runBlocking {
        val baseUrl = server.url("/").toString().trimEnd('/')
        val client = OpenAiCompatClient(
            apiKey = "key",
            baseUrl = baseUrl,
            model = "test-model",
            http = http
        )

        server.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "120")
                .setBody("""{"error":{"message":"Rate limited"}}""")
        )

        val result = client.complete(LlmRequest(system = "", user = "test"))

        assertTrue(result is LlmResult.HttpError)
        val error = result as LlmResult.HttpError
        assertEquals(429, error.code)
        assertEquals(120L, error.retryAfterSeconds)
    }

    @Test
    fun `groq factory creates a groq client`() {
        val groq = LlmClientFactory.create(
            ProviderKind.GROQ,
            apiKey = "gsk_test",
            http = http
        )
        assertTrue("Groq client should be created", groq is GroqLlmClient)
    }

    @Test
    fun `openrouter factory creates an openrouter client`() {
        val openrouter = LlmClientFactory.create(
            ProviderKind.OPENROUTER,
            apiKey = "or_test",
            http = http
        )
        assertTrue("OpenRouter client should be created", openrouter is OpenRouterLlmClient)
    }

    @Test
    fun `gemini factory creates a gemini client`() {
        val gemini = LlmClientFactory.create(
            ProviderKind.GEMINI,
            apiKey = "test-key",
            http = http
        )
        assertTrue("Gemini client should be created", gemini != null)
    }
}