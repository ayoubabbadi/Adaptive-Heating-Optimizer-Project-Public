package com.example.myapplication

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "AdaptiveHeatPrefs"
        private const val KEY_TEMP = "last_temp"
        private const val KEY_PRESENCE = "last_presence"
        private const val KEY_HEATER_STATUS = "last_heater_status"
        private const val KEY_HUMIDITY = "last_humidity"
        private const val KEY_TARGET = "last_target"
        private const val KEY_HABIT = "last_habit"
    }

    fun saveData(topic: String, payload: String) {
        with(prefs.edit()) {
            when (topic) {
                MqttTopics.TEMP -> putString(KEY_TEMP, payload)
                MqttTopics.PRESENCE -> putString(KEY_PRESENCE, payload)
                MqttTopics.HEATER_STATUS -> putString(KEY_HEATER_STATUS, payload)
                MqttTopics.HUMIDITY -> putString(KEY_HUMIDITY, payload)
                MqttTopics.TARGET -> putString(KEY_TARGET, payload)
                MqttTopics.HABIT -> putString(KEY_HABIT, payload)
            }
            apply()
        }
    }

    fun getLastTemp(defaultValue: String): String = prefs.getString(KEY_TEMP, defaultValue) ?: defaultValue
    fun getLastPresence(defaultValue: String): String = prefs.getString(KEY_PRESENCE, defaultValue) ?: defaultValue
    fun getLastHeaterStatus(defaultValue: String): String = prefs.getString(KEY_HEATER_STATUS, defaultValue) ?: defaultValue
    fun getLastHumidity(defaultValue: String): String = prefs.getString(KEY_HUMIDITY, defaultValue) ?: defaultValue
    fun getLastTarget(defaultValue: String): String = prefs.getString(KEY_TARGET, defaultValue) ?: defaultValue
    fun getLastHabit(defaultValue: String): String = prefs.getString(KEY_HABIT, defaultValue) ?: defaultValue
}
