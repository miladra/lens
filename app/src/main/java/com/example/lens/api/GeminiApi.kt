package com.example.lens.api

import retrofit2.http.GET
import retrofit2.http.Query

data class GeminiModelListResponse(
    val models: List<GeminiModelInfo>
)

data class GeminiModelInfo(
    val name: String,
    val version: String,
    val displayName: String,
    val description: String,
    val supportedGenerationMethods: List<String>
)

interface GeminiApi {
    @GET("v1/models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): GeminiModelListResponse
}
