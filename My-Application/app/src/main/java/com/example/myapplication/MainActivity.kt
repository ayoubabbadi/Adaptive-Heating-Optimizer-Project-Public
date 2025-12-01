package com.example.myapplication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
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
    private lateinit var analyzeButton: Button
    private lateinit var disconnectButton: Button
    private lateinit var manualControlSwitch: SwitchCompat
    private lateinit var heaterCard: CardView
    private lateinit var targetCard: CardView
    private lateinit var connectionStatusBar: TextView
    
    private lateinit var preferencesManager: PreferencesManager

    private val dataReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val status = intent?.getStringExtra(MqttService.EXTRA_STATUS)
            if (status == MqttService.STATUS_MSG_ARRIVED) {
                val topic = intent.getStringExtra(MqttService.EXTRA_DATA_TOPIC)
                val payload = intent.getStringExtra(MqttService.EXTRA_DATA_PAYLOAD)
                
                if (topic != null && payload != null) {
                    preferencesManager.saveData(topic, payload)
                    updateUI(topic, payload)
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

        preferencesManager = PreferencesManager(this)

        initializeViews()
        loadData()
        setupListeners()

        val isConnected = MqttData.client != null && MqttData.client!!.isConnected
        if (isConnected) updateConnectionStatus(true) else updateConnectionStatus(null)
    }

    private fun initializeViews() {
        tempText = findViewById(R.id.temperature_text)
        presenceText = findViewById(R.id.presence_text)
        heaterText = findViewById(R.id.heater_text)
        humidityText = findViewById(R.id.humidity_text)
        targetText = findViewById(R.id.target_text)
        habitText = findViewById(R.id.habit_text)

        analyzeButton = findViewById(R.id.analyze_button)
        disconnectButton = findViewById(R.id.disconnect_button)

        manualControlSwitch = findViewById(R.id.manual_switch)
        heaterCard = findViewById(R.id.heater_card)
        targetCard = findViewById(R.id.target_temp_card)
        connectionStatusBar = findViewById(R.id.connection_status_bar)
        
        updateSwitchColor(manualControlSwitch.isChecked)
    }

    private fun setupListeners() {
        analyzeButton.setOnClickListener {
            publish(MqttTopics.START_ANALYSIS, "start")
            Toast.makeText(this, getString(R.string.starting_new_analysis), Toast.LENGTH_SHORT).show()
        }

        disconnectButton.setOnClickListener {
            val serviceIntent = Intent(this, MqttService::class.java)
            stopService(serviceIntent)

            val intent = Intent(this, LaunchActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        manualControlSwitch.setOnCheckedChangeListener { _, isChecked ->
            val message = if (isChecked) "ON" else "OFF"
            publish(MqttTopics.COMMAND, message)
            updateSwitchColor(isChecked)
            val toastMessageResId = if (isChecked) R.string.manual_control_on else R.string.manual_control_off
            Toast.makeText(this, getString(toastMessageResId), Toast.LENGTH_SHORT).show()
        }

        targetCard.setOnClickListener {
            showEditTempDialog()
        }
    }

    private fun updateUI(topic: String, payload: String) {
        when (topic) {
            MqttTopics.TEMP -> tempText.text = getString(R.string.temperature_format, payload)
            MqttTopics.PRESENCE -> presenceText.text = payload
            MqttTopics.HUMIDITY -> humidityText.text = getString(R.string.humidity_format, payload)
            MqttTopics.TARGET -> targetText.text = getString(R.string.temperature_format, payload)
            MqttTopics.HABIT -> habitText.text = payload
            MqttTopics.HEATER_STATUS -> {
                heaterText.text = payload
                if (payload == "ON") {
                    heaterCard.setCardBackgroundColor(getColor(R.color.accent_hot))
                } else {
                    heaterCard.setCardBackgroundColor(getColor(R.color.surface))
                }
            }
        }
    }

    private fun updateSwitchColor(isChecked: Boolean) {
        if (isChecked) {
            val color = getColor(R.color.accent_hot)
            manualControlSwitch.thumbTintList = ColorStateList.valueOf(color)
            manualControlSwitch.trackTintList = ColorStateList.valueOf(color)
        } else {
            val gray = getColor(android.R.color.darker_gray)
            manualControlSwitch.thumbTintList = ColorStateList.valueOf(gray)
            manualControlSwitch.trackTintList = ColorStateList.valueOf(gray)
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

        val currentTarget = targetText.text.toString().replace(getString(R.string.temperature_format, ""), "").replace(getString(R.string.offline_placeholder),"")
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
                        preferencesManager.saveData(MqttTopics.TARGET, newTempStr)
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
        manualControlSwitch.isEnabled = isEnabled
        analyzeButton.isEnabled = isEnabled
        targetCard.isEnabled = isEnabled
        targetCard.alpha = if (isEnabled) 1.0f else 0.5f
    }

    private fun loadData() {
        val placeholder = getString(R.string.offline_placeholder)
        tempText.text = getString(R.string.temperature_format, preferencesManager.getLastTemp(placeholder))
        presenceText.text = preferencesManager.getLastPresence(placeholder)
        heaterText.text = preferencesManager.getLastHeaterStatus(placeholder)
        humidityText.text = getString(R.string.humidity_format, preferencesManager.getLastHumidity(placeholder))
        targetText.text = getString(R.string.temperature_format, preferencesManager.getLastTarget(placeholder))
        habitText.text = preferencesManager.getLastHabit(placeholder)
    }
}