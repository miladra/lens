package com.example.lens.data

enum class TranslationProvider {
    GEMINI, GROQ
}

data class TranslationResult(
    val translatedText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class Config(
    val geminiApiKey: String = "",
    val groqApiKey: String = "",
    val geminiModel: String = "gemini-2.5-flash",
    val groqModel: String = "llama-3.3-70b-versatile",
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
