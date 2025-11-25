package com.example.myapplication

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.eclipse.paho.android.service.MqttAndroidClient
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage

class MqttService : Service() {

    private var mqttClient: MqttAndroidClient? = null
    private val notificationId = 1
    private val alertNotificationId = 2 
    private val channelId = "MqttServiceChannel"

    companion object {
        const val ACTION_CONNECT = "com.example.myapplication.CONNECT"
        const val ACTION_DISCONNECT = "com.example.myapplication.DISCONNECT"
        const val ACTION_PUBLISH = "com.example.myapplication.PUBLISH"

        const val EXTRA_IP_ADDRESS = "com.example.myapplication.EXTRA_IP"
        const val EXTRA_TOPIC = "com.example.myapplication.EXTRA_TOPIC"
        const val EXTRA_MESSAGE = "com.example.myapplication.EXTRA_MESSAGE"

        const val BROADCAST_ACTION_STATUS = "com.example.myapplication.MQTT_STATUS"
        const val EXTRA_STATUS = "com.example.myapplication.EXTRA_STATUS"
        const val EXTRA_DATA_TOPIC = "com.example.myapplication.EXTRA_DATA_TOPIC"
        const val EXTRA_DATA_PAYLOAD = "com.example.myapplication.EXTRA_DATA_PAYLOAD"

        const val STATUS_CONNECTED = "CONNECTED"
        const val STATUS_FAILED = "CONNECTION_FAILED"
        const val STATUS_MSG_ARRIVED = "MESSAGE_ARRIVED"
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ACTION_CONNECT)
            addAction(ACTION_PUBLISH)
            addAction(ACTION_DISCONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mqttCommandReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(mqttCommandReceiver, filter)
        }
    }

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, createNotification(getString(R.string.notification_waiting)))

        if (intent != null && intent.action == ACTION_CONNECT) {
            val ip = intent.getStringExtra(EXTRA_IP_ADDRESS)
            if (ip != null) {
                connect(ip)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(mqttCommandReceiver)
        } catch (e: Exception) {}
        disconnect()
    }

    override fun onBind(intent: Intent): IBinder? = null

    private val mqttCommandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_CONNECT -> {
                    val ip = intent.getStringExtra(EXTRA_IP_ADDRESS)
                    ip?.let { connect(it) }
                }
                ACTION_PUBLISH -> {
                    val topic = intent.getStringExtra(EXTRA_TOPIC)
                    val message = intent.getStringExtra(EXTRA_MESSAGE)
                    if (topic != null && message != null) {
                        publish(topic, message)
                    }
                }
                ACTION_DISCONNECT -> disconnect()
            }
        }
    }

    private fun connect(ipAddress: String) {
        val cleanIp = ipAddress.trim()
        val serverUri = "tcp://$cleanIp:1883"

        if (mqttClient != null && mqttClient!!.isConnected) return

        val clientId = MqttClient.generateClientId()
        MqttData.client = MqttAndroidClient(applicationContext, serverUri, clientId)
        mqttClient = MqttData.client

        mqttClient?.setCallback(object : MqttCallbackExtended {
            override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                subscribeToTopics()
                publish(MqttTopics.APP_STATUS, "ONLINE", retain = true)
                updateNotification(getString(R.string.notification_connected))
                sendBroadcastMessage(STATUS_CONNECTED, null)
            }

            override fun connectionLost(cause: Throwable?) {
                updateNotification(getString(R.string.notification_disconnected))
                sendBroadcastMessage(STATUS_FAILED, cause?.message)
            }

            override fun messageArrived(topic: String, message: MqttMessage) {
                val payload = String(message.payload)

                if (topic == MqttTopics.ALERT) {
                    showSystemAlert()
                }

                val data = Bundle().apply {
                    putString(EXTRA_DATA_TOPIC, topic)
                    putString(EXTRA_DATA_PAYLOAD, payload)
                }
                sendBroadcastMessage(STATUS_MSG_ARRIVED, data)
            }

            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
        })

        val options = MqttConnectOptions().apply {
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 20
            setWill(MqttTopics.APP_STATUS, "OFFLINE".toByteArray(), 1, true)
        }

        try {
            mqttClient?.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {}
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    sendBroadcastMessage(STATUS_FAILED, exception?.message)
                }
            })
        } catch (e: Exception) {
            sendBroadcastMessage(STATUS_FAILED, e.message)
        }
    }

    private fun subscribeToTopics() {
        try {
            val topics = arrayOf(
                MqttTopics.TEMP, MqttTopics.PRESENCE, MqttTopics.HUMIDITY,
                MqttTopics.HEATER_STATUS, MqttTopics.TARGET, MqttTopics.HABIT,
                MqttTopics.ALERT 
            )
            val qos = IntArray(topics.size) { 1 }
            mqttClient?.subscribe(topics, qos)
        } catch (e: MqttException) {
            e.printStackTrace()
        }
    }

    private fun publish(topic: String, message: String, retain: Boolean = false) {
        if (mqttClient?.isConnected == true) {
            try {
                val mqttMessage = MqttMessage(message.toByteArray()).apply { isRetained = retain }
                mqttClient?.publish(topic, mqttMessage)
            } catch (e: MqttException) {
                e.printStackTrace()
            }
        }
    }

    private fun disconnect() {
        try {
            if (mqttClient?.isConnected == true) mqttClient?.disconnect()
        } catch (e: MqttException) {
            e.printStackTrace()
        }
        MqttData.client = null
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId, "MQTT Service Channel", NotificationManager.IMPORTANCE_HIGH 
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AdaptiveHeat Optimizer")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showSystemAlert() {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.alert_title))
            .setContentText(getString(R.string.alert_message))
            .setSmallIcon(R.mipmap.ic_launcher) 
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(alertNotificationId, notification)
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, createNotification(text))
    }

    private fun sendBroadcastMessage(status: String, data: Any?) {
        val intent = Intent(BROADCAST_ACTION_STATUS).apply {
            putExtra(EXTRA_STATUS, status)
            when (data) {
                is String? -> putExtra(EXTRA_DATA_PAYLOAD, data)
                is Bundle? -> putExtras(data)
            }
        }
        sendBroadcast(intent)
    }
}