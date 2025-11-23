#  Adaptive Heating Optimizer (IoT Edge Project)

**This project is part of a university course competition (ROMITEF 2026) and serves as the practical model for the "Architecture et Technologie des Ordinateurs" module.**

## Project Description

This project is an entry for the **ROMITEF 2026** competition, listed as **Project #3: "Optimiseur de Chauffage Adaptatif (IoT Edge)"**.

It creates an intelligent heating system that learns a room's occupation habits (using a **PIR sensor**) and current temperature (using a **BME280 sensor**). All decisions are made locally by a **Raspberry Pi 3** (the "Edge Brain"). The Pi sends commands to an **ESP32** (the "Controller"), which then activates a relay to control a fan. The entire system can be monitored and controlled in real-time via a **custom Android application**.

##  Core Features
* **Adaptive Logic:** The system only turns on the "heater" (fan) if the room is occupied AND the temperature is below a set target.
* **IoT Edge Processing:** All logic runs locally on the Raspberry Pi.
* **Mobile Dashboard:** A custom Android app connects to the system to display live data (temperature, presence) and send manual commands.
* **Wireless Communication:** The ESP32, Raspberry Pi, and Android App all communicate wirelessly over the local Wi-Fi network using the **MQTT** protocol.

## Hardware Components
* **Edge Brain:** Raspberry Pi 3 Model B
* **Controller:** ESP32-DevKitC
* **Temperature Sensor:** BME280
* **Presence Sensor:** HC-SR-501 PIR Sensor
* **The Switch:** 1-Channel 5V Relay Module
* **The "Heater" (Test):** 5V DC Fan
* **Accessories:**
    * MicroSD Card (for Raspberry Pi OS)
    * 1x Micro-USB Power Supply (for Pi 3, 5V 2.5A recommended)
    * 1x Micro-USB Cable (for ESP32)
    * Breadboard & Jumper Wires

##  Wiring Diagram (Schema)

Here is the complete hardware wiring for all components (except the Pi, which connects via Wi-Fi).

![Project Wiring Diagram](Projet_Chauffage_Adaptatif_IoT_Edge_Schema.png)


##  Software & Setup

**The full source code for all components will be published here soon.**

### Configuration

Before running the code, you need to provide your network credentials.

1.  **ESP32 Configuration:**
    *   Create a file named `config.h` inside the `esp32_controller/` directory.
    *   Add your Wi-Fi SSID/password and the IP address of your Raspberry Pi (MQTT Broker).
    *   **Example `esp32_controller/config.h`:**
        ```c
        #define WIFI_SSID "YOUR_WIFI_SSID"
        #define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
        #define MQTT_BROKER_IP "192.168.1.100" // Your Pi's IP
        ```

2.  **Raspberry Pi Configuration (Optional):**
    *   The Python script defaults to connecting to the MQTT broker on `localhost`.
    *   If your broker is on a different machine, create a file named `config.py` inside the `raspberrypi_logic/` directory.
    *   **Example `raspberrypi_logic/config.py`:**
        ```python
        MQTT_BROKER_IP = "192.168.1.101"
        ```

These configuration files are ignored by Git to keep your credentials secure.

### 1. ESP32 (Controller)
* **Code File:** `esp32_controller/esp32_controller.ino` (Coming Soon)
* **Environment:** Arduino IDE
* **Libraries:** `PubSubClient`, `Adafruit_BME280`, `WiFi.h`

### 2. Raspberry Pi (Edge Brain)
* **Code File:** `raspberrypi_logic/raspberrypi_logic.py` (Coming Soon)
* **Environment:** Raspberry Pi OS
* **Broker:** `Mosquitto` (MQTT Broker)
* **Libraries:** `paho-mqtt` (Python)

### 3. Android App (Dashboard)
* **Code File:** The source code is available in the `My-Application/` directory.
* **Environment:** Android Studio
* **Language:** Kotlin
* **Libraries:** `paho-mqtt` (Android), `core-splashscreen`

##  How It Works (Data Flow)

1.  **SENSE:** The **ESP32** reads the temperature and motion status.
2.  **PUBLISH:** The **ESP32** sends this data (e.g., `{"temp": 21.5, "presence": 1}`) to the MQTT broker on the Raspberry Pi.
3.  **MONITOR:** The **Android App** also listens to these topics and updates its UI in real-time.
4.  **DECIDE:** The **Python script** on the **Raspberry Pi** receives the data and runs the adaptive logic.
5.  **COMMAND:** The **Raspberry Pi** publishes an "ON" command. (The **Android App** can also publish "ON"/"OFF" commands for manual control).
6.  **ACT:** The **ESP32** receives the "ON" command and activates the **Relay**, turning on the **Fan**.
