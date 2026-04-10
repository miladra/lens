package com.example.lens.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

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

data class GroqTranscriptionResponse(
    val text: String
)

interface GroqApi {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") apiKey: String,
        @Body request: GroqRequest
    ): GroqResponse

    @Multipart
    @POST("v1/audio/translations")
    suspend fun translateAudio(
        @Header("Authorization") apiKey: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody? = null,
        @Part("prompt") prompt: RequestBody? = null,
        @Part("temperature") temperature: RequestBody? = null
    ): GroqTranscriptionResponse
}
