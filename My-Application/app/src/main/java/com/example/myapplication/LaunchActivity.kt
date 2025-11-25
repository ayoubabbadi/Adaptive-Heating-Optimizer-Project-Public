package com.example.myapplication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

@SuppressLint("CustomSplashScreen")
class LaunchActivity : AppCompatActivity() {

    private lateinit var statusTextView: TextView
    private lateinit var ipAddressEditText: EditText
    private lateinit var connectButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var connectionTimeoutRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        statusTextView = findViewById(R.id.statusTextView)
        ipAddressEditText = findViewById(R.id.ipAddressEditText)
        connectButton = findViewById(R.id.connectButton)

        connectButton.setOnClickListener {
            val ipAddress = ipAddressEditText.text.toString().trim()
            if (ipAddress.isNotEmpty() && Patterns.IP_ADDRESS.matcher(ipAddress).matches()) {
                startConnectionProcess(ipAddress)
            } else {
                statusTextView.text = "Status: Please enter a valid IP address."
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MqttService.BROADCAST_ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mqttStatusReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(mqttStatusReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(mqttStatusReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e("LaunchActivity", "Receiver not registered", e)
        }
    }

    private fun startConnectionProcess(ipAddress: String) {
        setUIState(connecting = true)

        val serviceIntent = Intent(this, MqttService::class.java)
        serviceIntent.action = MqttService.ACTION_CONNECT
        serviceIntent.putExtra(MqttService.EXTRA_IP_ADDRESS, ipAddress)

        Log.d("AppFlow", "Starting Service directly with IP: $ipAddress")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        connectionTimeoutRunnable = Runnable {
            Log.w("AppFlow", "Connection timed out after 30 seconds.")
            setUIState(connecting = false)
            statusTextView.text = "Status: Connection Failed (Timeout)"
            stopService(Intent(this, MqttService::class.java))
        }
        connectionTimeoutRunnable?.let { handler.postDelayed(it, 30000) }
    }

    private val mqttStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(MqttService.EXTRA_STATUS)
            Log.d("AppFlow", "LaunchActivity received status: $status")

            if (status == MqttService.STATUS_CONNECTED) {
                connectionTimeoutRunnable?.let { handler.removeCallbacks(it) }
                connectionTimeoutRunnable = null

                val mainIntent = Intent(this@LaunchActivity, MainActivity::class.java)
                startActivity(mainIntent)
                finish()
            } else if (status == MqttService.STATUS_FAILED) {
                connectionTimeoutRunnable?.let { handler.removeCallbacks(it) }
                connectionTimeoutRunnable = null
                setUIState(connecting = false)
                statusTextView.text = "Status: Connection Failed"
            }
        }
    }

    private fun setUIState(connecting: Boolean) {
        connectButton.isEnabled = !connecting
        ipAddressEditText.isEnabled = !connecting
        if (connecting) {
            statusTextView.text = "Status: Connecting..."
        }
    }
}