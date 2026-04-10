package com.example.lens.data

enum class TranslationProvider {
    GEMINI, GROQ, OPENROUTER
}

data class TranslationResult(
    val translatedText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class Config(
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val openRouterApiKey: String = "",
    val geminiModel: String = "gemini-2.0-flash",
    val groqModel: String = "llama-3.2-11b-vision-preview",
    val openRouterModel: String = "google/gemini-2.0-flash-001",
    val targetLanguage: String = "English",
    val explanationLanguage: String = "Farsi",
    val preferredProvider: TranslationProvider = TranslationProvider.GEMINI
)

data class HistoryItem(
    val id: Long = System.currentTimeMillis(),
    val originalText: String = "",
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)
