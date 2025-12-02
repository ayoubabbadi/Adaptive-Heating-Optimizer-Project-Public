package com.example.myapplication

import android.annotation.SuppressLint
import android.content.Context

import info.mqtt.android.service.MqttAndroidClient
import info.mqtt.android.service.Ack

import org.eclipse.paho.client.mqttv3.MqttClient

object MqttData {

    @SuppressLint("StaticFieldLeak")
    var client: MqttAndroidClient? = null

    fun getInstance(context: Context, serverURI: String): MqttAndroidClient {
        if (client == null) {

            client = MqttAndroidClient(
                context.applicationContext,
                serverURI,
                MqttClient.generateClientId(),
                Ack.AUTO_ACK
            )
        }
        return client!!
    }
}