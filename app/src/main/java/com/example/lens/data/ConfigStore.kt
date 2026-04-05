package com.example.lens.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class ConfigStore(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("lens_prefs", Context.MODE_PRIVATE)
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
}
