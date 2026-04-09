package com.example.lens.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>
)

data class OpenRouterMessage(
    val role: String,
    val content: String
)

data class OpenRouterResponse(
    val choices: List<OpenRouterChoice>
)

data class OpenRouterChoice(
    val message: OpenRouterResponseMessage
)

data class OpenRouterResponseMessage(
    val role: String,
    val content: String
)

interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") apiKey: String,
        @Header("HTTP-Referer") referer: String = "Lens",
        @Header("X-Title") title: String = "Lens",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse
}
