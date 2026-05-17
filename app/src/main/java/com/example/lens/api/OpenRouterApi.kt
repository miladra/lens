package com.example.lens.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>
)

data class OpenRouterMessage(
    val role: String,
    val content: Any // Can be String or List<OpenRouterContent>
)

data class OpenRouterContent(
    val type: String,
    val text: String? = null,
    val image_url: OpenRouterImageUrl? = null
)

data class OpenRouterImageUrl(
    val url: String
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

data class OpenRouterTranscriptionResponse(
    val text: String
)

interface OpenRouterApi {
    @POST("chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") apiKey: String,
        @Header("HTTP-Referer") referer: String = "AiLens",
        @Header("X-Title") title: String = "AiLens",
        @Body request: OpenRouterRequest
    ): OpenRouterResponse

    @Multipart
    @POST("audio/translations")
    suspend fun translateAudio(
        @Header("Authorization") apiKey: String,
        @Header("HTTP-Referer") referer: String = "AiLens",
        @Header("X-Title") title: String = "AiLens",
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): OpenRouterTranscriptionResponse
}
