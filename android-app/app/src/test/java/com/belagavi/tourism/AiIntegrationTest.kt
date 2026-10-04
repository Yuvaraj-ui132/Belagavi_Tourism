package com.belagavi.tourism

import com.belagavi.tourism.data.model.ChatMessageDto
import com.belagavi.tourism.data.model.ChatRequestDto
import com.belagavi.tourism.data.model.ChatResponseDto
import com.belagavi.tourism.data.network.AiApiService
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import java.util.concurrent.TimeUnit

class AiIntegrationTest {

    private lateinit var apiService: AiApiService

    @Before
    fun setup() {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
            .build()

        apiService = Retrofit.Builder()
            .baseUrl(AiApiService.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiApiService::class.java)
    }

    @Test
    fun testBaseUrlVerification() {
        assertEquals("https://belagavi-tourism-yuvaraj21.vercel.app/", AiApiService.BASE_URL)
    }

    @Test
    fun testQuery1_Hi() = runBlocking {
        val query = "hi"
        val request = ChatRequestDto(message = query, history = emptyList())
        val response = apiService.sendChatMessage(request)

        assertTrue("HTTP status must be successful", response.isSuccessful)
        assertEquals(200, response.code())

        val body = response.body()
        assertNotNull("Response body must not be null", body)
        val dto = body!!

        assertNotNull("Answer must not be null", dto.safeAnswer)
        assertTrue("Answer must not be empty", dto.safeAnswer.isNotBlank())
        assertFalse("Web research should not be used for greeting", dto.webResearchUsed ?: false)
        assertTrue("Destinations should be empty for greeting", dto.safeDestinations.isEmpty())
        assertTrue("Web sources should be empty for greeting", dto.safeWebSources.isEmpty())

        println("=== Query 1: hi ===")
        println("URL: ${AiApiService.BASE_URL}api/chat")
        println("HTTP: ${response.code()}")
        println("Answer: ${dto.safeAnswer}")
        println("Destinations: ${dto.safeDestinations.size}")
        println("Web Sources: ${dto.safeWebSources.size}")
    }

    @Test
    fun testQuery2_BelagaviFort() = runBlocking {
        val query = "Tell me about Belagavi Fort"
        val request = ChatRequestDto(
            message = query,
            history = listOf(ChatMessageDto("assistant", "Hello! I am your Belagavi Tourism assistant."))
        )
        val response = apiService.sendChatMessage(request)

        assertTrue("HTTP status must be successful", response.isSuccessful)
        assertEquals(200, response.code())

        val body = response.body()
        assertNotNull("Response body must not be null", body)
        val dto = body!!

        assertTrue("Answer must not be empty", dto.safeAnswer.isNotBlank())
        assertTrue("Answer should contain 'Fort'", dto.safeAnswer.contains("Fort", ignoreCase = true))
        assertNotNull("Destinations list exists", dto.safeDestinations)

        println("=== Query 2: Tell me about Belagavi Fort ===")
        println("HTTP: ${response.code()}")
        println("Answer: ${dto.safeAnswer.take(120)}...")
        println("Destinations: ${dto.safeDestinations.map { it.safeName }}")
        println("Web Research: ${dto.webResearchUsed}")
    }

    @Test
    fun testQuery3_SadaFallsHospitals() = runBlocking {
        val query = "I'm at Sada Falls, suggest nearby hospitals"
        val request = ChatRequestDto(message = query, history = emptyList())
        val response = apiService.sendChatMessage(request)

        assertTrue("HTTP status must be successful", response.isSuccessful)
        assertEquals(200, response.code())

        val body = response.body()
        assertNotNull("Response body must not be null", body)
        val dto = body!!

        assertTrue("Answer must not be empty", dto.safeAnswer.isNotBlank())
        assertTrue("Web research should be triggered for hospital query", dto.webResearchUsed == true)
        assertTrue("Web sources should be populated", dto.safeWebSources.isNotEmpty())

        println("=== Query 3: I'm at Sada Falls, suggest nearby hospitals ===")
        println("HTTP: ${response.code()}")
        println("Answer: ${dto.safeAnswer.take(150)}...")
        println("Web Sources count: ${dto.safeWebSources.size}")
        println("Web Sources: ${dto.safeWebSources.map { it.safeDomain }}")
    }

    @Test
    fun testQuery4_IsBelagaviFortOpenNow() = runBlocking {
        val query = "Is Belagavi Fort open now?"
        val request = ChatRequestDto(message = query, history = emptyList())
        val response = apiService.sendChatMessage(request)

        assertTrue("HTTP status must be successful", response.isSuccessful)
        assertEquals(200, response.code())

        val body = response.body()
        assertNotNull("Response body must not be null", body)
        val dto = body!!

        assertTrue("Answer must not be empty", dto.safeAnswer.isNotBlank())
        assertTrue("Web research should be used for real-time timing", dto.webResearchUsed == true)
        assertTrue("Web sources should be present", dto.safeWebSources.isNotEmpty())

        println("=== Query 4: Is Belagavi Fort open now? ===")
        println("HTTP: ${response.code()}")
        println("Answer: ${dto.safeAnswer.take(150)}...")
        println("Web Sources: ${dto.safeWebSources.map { it.safeTitle }}")
    }

    @Test
    fun testQuery5_CurrentWeather() = runBlocking {
        val query = "What is the current weather in Belagavi?"
        val request = ChatRequestDto(message = query, history = emptyList())
        val response = apiService.sendChatMessage(request)

        assertTrue("HTTP status must be successful", response.isSuccessful)
        assertEquals(200, response.code())

        val body = response.body()
        assertNotNull("Response body must not be null", body)
        val dto = body!!

        assertTrue("Answer must not be empty", dto.safeAnswer.isNotBlank())
        assertTrue("Web research should be used for weather", dto.webResearchUsed == true)
        assertTrue("Web sources should be present", dto.safeWebSources.isNotEmpty())

        println("=== Query 5: What is the current weather in Belagavi? ===")
        println("HTTP: ${response.code()}")
        println("Answer: ${dto.safeAnswer.take(150)}...")
        println("Web Sources count: ${dto.safeWebSources.size}")
    }
}
