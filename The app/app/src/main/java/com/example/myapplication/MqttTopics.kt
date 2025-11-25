package com.example.myapplication

object MqttTopics {
    const val TEMP = "chauffage/etat/temperature"
    const val HUMIDITY = "chauffage/etat/humidite"
    const val PRESENCE = "chauffage/etat/presence"

    const val HEATER_STATUS = "chauffage/etat/statutChauffage"
    const val ESP32_STATUS = "chauffage/etat/esp32_status"
    const val APP_STATUS = "chauffage/etat/app_status"
    const val HABIT = "chauffage/etat/habit"

    const val COMMAND = "chauffage/commande/set"

    const val SET_TEMP = "chauffage/etat/target"

    const val START_ANALYSIS = "chauffage/commande/start_analysis"
    const val TARGET = "chauffage/etat/target"
    const val ALERT = "chauffage/alert"
}