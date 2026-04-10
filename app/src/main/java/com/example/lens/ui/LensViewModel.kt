package com.example.lens.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lens.api.TranslationService
import com.example.lens.data.Config
import com.example.lens.data.ConfigStore
import com.example.lens.data.HistoryItem
import com.example.lens.data.TranslationProvider
import com.example.lens.data.TranslationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class LensViewModel(private val configStore: ConfigStore) : ViewModel() {

    private val translationService = TranslationService()

    private val _config = MutableStateFlow(configStore.getConfig())
    val config: StateFlow<Config> = _config.asStateFlow()

    private val _translationResult = MutableStateFlow(TranslationResult())
    val translationResult: StateFlow<TranslationResult> = _translationResult.asStateFlow()

    private val _explanation = MutableStateFlow<String?>(null)
    val explanation: StateFlow<String?> = _explanation.asStateFlow()

    private val _history = MutableStateFlow(configStore.getHistory())
    val history: StateFlow<List<HistoryItem>> = _history.asStateFlow()

    fun updateConfig(newConfig: Config) {
        _config.value = newConfig
        configStore.saveConfig(newConfig)
    }

    private fun addToHistory(original: String, translated: String) {
        val item = HistoryItem(originalText = original, translatedText = translated)
        configStore.addHistoryItem(item)
        _history.value = configStore.getHistory()
    }

    fun translateText(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _translationResult.update { it.copy(isLoading = true, error = null) }
            try {
                val result = when (_config.value.preferredProvider) {
                    TranslationProvider.GEMINI -> translationService.translateWithGemini(text, null, null, _config.value)
                    TranslationProvider.GROQ -> translationService.translateWithGroq(text, _config.value)
                    TranslationProvider.OPENROUTER -> translationService.translateWithOpenRouter(text, _config.value)
                }
                _translationResult.update { it.copy(translatedText = result, isLoading = false) }
                addToHistory(text, result)
            } catch (e: Exception) {
                _translationResult.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun translateImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _translationResult.update { it.copy(isLoading = true, error = null) }
            try {
                val result = when (_config.value.preferredProvider) {
                    TranslationProvider.GEMINI -> translationService.translateWithGemini("", bitmap, null, _config.value)
                    TranslationProvider.GROQ -> translationService.translateImageWithGroq(bitmap, _config.value)
                    TranslationProvider.OPENROUTER -> translationService.translateImageWithOpenRouter(bitmap, _config.value)
                }
                _translationResult.update { it.copy(translatedText = result, isLoading = false) }
                addToHistory("[Image]", result)
            } catch (e: Exception) {
                _translationResult.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun translateAudio(file: File) {
        viewModelScope.launch {
            _translationResult.update { it.copy(isLoading = true, error = null) }
            try {
                val result = when (_config.value.preferredProvider) {
                    TranslationProvider.GEMINI -> translationService.translateWithGemini("", null, file, _config.value)
                    TranslationProvider.GROQ -> translationService.translateAudioWithGroq(file, _config.value)
                    TranslationProvider.OPENROUTER -> translationService.translateAudioWithOpenRouter(file, _config.value)
                }
                _translationResult.update { it.copy(translatedText = result, isLoading = false) }
                addToHistory("[Audio]", result)
            } catch (e: Exception) {
                _translationResult.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun explainWord(word: String) {
        if (word.isBlank()) return
        viewModelScope.launch {
            _explanation.value = "Loading explanation..."
            try {
                val context = _translationResult.value.translatedText
                val result = translationService.explainWord(word, context, _config.value)
                _explanation.value = result
            } catch (e: Exception) {
                _explanation.value = "Error: ${e.message}"
            }
        }
    }

    fun selectHistoryItem(item: HistoryItem) {
        _translationResult.update { it.copy(translatedText = item.translatedText, isLoading = false, error = null) }
    }

    fun clearExplanation() {
        _explanation.value = null
    }

    fun deleteHistoryItem(id: Long) {
        configStore.removeHistoryItem(id)
        _history.value = configStore.getHistory()
    }

    fun clearHistory() {
        configStore.saveHistory(emptyList())
        _history.value = emptyList()
    }
}
