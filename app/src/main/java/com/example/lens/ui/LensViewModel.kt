package com.example.lens.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lens.api.TranslationService
import com.example.lens.data.Config
import com.example.lens.data.ConfigStore
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

    fun updateConfig(newConfig: Config) {
        _config.value = newConfig
        configStore.saveConfig(newConfig)
    }

    fun translateText(text: String) {
        if (text.isBlank()) return
        
        viewModelScope.launch {
            _translationResult.update { it.copy(isLoading = true, error = null) }
            try {
                val useGroq = _config.value.preferredProvider == TranslationProvider.GROQ
                val result = if (useGroq) {
                    translationService.translateWithGroq(text, _config.value)
                } else {
                    translationService.translateWithGemini(text, null, null, _config.value)
                }
                _translationResult.update { it.copy(translatedText = result, isLoading = false) }
            } catch (e: Exception) {
                _translationResult.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun translateImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _translationResult.update { it.copy(isLoading = true, error = null) }
            try {
                val result = translationService.translateWithGemini("", bitmap, null, _config.value)
                _translationResult.update { it.copy(translatedText = result, isLoading = false) }
            } catch (e: Exception) {
                _translationResult.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun translateAudio(file: File) {
        viewModelScope.launch {
            _translationResult.update { it.copy(isLoading = true, error = null) }
            try {
                val result = translationService.translateWithGemini("", null, file, _config.value)
                _translationResult.update { it.copy(translatedText = result, isLoading = false) }
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

    fun clearExplanation() {
        _explanation.value = null
    }
}
