package com.example.lens.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ConfigStore(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("ailens_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveConfig(config: Config) {
        val json = gson.toJson(config)
        sharedPreferences.edit().putString("config", json).apply()
    }

    fun getConfig(): Config {
        val json = sharedPreferences.getString("config", null)
        return if (json != null) {
            gson.fromJson(json, Config::class.java)
        } else {
            Config()
        }
    }

    fun saveHistory(history: List<HistoryItem>) {
        val json = gson.toJson(history)
        sharedPreferences.edit().putString("history", json).apply()
    }

    fun getHistory(): List<HistoryItem> {
        val json = sharedPreferences.getString("history", null)
        return if (json != null) {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            gson.fromJson(json, type)
        } else {
            emptyList()
        }
    }

    fun addHistoryItem(item: HistoryItem) {
        val currentHistory = getHistory().toMutableList()
        currentHistory.add(0, item)
        // Keep only last 50 items
        val limitedHistory = currentHistory.take(50)
        saveHistory(limitedHistory)
    }

    fun removeHistoryItem(id: Long) {
        val currentHistory = getHistory().toMutableList()
        currentHistory.removeAll { it.id == id }
        saveHistory(currentHistory)
    }
}
