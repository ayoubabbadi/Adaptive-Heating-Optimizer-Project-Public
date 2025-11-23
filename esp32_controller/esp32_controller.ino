#include <WiFi.h>
#include <PubSubClient.h>
#include "config.h"

#define LED_ON  HIGH
#define LED_OFF LOW
#define RELAY_ON  HIGH
#define RELAY_OFF LOW

#if defined(ESP32)
  const int LED_PIN = 2;
#else
  const int LED_PIN = LED_BUILTIN;
#endif
const int PIR_PIN = 27;
const int RELAY_PIN = 26;

const int MQTT_PORT = 1883;
const char* MQTT_CLIENT_ID = "esp32-chauffage-controller";
const int WIFI_TIMEOUT_SECONDS = 30;

const char* TOPIC_PRESENCE = "chauffage/etat/presence";
const char* TOPIC_HEATER_STATUS = "chauffage/etat/statutChauffage";
const char* TOPIC_COMMAND = "chauffage/commande/set";
const char* TOPIC_ESP32_STATUS = "chauffage/etat/esp32_status";

WiFiClient espClient;
PubSubClient mqttClient(espClient);

long lastHeartbeat = 0;
bool lastMotionState = false;

void setup_wifi();
void reconnect_mqtt();
void mqtt_callback(char* topic, byte* payload, unsigned int length);
void check_physical_sensors();
void blink_fast_feedback();

void setup() {
  Serial.begin(115200);

  pinMode(LED_PIN, OUTPUT);
  pinMode(PIR_PIN, INPUT_PULLUP);
  pinMode(RELAY_PIN, OUTPUT);

  digitalWrite(RELAY_PIN, RELAY_OFF);
  digitalWrite(LED_PIN, LED_OFF);

  setup_wifi();
  mqttClient.setServer(MQTT_BROKER_IP, MQTT_PORT);
  mqttClient.setCallback(mqtt_callback);
  
  Serial.println("Setup complete.");
}

void loop() {
  if (WiFi.status() != WL_CONNECTED) setup_wifi();
  if (!mqttClient.connected()) reconnect_mqtt();
  mqttClient.loop();

  check_physical_sensors();

  long now = millis();
  if (now - lastHeartbeat > 30000) {
    lastHeartbeat = now;
    mqttClient.publish(TOPIC_ESP32_STATUS, "ONLINE");
  }
}

void check_physical_sensors() {
  bool currentMotionState = (digitalRead(PIR_PIN) == HIGH);

  if (currentMotionState != lastMotionState) {
    if (currentMotionState == true) {
      Serial.println("Motion START!");
      mqttClient.publish(TOPIC_PRESENCE, "DETECTED");
      blink_fast_feedback();
    } else {
      Serial.println("Motion END.");
      mqttClient.publish(TOPIC_PRESENCE, "CLEAR"); 
    }
    lastMotionState = currentMotionState;
  }
}

void setup_wifi() {
  delay(10);
  WiFi.disconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int attempt = 0;
  bool ledState = false;
  while (WiFi.status() != WL_CONNECTED) {
    digitalWrite(LED_PIN, ledState ? LED_ON : LED_OFF);
    ledState = !ledState;
    delay(250);
    if (attempt++ > WIFI_TIMEOUT_SECONDS * 4) ESP.restart();
  }
  digitalWrite(LED_PIN, LED_OFF);
}

void reconnect_mqtt() {
  while (!mqttClient.connected()) {
    if (mqttClient.connect(MQTT_CLIENT_ID, NULL, NULL, TOPIC_ESP32_STATUS, 1, true, "OFFLINE")) {
      mqttClient.publish(TOPIC_ESP32_STATUS, "ONLINE", true);
      mqttClient.subscribe(TOPIC_COMMAND);
      mqttClient.publish(TOPIC_HEATER_STATUS, "OFF"); 
    } else {
      delay(5000);
    }
  }
}

void mqtt_callback(char* topic, byte* payload, unsigned int length) {
  char message[length + 1];
  for (int i = 0; i < length; i++) message[i] = (char)payload[i];
  message[length] = '\0';

  if (strcmp(topic, TOPIC_COMMAND) == 0) {
    if (strcmp(message, "ON") == 0) {
      digitalWrite(RELAY_PIN, RELAY_ON);
      digitalWrite(LED_PIN, LED_ON);
      mqttClient.publish(TOPIC_HEATER_STATUS, "ON");
    } else if (strcmp(message, "OFF") == 0) {
      digitalWrite(RELAY_PIN, RELAY_OFF);
      digitalWrite(LED_PIN, LED_OFF);
      mqttClient.publish(TOPIC_HEATER_STATUS, "OFF");
    }
  }
}

void blink_fast_feedback() {
  bool wasHeaterOn = (digitalRead(RELAY_PIN) == RELAY_ON);
  for (int i = 0; i < 2; i++) {
    digitalWrite(LED_PIN, !digitalRead(LED_PIN));
    delay(50);
  }
  digitalWrite(LED_PIN, wasHeaterOn ? LED_ON : LED_OFF);
}