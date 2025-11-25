package com.example.myapplication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView

class MainActivity : AppCompatActivity() {

    private lateinit var tempText: TextView
    private lateinit var presenceText: TextView
    private lateinit var heaterText: TextView
    private lateinit var humidityText: TextView
    private lateinit var targetText: TextView
    private lateinit var habitText: TextView
    private lateinit var myButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var mySwitch: SwitchCompat
    private lateinit var heaterCard: CardView
    private lateinit var targetCard: CardView
    private lateinit var connectionStatusBar: TextView
    private lateinit var prefs: SharedPreferences


    companion object {
        private const val PREFS_NAME = "AdaptiveHeatPrefs"
        private const val KEY_TEMP = "last_temp"
        private const val KEY_PRESENCE = "last_presence"
        private const val KEY_HEATER_STATUS = "last_heater_status"
        private const val KEY_HUMIDITY = "last_humidity"
        private const val KEY_TARGET = "last_target"
        private const val KEY_HABIT = "last_habit"
    }

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(MqttService.EXTRA_STATUS)
            if (status == MqttService.STATUS_MSG_ARRIVED) {
                val topic = intent.getStringExtra(MqttService.EXTRA_DATA_TOPIC)
                val payload = intent.getStringExtra(MqttService.EXTRA_DATA_PAYLOAD)
                saveData(topic, payload)

                when (topic) {
                    MqttTopics.TEMP -> tempText.text = getString(R.string.temperature_format, payload)
                    MqttTopics.PRESENCE -> presenceText.text = payload
                    MqttTopics.HUMIDITY -> humidityText.text = getString(R.string.humidity_format, payload)
                    MqttTopics.TARGET -> targetText.text = getString(R.string.temperature_format, payload)
                    MqttTopics.HABIT -> habitText.text = payload
                    MqttTopics.HEATER_STATUS -> {
                        heaterText.text = payload
                        // Only update the visual card based on HEATER_STATUS
                        if (payload == "ON") {
                            heaterCard.setCardBackgroundColor(getColor(R.color.accent_hot))
                        } else {
                            heaterCard.setCardBackgroundColor(getColor(R.color.surface))
                        }
                        // CRITICAL FIX: We DO NOT touch mySwitch.isChecked here.
                    }
                }
            } else if (status == MqttService.STATUS_FAILED) {
                updateConnectionStatus(false)
            } else if (status == MqttService.STATUS_CONNECTED) {
                updateConnectionStatus(true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Initialize Views
        tempText = findViewById(R.id.temperature_text)
        presenceText = findViewById(R.id.presence_text)
        heaterText = findViewById(R.id.heater_text)
        humidityText = findViewById(R.id.humidity_text)
        targetText = findViewById(R.id.target_text)
        habitText = findViewById(R.id.habit_text)

        myButton = findViewById(R.id.analyze_button)
        disconnectButton = findViewById(R.id.disconnect_button)

        mySwitch = findViewById(R.id.manual_switch)
        heaterCard = findViewById(R.id.heater_card)
        targetCard = findViewById(R.id.target_temp_card)
        connectionStatusBar = findViewById(R.id.connection_status_bar)

        loadData()

        // Apply initial color based on the switch state
        updateSwitchColor(mySwitch.isChecked)

        // --- Button Listeners ---

        myButton.setOnClickListener {
            publish(MqttTopics.START_ANALYSIS, "start")
            Toast.makeText(this, getString(R.string.starting_new_analysis), Toast.LENGTH_SHORT).show()
        }

        // DISCONNECT LOGIC
        disconnectButton.setOnClickListener {
            // 1. Stop the Service (Kill MQTT)
            val serviceIntent = Intent(this, MqttService::class.java)
            stopService(serviceIntent)

            // 2. Navigate back to Launch Screen
            val intent = Intent(this, LaunchActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // --- MANUAL SWITCH LISTENER WITH COLOR CHANGE ---
        mySwitch.setOnCheckedChangeListener { view, isChecked ->
            val message = if (isChecked) "ON" else "OFF"

            // 1. Send command (ON/OFF)
            publish(MqttTopics.COMMAND, message)

            // 2. Change visual color of the switch
            updateSwitchColor(isChecked)

            // 3. Provide toast feedback
            val toastMessageResId = if (isChecked) R.string.manual_control_on else R.string.manual_control_off
            Toast.makeText(this, getString(toastMessageResId), Toast.LENGTH_SHORT).show()
        }

        targetCard.setOnClickListener {
            showEditTempDialog()
        }

        // Initial State Check
        val isConnected = MqttData.client != null && MqttData.client!!.isConnected
        if (isConnected) updateConnectionStatus(true) else updateConnectionStatus(null)
    }

    // Helper function to change switch color cleanly
    private fun updateSwitchColor(isChecked: Boolean) {
        if (isChecked) {
            // ON: Set to accent_hot (Orange)
            val color = getColor(R.color.accent_hot)
            mySwitch.thumbTintList = ColorStateList.valueOf(color)
            mySwitch.trackTintList = ColorStateList.valueOf(color)
        } else {
            // OFF: Set to Grey
            val grayTrack = Color.argb(128, 128, 128, 128) // Light semi-transparent gray
            val grayThumb = Color.parseColor("#EEEEEE") // Almost white thumb for contrast
            mySwitch.thumbTintList = ColorStateList.valueOf(grayThumb)
            mySwitch.trackTintList = ColorStateList.valueOf(grayTrack)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(MqttService.BROADCAST_ACTION_STATUS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(dataReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(dataReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(dataReceiver) } catch (e: Exception) {}
    }

    private fun showEditTempDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_edit_temp, null)
        val newTempInput: EditText = dialogView.findViewById(R.id.new_temp_input)
        val saveButton: Button = dialogView.findViewById(R.id.save_temp_button)

        val currentTarget = targetText.text.toString().replace("°C", "").replace(getString(R.string.offline_placeholder),"")
        newTempInput.setText(currentTarget)

        builder.setView(dialogView)
        val dialog = builder.create()

        saveButton.setOnClickListener {
            val newTempStr = newTempInput.text.toString()
            if (newTempStr.isNotBlank()) {
                try {
                    val newTemp = newTempStr.toFloat()
                    if (newTemp in 10.0..35.0) {
                        publish(MqttTopics.SET_TEMP, newTempStr)
                        targetText.text = getString(R.string.temperature_format, newTempStr)
                        saveData(MqttTopics.TARGET, newTempStr)
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this, getString(R.string.invalid_temp_range), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: NumberFormatException) {
                    Toast.makeText(this, getString(R.string.invalid_number_format), Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, getString(R.string.please_enter_value), Toast.LENGTH_SHORT).show()
            }
        }
        dialog.show()
    }

    private fun publish(topic: String, message: String) {
        if (MqttData.client != null && MqttData.client?.isConnected == true) {
            val intent = Intent(MqttService.ACTION_PUBLISH)
            intent.putExtra(MqttService.EXTRA_TOPIC, topic)
            intent.putExtra(MqttService.EXTRA_MESSAGE, message)
            sendBroadcast(intent)
        } else {
            Toast.makeText(this, getString(R.string.not_connected_toast), Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateConnectionStatus(isConnected: Boolean?) {
        when (isConnected) {
            true -> {
                connectionStatusBar.text = getString(R.string.connection_status_connected)
                connectionStatusBar.setBackgroundColor(getColor(R.color.status_green))
                setControlsEnabled(true)
            }
            false -> {
                connectionStatusBar.text = getString(R.string.connection_status_disconnected)
                connectionStatusBar.setBackgroundColor(getColor(R.color.status_red))
                setControlsEnabled(false)
            }
            null -> {
                connectionStatusBar.text = getString(R.string.connection_status_connecting)
                connectionStatusBar.setBackgroundColor(getColor(android.R.color.darker_gray))
                setControlsEnabled(false)
            }
        }
    }

    private fun setControlsEnabled(isEnabled: Boolean) {
        mySwitch.isEnabled = isEnabled
        myButton.isEnabled = isEnabled
        targetCard.isEnabled = isEnabled
        targetCard.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun saveData(topic: String?, payload: String?) {
        if (topic == null || payload == null) return
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

    private fun loadData() {
        val placeholder = getString(R.string.offline_placeholder)
        tempText.text = getString(R.string.temperature_format, prefs.getString(KEY_TEMP, placeholder))
        presenceText.text = prefs.getString(KEY_PRESENCE, placeholder)
        heaterText.text = prefs.getString(KEY_HEATER_STATUS, placeholder)
        humidityText.text = getString(R.string.humidity_format, prefs.getString(KEY_HUMIDITY, placeholder))
        targetText.text = getString(R.string.temperature_format, prefs.getString(KEY_TARGET, placeholder))
        habitText.text = prefs.getString(KEY_HABIT, placeholder)
    }
}