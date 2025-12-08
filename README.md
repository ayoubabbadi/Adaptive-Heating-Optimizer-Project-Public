# EcoHeat Edge: Adaptive Heating Optimizer

**An intelligent, localized IoT heating control system prioritizing energy efficiency, comfort, and safety.**

---

##  Important Note: Implementation vs. Design
**Please note a distinction between the system design and the current codebase:**
* [cite_start]**Design (Schema):** The *Global Functional Diagram* (`Synoptique de Fonctionnement Global.pdf`) specifies the use of a physical **BMP280 sensor** connected to the ESP32 for local Temperature and Humidity readings[cite: 8, 9, 38, 45].
* **Current Implementation (Code):** The current software version utilizes an **Online API** to fetch Temperature and Humidity data. This allows the system to function and be tested even without the physical BMP280 sensor installed.

---

##  Project Overview

[cite_start]**EcoHeat Edge** creates an intelligent heating system based on a **Master-Slave Edge Computing Architecture**[cite: 37, 38]. The system is divided into two distinct physical zones:

1.  [cite_start]**The "Brain" (Master - Raspberry Pi 3):** Handles 100% of data processing, decision-making, predictive analysis (Smart Habit), and hosts the MQTT Broker[cite: 42, 43].
2.  [cite_start]**The "Hands" (Slave - ESP32):** A purely physical unit responsible for executing mechanical orders (Relay ON/OFF)[cite: 44, 45].

---

##  Core Features

* [cite_start]**Hybrid Occupation Algorithm:** Determines presence based on **Real Motion (PIR)** OR **Historical Probability (> 40%)**[cite: 23].
* [cite_start]**Thermostatic Regulation:** Maintains comfort using a precise hysteresis loop (Target Temp ±0.5°C)[cite: 24].
* [cite_start]**Safety & Anomaly Detection:** Continuously monitors for failures (e.g., "Open Window" or "Heater Failure") by analyzing temperature response times[cite: 33, 34].
* [cite_start]**Mobile Dashboard:** A custom Android app for live monitoring, target temperature setting, and manual override[cite: 12, 18, 20].
* [cite_start]**Resilient Connectivity:** Includes connection handshakes and "Wait Loops" to ensure synchronization between the App, Pi, and ESP32[cite: 14, 15, 17].

---

##  Hardware Components

* [cite_start]**Master Unit (Edge Brain):** Raspberry Pi 3 Model B (Python Logic + Mosquitto Broker)[cite: 42].
* [cite_start]**Slave Unit (Controller):** ESP32-DevKitC[cite: 44].
* **Sensors & Inputs:**
    * [cite_start]**BMP280:** (As defined in Schema) For acquiring environmental data (Temperature & Humidity)[cite: 9, 45].
    * [cite_start]**HC-SR-501 (PIR):** For detecting physical movement[cite: 10, 45].
* **Actuators:**
    * [cite_start]**Relay Module:** Controls the heating element[cite: 26, 45].
    * [cite_start]**LED Indicator:** Visual status for Heating ON/OFF[cite: 26, 27].

---


## Wiring Diagram

Here is the complete hardware wiring for the controller components.

![Project Wiring Diagram](Projet_Chauffage_Adaptatif_IoT_Edge_Schema.png)


---

##  System Logic & Data Flow

The system operates in a **5-Phase Loop** as defined in the *Global Functional Diagram*:

### Phase 1: Initialization & Connection
* [cite_start]System startup and script initialization (`raspberrypi_logic.py`)[cite: 2, 3, 4].
* [cite_start]**Handshake:** Establishes MQTT connection and verifies availability of the ESP32 and Android App[cite: 6, 17, 18].
* [cite_start]**Wait Loop:** If connection fails, the system enters a "Waiting..." status until synchronized[cite: 14, 15, 16].

### Phase 2: Data Acquisition
[cite_start]The system gathers three types of data[cite: 7]:
1.  **Environmental:** Temperature & Humidity.
    * [cite_start]*Schema Design:* Readings via **BMP280**[cite: 9].
    * *Current Code:* Readings via **Online API**.
2.  [cite_start]**Physical:** Motion status from the **PIR Sensor**[cite: 10].
3.  [cite_start]**Predictive:** Probability of presence calculated from `motion_history.json`[cite: 11].

### Phase 3: Decision Logic Core
[cite_start]The "Brain" processes data through a strict priority hierarchy[cite: 19]:
1.  [cite_start]**Priority 1 (Manual Mode):** Is the user forcing the system ON/OFF via the App?[cite: 20, 21].
2.  **Priority 2 (Hybrid Occupation):**
    * Is Motion Detected? **OR**
    * [cite_start]Is Habit Probability > **40%**?[cite: 23].
3.  [cite_start]**Priority 3 (Thermostat):** Is the Target Temperature within the range of Ambient Temp **±0.5°C**?[cite: 24].

### Phase 4: Execution
* [cite_start]**ON State:** Relay Closed (Heater ON) + LED ON[cite: 26].
* [cite_start]**OFF State:** Relay Open (Heater OFF) + LED OFF[cite: 27].

### Phase 5: Safety & Monitoring
[cite_start]The system verifies physical response to ensure safety[cite: 28]:
* [cite_start]*Check:* Is Heating active for **> 30 seconds**?[cite: 29].
* [cite_start]*Check:* Has temperature varied significantly ($Abs(\Delta T) \ge 0.5^{\circ}C$)?[cite: 30, 31].
* [cite_start]**Alert:** If heating is ON but temperature doesn't rise, trigger **"Security Alert"** (Possible Open Window or Heater Failure) and notify the user[cite: 33, 34].

---

##  Configuration & Setup

### 1. Raspberry Pi (The Brain)
* **File:** `raspberrypi_logic.py`
* [cite_start]**Role:** Runs the Python decision script, updates the history database, and hosts the MQTT Broker[cite: 42].
* **Config:**
    ```python
    # Logic Thresholds
    HABIT_THRESHOLD = 40  # % probability to trigger heating
    TEMP_HYSTERESIS = 0.5 # +/- degrees for stability
    # Note: Ensure API credentials are set if using the API method.
    ```

### 2. ESP32 (The Hands)
* [cite_start]**Role:** Reads sensors (PIR) and toggles pins (Relay/LED).[cite: 45].
* **Pins:**
    * PIR Sensor (Digital Input)
    * Relay & LED (Digital Output)
    * *(Note: BMP280 code is present for schema compliance but overridden by API logic in the main script).*

---

##  Documentation
* [cite_start]**Functional Diagram:** See `Synoptique de Fonctionnement Global.pdf` for the detailed algorithmic flow[cite: 5].