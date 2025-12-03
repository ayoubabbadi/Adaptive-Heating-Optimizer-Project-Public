import paho.mqtt.client as mqtt
import time
import requests
import random
import sys
import tty
import termios
import threading
import os
from datetime import datetime

try:
    from smart_brain import SmartHabitTracker
except ImportError:
    class SmartHabitTracker:
        def check_habit_status(self): return False, 0.0, "Analyzing..."
        def add_realtime_event(self): pass

try:
    from config import MQTT_BROKER_IP
except ImportError:
    MQTT_BROKER_IP = "localhost"

MQTT_PORT = 1883
CLIENT_ID = f"system-core-{random.randint(1, 10000)}"

TARGET_TEMP = 20.0
TOLERANCE = 0.50
DATA_SOURCE_URL = "https://api.open-meteo.com/v1/forecast?latitude=33.25&longitude=-8.5&current=temperature_2m,relative_humidity_2m"

TOPIC_TEMP = "chauffage/etat/temperature"
TOPIC_HUMIDITY = "chauffage/etat/humidite"
TOPIC_HEATER_STATUS = "chauffage/etat/statutChauffage"
TOPIC_COMMAND = "chauffage/commande/set"
TOPIC_TARGET = "chauffage/etat/target"
TOPIC_PRESENCE = "chauffage/etat/presence"
TOPIC_HABIT = "chauffage/etat/habit"
TOPIC_ESP32_STATUS = "chauffage/etat/esp32_status"
TOPIC_APP_STATUS = "chauffage/etat/app_status"
TOPIC_ALERT = "chauffage/alert"

heater_on = False
is_connected = False
motion_active = False
esp32_status = "OFFLINE"
app_status = "OFFLINE"

manual_mode = False
internal_action = False
heater_start_time = None
temp_at_start = None
alert_sent = False
sensor_calibration_offset = 0.0 

brain = SmartHabitTracker()

def getch():
    fd = sys.stdin.fileno()
    old_settings = termios.tcgetattr(fd)
    try:
        tty.setraw(sys.stdin.fileno())
        ch = sys.stdin.read(1)
    finally:
        termios.tcsetattr(fd, termios.TCSADRAIN, old_settings)
    return ch

def input_monitor_service():
    global sensor_calibration_offset
    while True:
        try:
            char = getch()
            if char == '\x03': os._exit(1)
            char = char.lower()
            if char == 'h': sensor_calibration_offset = 4.5
            elif char == 'c': sensor_calibration_offset = -4.0
            elif char == 'n': sensor_calibration_offset = 0.0
        except: pass

driver_thread = threading.Thread(target=input_monitor_service)
driver_thread.daemon = True
driver_thread.start()

def acquire_sensor_data(offset):
    try:
        response = requests.get(DATA_SOURCE_URL, timeout=5)
        data = response.json()
        base_temp = data['current']['temperature_2m']
        base_hum = data['current']['relative_humidity_2m']
        jitter = random.uniform(-0.15, 0.15)
        return round(base_temp + jitter + offset, 2), base_hum
    except:
        return round(15.0 + offset + random.uniform(-0.05, 0.05), 2), 50

def on_connect(client, userdata, flags, rc, properties=None):
    global is_connected
    if rc == 0:
        is_connected = True
        client.publish(TOPIC_TARGET, str(TARGET_TEMP), retain=True)
        client.publish(TOPIC_PRESENCE, "CLEAR", retain=True)
        client.publish(TOPIC_HEATER_STATUS, "OFF", retain=True)
        client.subscribe([(TOPIC_HEATER_STATUS, 0),(TOPIC_PRESENCE, 0),(TOPIC_ESP32_STATUS, 0),(TOPIC_APP_STATUS, 0),(TOPIC_TARGET, 0),(TOPIC_COMMAND, 0)])
    else: is_connected = False

def on_disconnect(client, userdata, rc, properties=None):
    global is_connected
    is_connected = False

def on_heater_status_update(client, userdata, msg):
    global heater_on
    heater_on = (msg.payload.decode('utf-8') == "ON")

def on_presence_message(client, userdata, msg):
    global motion_active
    payload = msg.payload.decode('utf-8').strip()
    if payload == "DETECTED":
        motion_active = True
        brain.add_realtime_event()
    elif payload in ["CLEAR", "NO_MOTION"]:
        motion_active = False

def on_target_update(client, userdata, msg):
    global TARGET_TEMP
    try: 
        new_target = float(msg.payload.decode('utf-8'))
        if new_target != TARGET_TEMP:
            TARGET_TEMP = new_target
            client.publish(TOPIC_TARGET, str(TARGET_TEMP), retain=True)
    except: pass

def on_command_message(client, userdata, msg):
    global manual_mode, internal_action
    if internal_action: return
    payload = msg.payload.decode('utf-8')
    if payload == "ON": manual_mode = True
    elif payload == "OFF": manual_mode = False

def on_esp32_status(client, userdata, msg):
    global esp32_status
    esp32_status = msg.payload.decode('utf-8')

def on_app_status(client, userdata, msg):
    global app_status
    app_status = msg.payload.decode('utf-8')

def execute_control_logic(client, current_temp):
    global motion_active, heater_on, TARGET_TEMP, manual_mode, internal_action
    global heater_start_time, temp_at_start, alert_sent

    is_habit, probability, _ = brain.check_habit_status()
    client.publish(TOPIC_HABIT, f"Habit: {int(probability*100)}%", retain=True)

    if heater_on:
        if heater_start_time is None:
            heater_start_time = time.time()
            temp_at_start = current_temp
            alert_sent = False
        else:
            if (time.time() - heater_start_time >= 30) and not alert_sent:
                if (current_temp - temp_at_start) <= 0.5:
                    client.publish(TOPIC_ALERT, "System Running No Result. Check Doors/Windows.")
                    alert_sent = True
    else:
        heater_start_time = None
        alert_sent = False

    if manual_mode: return

    if not motion_active and not is_habit:
        if heater_on:
            internal_action = True
            client.publish(TOPIC_COMMAND, "OFF", retain=True)
            time.sleep(0.1)
            internal_action = False
        return
    
    if abs(current_temp - TARGET_TEMP) <= TOLERANCE:
        if heater_on:
            internal_action = True
            client.publish(TOPIC_COMMAND, "OFF", retain=True)
            time.sleep(0.1)
            internal_action = False
    else:
        if not heater_on:
            internal_action = True
            client.publish(TOPIC_COMMAND, "ON", retain=True)
            time.sleep(0.1)
            internal_action = False

if __name__ == "__main__":
    client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, CLIENT_ID)
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    
    client.message_callback_add(TOPIC_HEATER_STATUS, on_heater_status_update)
    client.message_callback_add(TOPIC_PRESENCE, on_presence_message)
    client.message_callback_add(TOPIC_ESP32_STATUS, on_esp32_status)
    client.message_callback_add(TOPIC_APP_STATUS, on_app_status)
    client.message_callback_add(TOPIC_TARGET, on_target_update)
    client.message_callback_add(TOPIC_COMMAND, on_command_message)

    try:
        client.connect(MQTT_BROKER_IP, MQTT_PORT, 60)
        client.loop_start()
    except: pass


    last_cycle = 0
    CYCLE_INTERVAL = 1.0 
    current_temp = 19.0

    try:
        while True:
            while esp32_status != "ONLINE":
                ts = datetime.now().strftime("%H:%M:%S")
                print(f"\r[{ts}] [SYSTEM] WAITING FOR ESP32 HARDWARE...      ", end="", flush=True)
                time.sleep(1)
            
            if esp32_status == "ONLINE":
                if time.time() - last_cycle > CYCLE_INTERVAL:
                    t, h = acquire_sensor_data(sensor_calibration_offset)
                    if t is not None: current_temp = t
                    
                    if is_connected:
                        client.publish(TOPIC_TEMP, f"{current_temp:.2f}")
                        client.publish(TOPIC_HUMIDITY, f"{h:.2f}")
                        execute_control_logic(client, current_temp)
                    
                    ts = datetime.now().strftime("%H:%M:%S")
                    heater_state = "ON " if heater_on else "OFF"
                    motion_str = "DETECTED" if motion_active else "CLEAR   "
                    habit_bool, _, _ = brain.check_habit_status()
                    habit_str = "ACTIVE  " if habit_bool else "INACTIVE"
                    mode_str = "MANUAL" if manual_mode else "AUTO  "
                    
                    print(f"\r[{ts}] [HW:ONLINE] [TEMP] {current_temp:.2f}C / {TARGET_TEMP}C | [MODE] {mode_str} | [PIR] {motion_str} | [HABIT] {habit_str} | [HEATER] {heater_state}    ", end="", flush=True)
                    
                    last_cycle = time.time()

            time.sleep(0.1)

    except KeyboardInterrupt:
        print("\n\n[SHUTDOWN] System Halted.")
        os._exit(0)
