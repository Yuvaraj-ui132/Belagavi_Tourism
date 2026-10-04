package com.belagavi.tourism.data.network

import com.belagavi.tourism.data.model.ChatRequestDto
import com.belagavi.tourism.data.model.ChatResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

interface AiApiService {

    @Headers("Content-Type: application/json")
    @POST("api/chat")
    suspend fun sendChatMessage(
        @Body request: ChatRequestDto
    ): Response<ChatResponseDto>

    companion object {
        const val BASE_URL = "https://belagavi-tourism-yuvaraj21.vercel.app/"
    }
}
