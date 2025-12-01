package com.example.myapplication

import android.content.Context
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.MqttClient

object MqttData {
    var client: MqttAndroidClient? = null

    fun getInstance(context: Context, serverURI: String): MqttAndroidClient {
        if (client == null) {
            client = MqttAndroidClient(context.applicationContext, serverURI, MqttClient.generateClientId())
        }
        return client!!
    }
}