#!/usr/bin/env python3

import paho.mqtt.client as mqtt
import time
import requests
import random
from datetime import datetime

try:
    from smart_brain import SmartHabitTracker
except ImportError:
    print("SmartHabitTracker not found. Habits will be ignored.")
    # Dummy class to prevent crash if file missing
    class SmartHabitTracker:
        def check_habit_status(self): return False, 0.0, "No Brain"
        def add_realtime_event(self): pass

try:
    from config import MQTT_BROKER_IP
except ImportError:
    MQTT_BROKER_IP = "localhost"

MQTT_PORT = 1883
CLIENT_ID = f"raspberrypi-heater-logic-{random.randint(1, 10000)}"

TARGET_TEMP = 20.0
TOLERANCE = 0.50

API_URL = "https://api.open-meteo.com/v1/forecast?latitude=33.25&longitude=-8.5&current=temperature_2m,relative_humidity_2m"

# --- MQTT Topics ---
TOPIC_TEMP = "chauffage/etat/temperature"
TOPIC_HUMIDITY = "chauffage/etat/humidite"
TOPIC_HEATER_STATUS = "chauffage/etat/statutChauffage"
TOPIC_COMMAND = "chauffage/commande/set"
TOPIC_TARGET = "chauffage/etat/target"
TOPIC_PRESENCE = "chauffage/etat/presence"
TOPIC_HABIT = "chauffage/etat/habit"  # Added Habit Topic
TOPIC_ESP32_STATUS = "chauffage/etat/esp32_status"
TOPIC_APP_STATUS = "chauffage/etat/app_status"
TOPIC_ALERT = "chauffage/alert"

# --- Global State ---
heater_on = False
is_connected = False
motion_active = False
esp32_status = "OFFLINE"
app_status = "OFFLINE"
last_logic_print = 0 

# --- Manual Mode Flags ---
manual_mode = False
internal_action = False

# --- Safety Monitor Variables ---
heater_start_time = None
temp_at_start = None
alert_sent = False

# Initialize Brain
brain = SmartHabitTracker()

def fetch_weather_data():
    try:
        print("[API] Reading Weather Data...")
        response = requests.get(API_URL, timeout=10)
        response.raise_for_status()
        data = response.json()
        temp = data['current']['temperature_2m']
        humidity = data['current']['relative_humidity_2m']
        print(f"[API] Data Acquired: Temp={temp}C, Hum={humidity}%")
        return temp, humidity
    except Exception as e:
        print(f"Error fetching data from API: {e}")
        # Fallback for demo if internet fails
        return round(random.uniform(10.0, 15.0), 2), 50

def on_connect(client, userdata, flags, rc, properties=None):
    global is_connected
    if rc == 0:
        print("Connected to MQTT Broker successfully.")
        is_connected = True
        client.subscribe(TOPIC_HEATER_STATUS)
        client.subscribe(TOPIC_PRESENCE)
        client.subscribe(TOPIC_ESP32_STATUS)
        client.subscribe(TOPIC_APP_STATUS)
        client.subscribe(TOPIC_TARGET)
        client.subscribe(TOPIC_COMMAND)
    else:
        print(f"Failed to connect, return code {rc}.")
        is_connected = False

def on_disconnect(client, userdata, rc, properties=None):
    global is_connected
    is_connected = False
    print(f"Disconnected from MQTT. Result code {rc}.")

def on_heater_status_update(client, userdata, msg):
    global heater_on
    payload = msg.payload.decode('utf-8')
    heater_on = (payload == "ON")

def on_presence_message(client, userdata, msg):
    global motion_active
    payload = msg.payload.decode('utf-8').strip()
    
    if payload == "DETECTED":
        motion_active = True
        print(">>> [PIR] MOTION DETECTED - System Active <<<")
        # Teach the Brain
        brain.add_realtime_event()
    elif payload == "CLEAR" or payload == "NO_MOTION":
        motion_active = False
        print(">>> [PIR] Motion Clear <<<")

def on_target_update(client, userdata, msg):
    global TARGET_TEMP
    try:
        payload = msg.payload.decode('utf-8')
        TARGET_TEMP = float(payload)
        print(f">>> [USER] New Target Received: {TARGET_TEMP} C")
    except ValueError:
        print(f"Error: Invalid target received: {msg.payload}")

def on_command_message(client, userdata, msg):
    global manual_mode, internal_action
    
    if internal_action:
        return

    payload = msg.payload.decode('utf-8')
    
    if payload == "ON":
        manual_mode = True
        print(">>> [USER OVERRIDE] Manual Mode ACTIVATED. Ignoring Temp/Motion logic. <<<")
    elif payload == "OFF":
        manual_mode = False
        print(">>> [USER OVERRIDE] Manual Mode DEACTIVATED. Returning to Auto Logic. <<<")

def on_esp32_status(client, userdata, msg):
    global esp32_status
    esp32_status = msg.payload.decode('utf-8')
    if esp32_status == "OFFLINE":
        print(">>> [SYSTEM] HARDWARE LOST. PAUSING. <<<")

def on_app_status(client, userdata, msg):
    global app_status
    app_status = msg.payload.decode('utf-8')
    if app_status == "OFFLINE":
        print(">>> [SYSTEM] APP DISCONNECTED. PAUSING. <<<")

# --- Logic ---
def run_control_logic(client, temp):
    global motion_active, heater_on, last_logic_print, TARGET_TEMP, manual_mode, internal_action
    global heater_start_time, temp_at_start, alert_sent

    if temp is None:
        return

    current_time = time.time()
    should_print = (current_time - last_logic_print > 5)

    # --- 1. CHECK HABITS (The New Part) ---
    is_habit, probability, _ = brain.check_habit_status()
    
    # Publish Habit Status to App
    habit_text = f"Habit: {int(probability*100)}% | Motion: {'Yes' if motion_active else 'No'}"
    client.publish(TOPIC_HABIT, habit_text)

    # ==========================================
    #  SAFETY MONITOR: 30s General Check
    # ==========================================
    if heater_on:
        if heater_start_time is None:
            heater_start_time = current_time
            temp_at_start = temp
            alert_sent = False
        else:
            elapsed_time = current_time - heater_start_time
            
            # UPDATED LOGIC: Check "System Health" regardless of Target Temp
            # If the heater has been ON for 30s, the temp MUST rise.
            # If it stays the same or drops (cooling), we trigger the alert.
            if elapsed_time >= 30 and not alert_sent:
                temp_diff = temp - temp_at_start
                
                # If temp difference is minimal OR negative (cooling down while heater is on)
                if temp_diff <= 0.1:
                    print(f">>> [ALERT] Heater ON for 30s but Temp stagnated/dropped! (Diff: {temp_diff:.2f}C)")
                    msg = "ALERT: System running for 30s with NO temp rise. Check Windows/Doors and Heater!"
                    client.publish(TOPIC_ALERT, msg)
                    alert_sent = True
                else:
                    if should_print:
                         print(f"[Safety] System OK. Temp rose by {temp_diff:.2f}C")
    else:
        heater_start_time = None
        temp_at_start = None
        alert_sent = False

    # ==========================================
    #  CONTROL LOGIC
    # ==========================================

    if manual_mode:
        if should_print:
             print(f"[Logic] Status: MANUAL OVERRIDE (Heater forced ON by User)")
             last_logic_print = current_time
        return

    # LOGIC CHANGE: Only shut down if NO Motion AND NO Habit
    if not motion_active and not is_habit:
        if heater_on:
            print(f"Logic: Empty House (No Motion + Not Habit Time). Energy Saving OFF.")
            internal_action = True
            client.publish(TOPIC_COMMAND, "OFF")
            time.sleep(0.1)
            internal_action = False
        elif should_print:
            print(f"[Logic] Monitoring... Status: IDLE (Empty/Energy Save)")
            last_logic_print = current_time
        return
    
    # If we are here, either Motion detected OR it is a Habit Time.
    # Proceed to Temperature Check.

    deviation = abs(temp - TARGET_TEMP)

    # If within buffer zone (24.5 to 25.5 if target is 25)
    if deviation <= TOLERANCE:
        if heater_on:
            print(f"Logic: Temp ({temp}C) reached Target ({TARGET_TEMP}C). System OFF.")
            internal_action = True
            client.publish(TOPIC_COMMAND, "OFF")
            time.sleep(0.1)
            internal_action = False
        elif should_print:
            print(f"[Logic] Status: COMFORTABLE (Temp: {temp}C / Target: {TARGET_TEMP}C)")
            last_logic_print = current_time
    else:
        # Outside buffer zone
        if not heater_on:
            print(f"Logic: Temp ({temp}C) deviated > {TOLERANCE} from {TARGET_TEMP}. System ON.")
            internal_action = True
            client.publish(TOPIC_COMMAND, "ON")
            time.sleep(0.1)
            internal_action = False
        elif should_print:
             print(f"[Logic] Status: RUNNING (Temp: {temp}C / Target: {TARGET_TEMP}C)")
             last_logic_print = current_time

if __name__ == "__main__":
    # Paho v2.0 compatibility
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, CLIENT_ID)

    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    
    client.message_callback_add(TOPIC_HEATER_STATUS, on_heater_status_update)
    client.message_callback_add(TOPIC_PRESENCE, on_presence_message)
    client.message_callback_add(TOPIC_ESP32_STATUS, on_esp32_status)
    client.message_callback_add(TOPIC_APP_STATUS, on_app_status)
    client.message_callback_add(TOPIC_TARGET, on_target_update)
    client.message_callback_add(TOPIC_COMMAND, on_command_message)

    while not is_connected:
        try:
            print("Attempting to connect to MQTT broker...")
            client.connect(MQTT_BROKER_IP, MQTT_PORT, 60)
            client.loop_start()
            time.sleep(2)
        except (ConnectionRefusedError, OSError) as e:
            print(f"Connection failed: {e}. Retrying in 5 seconds...")
            time.sleep(5)

    last_weather_update = 0
    WEATHER_UPDATE_DELAY = 60
    CONTROL_LOOP_DELAY = 1
    
    latest_temp = None

    try:
        while True:
            # Safety check: Wait for hardware and app
            while esp32_status != "ONLINE" or app_status != "ONLINE":
                print(f"\r[System] Waiting... ESP32: [{esp32_status}] | App: [{app_status}]", end="")
                time.sleep(2)

            print("\n[System] Hardware and App Connected. Starting Main Control Loop.")
            
            while esp32_status == "ONLINE" and app_status == "ONLINE":
                current_time = time.time()

                if current_time - last_weather_update > WEATHER_UPDATE_DELAY:
                    t, h = fetch_weather_data()
                    if t is not None and h is not None:
                        latest_temp = t
                        if is_connected:
                            client.publish(TOPIC_TEMP, f"{t:.2f}")
                            client.publish(TOPIC_HUMIDITY, f"{h:.2f}")
                    last_weather_update = current_time

                if is_connected and latest_temp is not None:
                    run_control_logic(client, latest_temp)
                
                time.sleep(CONTROL_LOOP_DELAY)

    except KeyboardInterrupt:
        print("\nShutting down...")
        client.loop_stop()
        client.disconnect()
        print("Disconnected.")