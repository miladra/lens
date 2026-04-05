package com.example.lens.api

import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

data class GroqRequest(
    val model: String,
    val messages: List<GroqMessage>
)

data class GroqMessage(
    val role: String,
    val content: Any // Can be String or List<GroqContent>
)

data class GroqContent(
    val type: String,
    val text: String? = null,
    val image_url: GroqImageUrl? = null
)

data class GroqImageUrl(
    val url: String
)

data class GroqResponse(
    val choices: List<GroqChoice>
)

data class GroqChoice(
    val message: GroqResponseMessage
)

data class GroqResponseMessage(
    val role: String,
    val content: String
)

interface GroqApi {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: GroqRequest
    ): GroqResponse
}
