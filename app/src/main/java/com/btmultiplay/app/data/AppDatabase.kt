package com.btmultiplay.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AppDatabase(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("bt_multiplay_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun loadAll(): MutableMap<String, SavedDevice> {
        val json = prefs.getString(KEY_DEVICES, null) ?: return mutableMapOf()
        val type = object : TypeToken<Map<String, SavedDevice>>() {}.type
        return gson.fromJson(json, type) ?: mutableMapOf()
    }

    fun saveAll(devices: Map<String, SavedDevice>) {
        prefs.edit().putString(KEY_DEVICES, gson.toJson(devices)).apply()
    }

    companion object {
        private const val KEY_DEVICES = "saved_devices"

        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppDatabase(context.applicationContext).also { INSTANCE = it }
            }
    }
}
