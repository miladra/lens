package com.example.lens.api

import android.graphics.Bitmap
import android.util.Log
import com.example.lens.data.Config
import com.example.lens.data.TranslationProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

class TranslationService {

    private fun getRetrofit(): Retrofit {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
    }

    private val groqApi = getRetrofit().create(GroqApi::class.java)

    suspend fun translateWithGemini(
        text: String,
        bitmap: Bitmap?,
        audioFile: File?,
        config: Config
    ): String {
        val generativeModel = GenerativeModel(
            modelName = config.geminiModel,
            apiKey = config.geminiApiKey
        )

        val prompt = when {
            audioFile != null -> "Transcribe and translate the following audio into ${config.targetLanguage}. Detect the source language automatically. Only provide the translated text."
            bitmap != null -> "Translate the text in this image into ${config.targetLanguage}. Detect the source language automatically. Only provide the translated text."
            else -> "Translate the following text into ${config.targetLanguage}: $text. Detect the source language automatically. Only provide the translated text."
        }

        val response = if (audioFile != null) {
            if (!audioFile.exists()) {
                throw Exception("Audio file not found at ${audioFile.absolutePath}")
            }
            val audioBytes = audioFile.readBytes()
            Log.d("TranslationService", "Audio file size: ${audioBytes.size} bytes")
            
            // 44 bytes is the size of an empty WAV header. If it's this small, nothing was recorded.
            if (audioBytes.size <= 44) {
                throw Exception("Recorded audio is empty. Please ensure you granted microphone permissions and that there was sound to capture.")
            }
            
            generativeModel.generateContent(content {
                blob("audio/wav", audioBytes)
                text(prompt)
            })
        } else if (bitmap != null) {
            generativeModel.generateContent(content {
                image(bitmap)
                text(prompt)
            })
        } else {
            generativeModel.generateContent(prompt)
        }

        return response.text ?: "Translation failed"
    }

    suspend fun translateWithGroq(
        text: String,
        config: Config
    ): String {
        val prompt = "Translate the following text into ${config.targetLanguage}: $text. Detect the source language automatically. Only provide the translated text."
        val request = GroqRequest(
            model = config.groqModel,
            messages = listOf(
                GroqMessage(role = "system", content = "You are a translator. Automatically detect the source language and translate into the target language."),
                GroqMessage(role = "user", content = prompt)
            )
        )
        val response = groqApi.getCompletion("Bearer ${config.groqApiKey}", request)
        return response.choices.firstOrNull()?.message?.content ?: "Translation failed"
    }

    suspend fun explainWord(
        word: String,
        context: String,
        config: Config
    ): String {
        return if (config.preferredProvider == TranslationProvider.GEMINI) {
            explainWithGemini(word, context, config)
        } else {
            explainWithGroq(word, context, config)
        }
    }

    private suspend fun explainWithGemini(
        word: String,
        context: String,
        config: Config
    ): String {
        val generativeModel = GenerativeModel(
            modelName = config.geminiModel,
            apiKey = config.geminiApiKey
        )
        val prompt = "Explain the word '$word' in the context of '$context' in ${config.explanationLanguage}. Be concise."
        val response = generativeModel.generateContent(prompt)
        return response.text ?: "Explanation failed"
    }

    private suspend fun explainWithGroq(
        word: String,
        context: String,
        config: Config
    ): String {
        val prompt = "Explain the word '$word' in the context of '$context' in ${config.explanationLanguage}. Be concise."
        val request = GroqRequest(
            model = config.groqModel,
            messages = listOf(
                GroqMessage(role = "system", content = "You are a helpful assistant providing concise word explanations."),
                GroqMessage(role = "user", content = prompt)
            )
        )
        val response = groqApi.getCompletion("Bearer ${config.groqApiKey}", request)
        return response.choices.firstOrNull()?.message?.content ?: "Explanation failed"
    }
}
