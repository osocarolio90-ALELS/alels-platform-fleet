#include <Arduino.h>
#include <SD.h>
#include <SPI.h>
#include <WiFi.h>
#include <WiFiClient.h>
#include <Preferences.h>

// =====================================================
// ALELS Device Firmware - Stable Core Revision v2.9.2
// Focus perubahan:
// 1) TCP ID handshake wajib ACK 01 sebelum upload telemetry
// 2) Pending file hanya dikirim jika tcpIdentified=true
// 3) Setiap JSON dikirim line-based dengan println() dan menunggu ACK 01
// 4) Heartbeat ringan saat idle
// 5) Logic serial read, build JSON, SD save, pending queue tetap dipertahankan
// 6) Prevent FMC650 diagnostic text from contaminating IO JSON values
// 7) Repair known historical Record Size and IO[...] contamination before TCP retry
// 8) Runtime SD mount recovery: never treat an unavailable SD as 0% free
// 9) Q advances only after telemetry is durably saved to SD
// 10) Known deterministic IO contamination is repaired before send
// 11) Unrecoverable corrupt files are preserved as .bad so they cannot block the durable queue
// 12) IO parser accepts only strict numeric/hex values; malformed IO is skipped instead of corrupting JSON
// 13) SD scan is event-driven with a slow safety rescan, not a 1-second full-directory polling loop
// 14) Network/ACK failures retry forever; corrupt-data failures do not retry forever
// 15) Pending telemetry with empty/malformed A (IMEI) is repaired from remembered/current IMEI before send
// 16) TCP identification is blocked until a valid IMEI exists; last valid IMEI is persisted across restart
// 17) No change to serial commands/configuration, WiFi/TCP settings, SD queue window, or ALELS JSON schema
// 18) When the configured per-file record limit is reached, close the writer immediately so realtime telemetry can upload without the idle-close delay
// =====================================================

Preferences preferences;

// =============================
// === ALELS Packet Sequence Q ===
// =============================
uint64_t globalQ = 1;

// =============================
// === Pin Definitions =========
// =============================
const int SERIAL_RX_PIN = 16;
const int SERIAL_TX_PIN = 17;
const int SD_CS_PIN = 5;

// =============================
// === Serial GPS ==============
// =============================
HardwareSerial SerialGPS(2);
String buffer = "";
unsigned long lastGPSCharTime = 0;
bool isReceivingGPS = false;
const int GPS_BUFFER_TIMEOUT = 500;
const int MAX_BUFFER_SIZE = 8192;

// =============================
// === Configurable Parameters ==
// =============================
const int DEFAULT_MAX_RECORDS_PER_FILE = 1;
const int DEFAULT_MIN_FREE_SPACE_PERCENT = 10;
const int DEFAULT_MAX_RETRY_COUNT = 5;
const unsigned long DEFAULT_BASE_RETRY_DELAY = 5000;

const char* DEFAULT_WIFI_SSID = "ALELS";
const char* DEFAULT_WIFI_PASSWORD = "<YOUR_WIFI_PASSWORD>";
const char* DEFAULT_TCP_SERVER = "192.168.1.36";
const int DEFAULT_TCP_PORT = 5050;

int MAX_RECORDS_PER_FILE = DEFAULT_MAX_RECORDS_PER_FILE;
int MIN_FREE_SPACE_PERCENT = DEFAULT_MIN_FREE_SPACE_PERCENT;
int MAX_RETRY_COUNT = DEFAULT_MAX_RETRY_COUNT;
unsigned long BASE_RETRY_DELAY = DEFAULT_BASE_RETRY_DELAY;

String WIFI_SSID = DEFAULT_WIFI_SSID;
String WIFI_PASSWORD = DEFAULT_WIFI_PASSWORD;
String TCP_SERVER = DEFAULT_TCP_SERVER;
int TCP_PORT = DEFAULT_TCP_PORT;

unsigned long networkRestartTimer = 0;
bool pendingNetworkRestart = false;
const unsigned long NETWORK_RESTART_DELAY = 3000;

// =============================
// === RAW Data Display ========
// =============================
bool rawDataEnabled = false;

// =============================
// === IMEI Management =========
// =============================
String lastIMEI = "";
String currentIMEI = "";
unsigned long totalIMEIChanges = 0;
unsigned long lastIMEIRecordNumber = 0;
bool imeiLogToFile = true;
const bool SEPARATE_FILE_PER_IMEI = false;

// =============================
// === Dynamic File Config =====
// =============================
File writerFile;
String currentWriterName = "";
int currentWriterNumber = 1;
int recordsInWriter = 0;
unsigned long totalSavedRecords = 0;
unsigned long totalBytesWritten = 0;

struct FileInfo {
  String name;
  int number;
  int recordCount;
  int retryCount;
  unsigned long lastRetryTime;
  bool isSent;
  bool isFailed;
};

const int PENDING_WINDOW_SIZE = 100;
FileInfo pendingFiles[PENDING_WINDOW_SIZE];
int pendingCount = 0;
unsigned long lastPendingRescanAt = 0;
const unsigned long PENDING_SAFETY_RESCAN_INTERVAL = 30000;
bool pendingRescanNeeded = true;
bool sdHasMorePending = false;
const unsigned long MAX_PENDING_RETRY_DELAY = 60000;
unsigned long lastRecordSavedAt = 0;
const unsigned long WRITER_IDLE_CLOSE_MS = 5000;

// =============================
// === SD Runtime Health ========
// =============================
bool sdReady = false;
unsigned long lastSDRecoveryAttempt = 0;
const unsigned long SD_RECOVERY_INTERVAL_MS = 5000;

// =============================
// === WiFi / TCP ==============
// =============================
WiFiClient tcpClient;
bool wifiConnected = false;
bool tcpConnected = false;
bool tcpIdentified = false;   // true setelah server ACK 01 untuk paket ID
bool wifiConnecting = false;

unsigned long wifiConnectStartTime = 0;
unsigned long lastWiFiReconnectAttempt = 0;
unsigned long lastTCPReconnectAttempt = 0;
unsigned long lastTCPKeepAlive = 0;
unsigned long lastHeartbeatTime = 0;

const unsigned long WIFI_RECONNECT_INTERVAL = 3000;
const unsigned long WIFI_CONNECT_TIMEOUT = 10000;
const unsigned long TCP_RECONNECT_INTERVAL = 3000;
const unsigned long TCP_CONNECT_TIMEOUT = 300;
const unsigned long TCP_KEEPALIVE_INTERVAL = 60000;
const unsigned long HEARTBEAT_INTERVAL = 30000;

String tcpRxBuffer = "";
const int TCP_RX_BUFFER_MAX = 2048;

// =============================
// === Serial Console Security ==
// =============================
const char* DEFAULT_SERIAL_KEYWORD = "alels123456";
String SERIAL_KEYWORD = DEFAULT_SERIAL_KEYWORD;
bool serialAuthenticated = false;
bool serialPromptShown = false;
bool lastSerialHostConnected = false;

// =============================
// === Function Prototypes =====
// =============================
void loadConfig();
void saveConfig();
void showConfig();
void setMaxRecordsPerFile(int value);
void setMinFreeSpacePercent(int value);
void setMaxRetryCount(int value);
void setBaseRetryDelay(unsigned long value);
void loadSerialSecurityConfig();
void saveSerialSecurityConfig();
void setSerialKeyword(String keyword);
bool isSerialHostConnected();
void processSerialSecurityState();
void showSerialLockedPrompt();
void showSerialUnlockedInfo();
void processSerialCommand(String cmdRaw);
void restartDevice(String source = "serial");

void loadWiFiConfig();
void saveWiFiConfig();
void setWiFiSSID(String ssid);
void setWiFiPassword(String password);
void setTCPServer(String server);
void setTCPPort(int port);
void resetWiFiConfig();
void scheduleNetworkRestart();
void processNetworkRestart();
void parseAndSetConfig(String cmd);

void handleIMEIDetection(String recordIMEI, unsigned long recordNumber);
void loadRememberedIMEI();
void saveRememberedIMEI(const String &imei);
bool isValidIMEIValue(const String &imei);
void saveIMEILog(String oldIMEI, String newIMEI, unsigned long recordNum);

String buildJSON(String record);
String sanitizeIOValue(String value);
bool repairStoredTelemetryJson(String &jsonLine);
bool looksLikeTelemetryJson(const String &jsonLine);
bool refreshTelemetrySizeField(String &jsonLine);
bool ensureTelemetryIMEIForSend(String &jsonLine);
bool quarantineCorruptPendingFile(String fileName, const String &reason);
enum PendingSendResult { PENDING_SEND_ACKED, PENDING_SEND_RETRYABLE, PENDING_SEND_QUARANTINED, PENDING_SEND_MISSING };
void processRecord(String record);
void processGPSData();
String convertTimestampToDateTime(int64_t timestamp);
String uint64ToString(uint64_t value);
void loadGlobalQ();
void saveGlobalQ();
void incrementGlobalQ();
void resetGlobalQ(uint64_t value = 1);
bool waitServerAck01(unsigned long timeoutMs = 2000);

void initSDCard();
bool mountSDCard(bool verbose = true);
bool ensureSDReady(bool force = false);
void processSDRecovery();
bool checkFreeSpace();
void createNewWriterFile();
void closeWriterFile();
bool saveToDynamicFile(String jsonData);
void processWriterIdleClose();
void addToPendingList(String fileName, int recordCount);
void processPendingFiles();
PendingSendResult sendFileToServer(String fileName);
void markFileAsSent(String fileName);
void markFileAsFailed(String fileName);
void recoverFromCrash();
void scanAndAddPendingFiles();
String sdBaseName(const String &path);
int countClosedPendingFilesOnSD();
void showSDCardInfo();
String buildStorageStatusJson();
void cleanSDData();
void formatSDCard();
void listAllFiles();
void viewFile(String fileName);
void viewLastFile();
void viewAllJSON();

void initWiFi();
void connectToWiFi();
void connectToTCPServer();
bool sendToTCP(String jsonData);
void sendHeartbeat();
void processTCPConnection();
void showNetworkStatus();
void disconnectWiFi();
void restartNetwork();

String jsonEscape(String value);
String jsonGetString(String json, String key);
bool jsonGetInt(String json, String key, int &out);
bool jsonGetULong(String json, String key, unsigned long &out);
void sendTCPCommandResponse(String cmd, String status, String message, String extraJson = "");
void processTCPIncomingLine(String line);
void processTCPJsonCommand(String json);

void toggleRawData();
void showRawDataStatus();

String getBestIMEI() {
  if (currentIMEI.length() > 0) return currentIMEI;
  if (lastIMEI.length() > 0) return lastIMEI;
  return "";
}

// =============================
// === ALELS Q ==================
// =============================
String uint64ToString(uint64_t value) {
  char buf[32];
  snprintf(buf, sizeof(buf), "%llu", (unsigned long long)value);
  return String(buf);
}

void loadGlobalQ() {
  preferences.begin("alels_q", false);
  globalQ = preferences.getULong64("q", 1);
  preferences.end();
  Serial.print("📦 ALELS Global Q loaded: ");
  Serial.println(uint64ToString(globalQ));
}

void saveGlobalQ() {
  preferences.begin("alels_q", false);
  preferences.putULong64("q", globalQ);
  preferences.end();
}

void incrementGlobalQ() {
  globalQ++;
  saveGlobalQ();
  Serial.print("🔢 Next ALELS Q: ");
  Serial.println(uint64ToString(globalQ));
}

void resetGlobalQ(uint64_t value) {
  if (value < 1) value = 1;
  globalQ = value;
  saveGlobalQ();
  Serial.print("🔁 ALELS Global Q reset to: ");
  Serial.println(uint64ToString(globalQ));
}

// =============================
// === Device Restart ==========
// =============================
void restartDevice(String source) {
  Serial.println();
  Serial.println("🔄 ESP32 restart command accepted");
  Serial.print("   Source: ");
  Serial.println(source);
  Serial.println("   Restart process will begin...");

  for (int i = 1; i <= 5; i++) {
    Serial.print("   Restart countdown ");
    Serial.print(i);
    Serial.println("/5");
    delay(1000);
  }

  Serial.println("🚀 Restarting ESP32 now...");
  Serial.flush();
  delay(200);
  ESP.restart();
}

bool waitServerAck01(unsigned long timeoutMs) {
  unsigned long start = millis();
  String resp = "";

  while (millis() - start < timeoutMs) {
    while (tcpClient.available()) {
      char c = tcpClient.read();

      if (c == '\n' || c == '\r') {
        resp.trim();

        if (resp == "01") {
          Serial.println("✅ Server ACK: 01");
          return true;
        }

        if (resp.startsWith("{")) {
          processTCPIncomingLine(resp);
        } else if (resp.length() > 0) {
          Serial.print("📨 Server: ");
          Serial.println(resp);
        }

        resp = "";
      } else {
        resp += c;

        if (resp.length() > 512) {
          Serial.println("⚠️ ACK buffer too long, clearing");
          resp = "";
        }
      }
    }

    if (!tcpClient.connected()) {
      tcpConnected = false;
      tcpIdentified = false;
      return false;
    }

    // Keep draining FMC650 UART while a slow server persists this packet.
    // Otherwise several seconds of ACK wait can overflow the serial driver.
    if (SerialGPS.available()) processGPSData();
    delay(1);
  }

  resp.trim();
  if (resp == "01") {
    Serial.println("✅ Server ACK: 01");
    return true;
  }

  if (resp.length() > 0) {
    Serial.print("⚠️ Server ACK timeout, last buffer: ");
    Serial.println(resp);
  } else {
    Serial.println("⚠️ Server ACK timeout");
  }
  return false;
}

// =============================
// === RAW Data Functions ======
// =============================
void toggleRawData() {
  rawDataEnabled = !rawDataEnabled;
  Serial.println("\n┌─────────────────────────────────────────┐");
  if (rawDataEnabled) {
    Serial.println("│  📡 RAW DATA MODE: ENABLED ✓              │");
    Serial.println("│  Full serial input will be displayed     │");
  } else {
    Serial.println("│  📡 RAW DATA MODE: DISABLED ✗             │");
    Serial.println("│  Only JSON output will be displayed      │");
  }
  Serial.println("└─────────────────────────────────────────┘\n");
}

void showRawDataStatus() {
  Serial.println("\n┌─────────────────────────────────────────┐");
  Serial.print("│  RAW Data Mode: ");
  if (rawDataEnabled) {
    Serial.println("ENABLED ✓                  │");
  } else {
    Serial.println("DISABLED ✗                 │");
  }
  Serial.println("└─────────────────────────────────────────┘\n");
}

// =============================
// === IMEI Functions ==========
// =============================
bool isValidIMEIValue(const String &imei) {
  if (imei.length() < 10 || imei.length() > 20) return false;
  for (size_t i = 0; i < imei.length(); i++) if (!isDigit(imei.charAt(i))) return false;
  return true;
}

void saveRememberedIMEI(const String &imei) {
  if (!isValidIMEIValue(imei)) return;
  preferences.begin("device_id", false);
  preferences.putString("imei", imei);
  preferences.end();
}

void loadRememberedIMEI() {
  preferences.begin("device_id", true);
  String remembered = preferences.getString("imei", "");
  preferences.end();
  remembered.trim();
  if (isValidIMEIValue(remembered)) {
    lastIMEI = remembered;
    currentIMEI = remembered;
    Serial.print("📦 Remembered IMEI loaded: ");
    Serial.println(remembered);
  }
}

void handleIMEIDetection(String recordIMEI, unsigned long recordNumber) {
  if (lastIMEI.length() == 0 && recordIMEI.length() > 0) {
    lastIMEI = recordIMEI;
    currentIMEI = recordIMEI;
    saveRememberedIMEI(recordIMEI);
    Serial.print("\n📱 [IMEI] Initial IMEI detected: ");
    Serial.println(recordIMEI);
    return;
  }
  
  if (lastIMEI != recordIMEI && recordIMEI.length() > 0 && lastIMEI.length() > 0) {
    totalIMEIChanges++;
    lastIMEIRecordNumber = recordNumber;
    Serial.println("\n╔════════════════════════════════════════════╗");
    Serial.println("║   ⚠️ IMEI CHANGE DETECTED! ⚠️               ║");
    Serial.println("╠════════════════════════════════════════════╣");
    Serial.print("║ Old IMEI: "); Serial.print(lastIMEI); Serial.println(" ║");
    Serial.print("║ New IMEI: "); Serial.print(recordIMEI); Serial.println(" ║");
    Serial.print("║ Record #: "); Serial.print(recordNumber); Serial.println(" ║");
    Serial.print("║ Change #: "); Serial.print(totalIMEIChanges); Serial.println(" ║");
    Serial.println("╚════════════════════════════════════════════╝");
    
    if (imeiLogToFile) {
      saveIMEILog(lastIMEI, recordIMEI, recordNumber);
    }
    
    lastIMEI = recordIMEI;
    currentIMEI = recordIMEI;
    saveRememberedIMEI(recordIMEI);
    
    if (SEPARATE_FILE_PER_IMEI) {
      Serial.println("📁 Creating new file for new IMEI...");
      closeWriterFile();
      createNewWriterFile();
    }
  }
}

void saveIMEILog(String oldIMEI, String newIMEI, unsigned long recordNum) {
  File logFile = SD.open("/gps_data/imei_changes.csv", FILE_APPEND);
  if (logFile) {
    logFile.print(millis());
    logFile.print(",");
    logFile.print(recordNum);
    logFile.print(",");
    logFile.print(oldIMEI);
    logFile.print(",");
    logFile.println(newIMEI);
    logFile.close();
    Serial.println("📝 IMEI change logged to SD card");
  }
}

// =============================
// === Serial Security =========
// =============================
void loadSerialSecurityConfig() {
  preferences.begin("serial_sec", false);
  SERIAL_KEYWORD = preferences.getString("keyword", DEFAULT_SERIAL_KEYWORD);
  preferences.end();

  if (SERIAL_KEYWORD.length() < 4) {
    SERIAL_KEYWORD = DEFAULT_SERIAL_KEYWORD;
    saveSerialSecurityConfig();
  }

  Serial.println("🔐 Serial security loaded");
}

void saveSerialSecurityConfig() {
  preferences.begin("serial_sec", false);
  preferences.putString("keyword", SERIAL_KEYWORD);
  preferences.end();
  Serial.println("💾 Serial keyword saved to flash");
}

void setSerialKeyword(String keyword) {
  keyword.trim();
  if (keyword.length() < 6 || keyword.length() > 32) {
    Serial.println("❌ Invalid serial keyword! Length must be 6-32 characters");
    return;
  }
  if (keyword.indexOf(' ') >= 0) {
    Serial.println("❌ Invalid serial keyword! Space is not allowed");
    return;
  }
  SERIAL_KEYWORD = keyword;
  saveSerialSecurityConfig();
  Serial.println("✅ Serial keyword changed successfully");
}

bool isSerialHostConnected() {
#if defined(ARDUINO_USB_CDC_ON_BOOT) && ARDUINO_USB_CDC_ON_BOOT
  return (bool)Serial;
#else
  return true;
#endif
}

void showSerialLockedPrompt() {
  Serial.println();
  Serial.println("🔐 SERIAL CONSOLE LOCKED");
  Serial.println("   Enter serial keyword to unlock command console.");
  Serial.println("   Default keyword: alels123456");
  Serial.println("   GPS serial, JSON build, SD save, WiFi and TCP keep running.");
  Serial.println();
}

void showSerialUnlockedInfo() {
  Serial.println();
  Serial.println("✅ SERIAL CONSOLE UNLOCKED");
  Serial.println("   Commands are now enabled until USB serial disconnect or logout.");
  Serial.println("   Change keyword: serialkey <new_keyword>");
  Serial.println("   Alternative  : set serialkey=<new_keyword>");
  Serial.println("   Lock again   : logout");
  Serial.println();
}

void processSerialSecurityState() {
  bool hostConnected = isSerialHostConnected();
  if (!hostConnected && lastSerialHostConnected) {
    serialAuthenticated = false;
    serialPromptShown = false;
  }
  if (hostConnected && !lastSerialHostConnected) {
    serialAuthenticated = false;
    serialPromptShown = false;
  }
  lastSerialHostConnected = hostConnected;
  if (hostConnected && !serialAuthenticated && !serialPromptShown) {
    showSerialLockedPrompt();
    serialPromptShown = true;
  }
}

// =============================
// === Config Functions ========
// =============================
void loadConfig() {
  preferences.begin("gps_config", false);
  MAX_RECORDS_PER_FILE = preferences.getInt("max_records", DEFAULT_MAX_RECORDS_PER_FILE);
  MIN_FREE_SPACE_PERCENT = preferences.getInt("min_free", DEFAULT_MIN_FREE_SPACE_PERCENT);
  MAX_RETRY_COUNT = preferences.getInt("max_retry", DEFAULT_MAX_RETRY_COUNT);
  BASE_RETRY_DELAY = preferences.getULong("base_delay", DEFAULT_BASE_RETRY_DELAY);
  preferences.end();
  Serial.println("\n📦 Config loaded from flash");
}

void saveConfig() {
  preferences.begin("gps_config", false);
  preferences.putInt("max_records", MAX_RECORDS_PER_FILE);
  preferences.putInt("min_free", MIN_FREE_SPACE_PERCENT);
  preferences.putInt("max_retry", MAX_RETRY_COUNT);
  preferences.putULong("base_delay", BASE_RETRY_DELAY);
  preferences.end();
  Serial.println("💾 Config saved to flash");
}

void showConfig() {
  Serial.println("\n=== CURRENT CONFIGURATION ===");
  Serial.println("--- File System Config ---");
  Serial.print("MAX_RECORDS_PER_FILE: "); Serial.println(MAX_RECORDS_PER_FILE);
  Serial.print("MIN_FREE_SPACE_PERCENT: "); Serial.print(MIN_FREE_SPACE_PERCENT); Serial.println("%");
  Serial.print("MAX_RETRY_COUNT: "); Serial.println(MAX_RETRY_COUNT);
  Serial.print("BASE_RETRY_DELAY: "); Serial.print(BASE_RETRY_DELAY); Serial.println(" ms");
  Serial.println("\n--- WiFi & TCP Config ---");
  Serial.print("WiFi SSID: "); Serial.println(WIFI_SSID);
  Serial.print("WiFi Password: "); Serial.println(WIFI_PASSWORD);
  Serial.print("TCP Server: "); Serial.println(TCP_SERVER);
  Serial.print("TCP Port: "); Serial.println(TCP_PORT);
  Serial.print("Serial Keyword: "); Serial.println(SERIAL_KEYWORD);
  Serial.println("\n--- ALELS Protocol ---");
  Serial.print("Global Q: "); Serial.println(uint64ToString(globalQ));
  Serial.println("\n--- IMEI Status ---");
  if (currentIMEI.length() > 0) {
    Serial.print("Current IMEI: "); Serial.println(currentIMEI);
    Serial.print("Total IMEI changes: "); Serial.println(totalIMEIChanges);
  } else {
    Serial.println("IMEI: Not detected yet");
  }
  Serial.println("\n--- Display Settings ---");
  Serial.print("RAW Data Mode: "); Serial.println(rawDataEnabled ? "ENABLED" : "DISABLED");
  Serial.println("=============================\n");
}

void setMaxRecordsPerFile(int value) {
  if (value >= 1 && value <= 1000) {
    MAX_RECORDS_PER_FILE = value;
    saveConfig();
    Serial.printf("✅ MAX_RECORDS_PER_FILE set to: %d\n", value);
  } else Serial.println("❌ Invalid value! Range: 1-1000");
}

void setMinFreeSpacePercent(int value) {
  if (value >= 1 && value <= 90) {
    MIN_FREE_SPACE_PERCENT = value;
    saveConfig();
    Serial.printf("✅ MIN_FREE_SPACE_PERCENT set to: %d%%\n", value);
  } else Serial.println("❌ Invalid value! Range: 1-90");
}

void setMaxRetryCount(int value) {
  if (value >= 0 && value <= 20) {
    MAX_RETRY_COUNT = value;
    saveConfig();
    Serial.printf("✅ MAX_RETRY_COUNT set to: %d\n", value);
  } else Serial.println("❌ Invalid value! Range: 0-20");
}

void setBaseRetryDelay(unsigned long value) {
  if (value >= 1000 && value <= 300000) {
    BASE_RETRY_DELAY = value;
    saveConfig();
    Serial.printf("✅ BASE_RETRY_DELAY set to: %lu ms (%lu seconds)\n", value, value/1000);
  } else Serial.println("❌ Invalid value! Range: 1000-300000 ms (1-300 seconds)");
}

// =============================
// === WiFi & TCP Config =======
// =============================
void loadWiFiConfig() {
  preferences.begin("wifi_config", false);
  WIFI_SSID = preferences.getString("wifi_ssid", DEFAULT_WIFI_SSID);
  WIFI_PASSWORD = preferences.getString("wifi_pass", DEFAULT_WIFI_PASSWORD);
  TCP_SERVER = preferences.getString("tcp_server", DEFAULT_TCP_SERVER);
  TCP_PORT = preferences.getInt("tcp_port", DEFAULT_TCP_PORT);
  preferences.end();
  Serial.println("📦 WiFi/TCP Config loaded from flash");
}

void saveWiFiConfig() {
  preferences.begin("wifi_config", false);
  preferences.putString("wifi_ssid", WIFI_SSID);
  preferences.putString("wifi_pass", WIFI_PASSWORD);
  preferences.putString("tcp_server", TCP_SERVER);
  preferences.putInt("tcp_port", TCP_PORT);
  preferences.end();
  Serial.println("💾 WiFi/TCP Config saved to flash");
}

void setWiFiSSID(String ssid) {
  if (ssid.length() > 0 && ssid.length() <= 32) {
    WIFI_SSID = ssid;
    saveWiFiConfig();
    Serial.printf("✅ WiFi SSID set to: %s\n", ssid.c_str());
    scheduleNetworkRestart();
  } else Serial.println("❌ Invalid SSID! Length: 1-32 characters");
}

void setWiFiPassword(String password) {
  if (password.length() >= 0 && password.length() <= 64) {
    WIFI_PASSWORD = password;
    saveWiFiConfig();
    Serial.printf("✅ WiFi Password set\n");
    scheduleNetworkRestart();
  } else Serial.println("❌ Invalid Password! Length: 0-64 characters");
}

void setTCPServer(String server) {
  if (server.length() > 0 && server.length() <= 64) {
    TCP_SERVER = server;
    saveWiFiConfig();
    Serial.printf("✅ TCP Server set to: %s\n", server.c_str());
    scheduleNetworkRestart();
  } else Serial.println("❌ Invalid Server address!");
}

void setTCPPort(int port) {
  if (port >= 1 && port <= 65535) {
    TCP_PORT = port;
    saveWiFiConfig();
    Serial.printf("✅ TCP Port set to: %d\n", port);
    scheduleNetworkRestart();
  } else Serial.println("❌ Invalid Port! Range: 1-65535");
}

void resetWiFiConfig() {
  WIFI_SSID = DEFAULT_WIFI_SSID;
  WIFI_PASSWORD = DEFAULT_WIFI_PASSWORD;
  TCP_SERVER = DEFAULT_TCP_SERVER;
  TCP_PORT = DEFAULT_TCP_PORT;
  saveWiFiConfig();
  Serial.println("✅ WiFi/TCP Config reset to default!");
  scheduleNetworkRestart();
}

void scheduleNetworkRestart() {
  if (!pendingNetworkRestart) {
    pendingNetworkRestart = true;
    networkRestartTimer = millis();
    Serial.println("\n⚠️ Network configuration changed!");
    Serial.printf("   Network will restart in %d seconds...\n", NETWORK_RESTART_DELAY / 1000);
    Serial.println("   (GPS recording continues normally during this time)");
  } else {
    networkRestartTimer = millis();
    Serial.println("⚠️ Network configuration changed again, resetting restart timer...");
  }
}

void processNetworkRestart() {
  if (pendingNetworkRestart && (millis() - networkRestartTimer >= NETWORK_RESTART_DELAY)) {
    pendingNetworkRestart = false;
    Serial.println("\n🔄 Executing network restart...");
    restartNetwork();
  }
}

void restartNetwork() {
  Serial.println("Disconnecting current network...");
  disconnectWiFi();
  Serial.println("Reconnecting with new configuration...");
  wifiConnecting = false;
  lastWiFiReconnectAttempt = 0;
  lastTCPReconnectAttempt = 0;
  connectToWiFi();
}

void disconnectWiFi() {
  if (tcpClient.connected()) tcpClient.stop();
  WiFi.disconnect(true);
  wifiConnected = false;
  wifiConnecting = false;
  tcpConnected = false;
  tcpIdentified = false;
  tcpRxBuffer = "";
}

// =============================
// === Multi-Command Parser ====
// =============================
void parseAndSetConfig(String cmd) {
  if (cmd.startsWith("set ")) cmd = cmd.substring(4);
  int startPos = 0;
  int spacePos;
  bool hasChanges = false;
  Serial.println("\n📝 Parsing configuration commands...");
  while (startPos < cmd.length()) {
    while (startPos < cmd.length() && cmd.charAt(startPos) == ' ') startPos++;
    if (startPos >= cmd.length()) break;
    // Only SSID consumes spaces; other configuration tokens keep their parser.
    String ssidPrefix = cmd.substring(startPos, startPos + 5);
    ssidPrefix.toLowerCase();
    if (ssidPrefix == "ssid=") {
      int valueStart = startPos + 5;
      int valueEnd = valueStart;
      String ssid;
      if (cmd.charAt(valueStart) == '"') {
        bool closed = false;
        valueEnd++;
        while (valueEnd < cmd.length()) {
          char c = cmd.charAt(valueEnd++);
          if (c == '"') { closed = true; break; }
          if (c == '\\' && valueEnd < cmd.length() &&
              (cmd.charAt(valueEnd) == '"' || cmd.charAt(valueEnd) == '\\')) {
            c = cmd.charAt(valueEnd++);
          }
          ssid += c;
        }
        if (!closed || (valueEnd < cmd.length() && cmd.charAt(valueEnd) != ' ')) {
          Serial.println("Invalid SSID: close quotes and separate parameters with a space");
          return;
        }
      } else {
        // An unquoted SSID ends at the next key=value parameter, not each space.
        valueEnd = cmd.length();
        for (int pos = valueStart; pos < cmd.length(); pos++) {
          if (cmd.charAt(pos) != ' ') continue;
          int next = pos + 1;
          while (next < cmd.length() && cmd.charAt(next) == ' ') next++;
          int tokenEnd = cmd.indexOf(' ', next);
          if (tokenEnd == -1) tokenEnd = cmd.length();
          int equals = cmd.indexOf('=', next);
          if (equals > next && equals < tokenEnd) { valueEnd = pos; break; }
        }
        ssid = cmd.substring(valueStart, valueEnd);
      }
      Serial.printf("  - ssid = %s\n", ssid.c_str());
      setWiFiSSID(ssid);
      if (ssid.length() > 0 && ssid.length() <= 32) hasChanges = true;
      startPos = valueEnd;
      while (startPos < cmd.length() && cmd.charAt(startPos) == ' ') startPos++;
      continue;
    }
    spacePos = cmd.indexOf(' ', startPos);
    String token;
    if (spacePos == -1) {
      token = cmd.substring(startPos);
      startPos = cmd.length();
    } else {
      token = cmd.substring(startPos, spacePos);
      startPos = spacePos + 1;
    }
    int eqPos = token.indexOf('=');
    if (eqPos != -1) {
      String key = token.substring(0, eqPos);
      String value = token.substring(eqPos + 1);
      key.toLowerCase();
      Serial.printf("  - %s = %s\n", key.c_str(), value.c_str());
      if (key == "records") { setMaxRecordsPerFile(value.toInt()); hasChanges = true; }
      else if (key == "freespace") { setMinFreeSpacePercent(value.toInt()); hasChanges = true; }
      else if (key == "retry") { setMaxRetryCount(value.toInt()); hasChanges = true; }
      else if (key == "delay") { setBaseRetryDelay(value.toInt()); hasChanges = true; }
      else if (key == "ssid") { setWiFiSSID(value); hasChanges = true; }
      else if (key == "pass" || key == "password") { setWiFiPassword(value); hasChanges = true; }
      else if (key == "server") { setTCPServer(value); hasChanges = true; }
      else if (key == "port") { setTCPPort(value.toInt()); hasChanges = true; }
      else if (key == "serialkey" || key == "keyword") { setSerialKeyword(value); hasChanges = true; }
      else Serial.printf("  ❌ Unknown key: %s\n", key.c_str());
    } else {
      Serial.printf("  ⚠️ Skipping invalid token: %s (missing '=')\n", token.c_str());
    }
  }
  if (hasChanges) Serial.println("\n✅ Configuration changes applied!");
  else {
    Serial.println("\n❌ No valid configuration changes detected!");
    Serial.println("   Format: set records=50 ssid=MyWiFi pass=1234 server=192.168.1.1 port=5000 serialkey=newkey");
  }
}

// =============================
// === Timestamp ===============
// =============================
String convertTimestampToDateTime(int64_t timestamp) {
  time_t rawTime = timestamp / 1000;
  struct tm* timeInfo = gmtime(&rawTime);
  char dtBuffer[20];
  strftime(dtBuffer, sizeof(dtBuffer), "%Y-%m-%d %H:%M:%S", timeInfo);
  return String(dtBuffer);
}

// =============================
// === Build JSON ==============
// =============================
String sanitizeIOValue(String value) {
  // Diagnostic text from FMC650 must never become part of an ALELS IO value.
  // Field-proven forms include:
  //   1Record Size: 273 Bytes (...)
  //   34IO[ 88]: 0
  //   1O[ 88]: 0       (damaged/missing leading I in diagnostic marker)
  // Keep the actual value before the diagnostic marker.
  int cutPos = -1;
  const char* markers[] = {"Record Size:", "IO[", "O["};
  for (size_t i = 0; i < sizeof(markers) / sizeof(markers[0]); i++) {
    int p = value.indexOf(markers[i]);
    if (p >= 0 && (cutPos < 0 || p < cutPos)) cutPos = p;
  }
  if (cutPos >= 0) value = value.substring(0, cutPos);
  value.trim();
  return value;
}

bool isDigitsOnly(const String &value) {
  if (value.length() == 0) return false;
  for (size_t i = 0; i < value.length(); i++) {
    if (!isDigit(value.charAt(i))) return false;
  }
  return true;
}

bool isSafeJsonNumber(const String &value) {
  // JSON number grammar; never emit damaged scalars (e.g. h), +1, 01 or 1.
  size_t i = 0, n = value.length();
  if (i < n && value.charAt(i) == '-') i++;
  if (i >= n) return false;
  if (value.charAt(i) == '0') i++;
  else {
    if (value.charAt(i) < '1' || value.charAt(i) > '9') return false;
    while (i < n && isDigit(value.charAt(i))) i++;
  }
  if (i < n && value.charAt(i) == '.') {
    size_t begin = ++i;
    while (i < n && isDigit(value.charAt(i))) i++;
    if (i == begin) return false;
  }
  if (i < n && (value.charAt(i) == 'e' || value.charAt(i) == 'E')) {
    i++;
    if (i < n && (value.charAt(i) == '+' || value.charAt(i) == '-')) i++;
    size_t begin = i;
    while (i < n && isDigit(value.charAt(i))) i++;
    if (i == begin) return false;
  }
  return i == n;
}

bool hasSafeFlatJsonLexicalForm(const String &jsonLine) {
  // Strict validator for the ALELS flat JSON schema. This validates object
  // grammar as well as characters, so corruption such as `"51":0: 0` is
  // rejected even though each individual character is JSON-like.
  String v = jsonLine;
  v.trim();
  int n = v.length();
  if (n < 2 || v.charAt(0) != '{' || v.charAt(n - 1) != '}') return false;

  int i = 1;
  auto skipWs = [&]() {
    while (i < n && isspace((unsigned char)v.charAt(i))) i++;
  };
  auto parseQuoted = [&]() -> bool {
    if (i >= n || v.charAt(i) != '"') return false;
    i++;
    bool escaped = false;
    while (i < n) {
      char c = v.charAt(i++);
      if (escaped) { escaped = false; continue; }
      if (c == '\\') { escaped = true; continue; }
      if (c == '"') return true;
      if ((unsigned char)c < 0x20) return false;
    }
    return false;
  };
  auto parseNumber = [&]() -> bool {
    int begin = i;
    while (i < n && v.charAt(i) != ',' && v.charAt(i) != '}' &&
           !isspace((unsigned char)v.charAt(i))) i++;
    return isSafeJsonNumber(v.substring(begin, i));
  };

  skipWs();
  if (i < n && v.charAt(i) == '}') return i == n - 1;

  while (i < n - 1) {
    skipWs();
    if (!parseQuoted()) return false;          // key
    skipWs();
    if (i >= n || v.charAt(i) != ':') return false;
    i++;
    skipWs();
    if (i >= n) return false;
    if (v.charAt(i) == '"') {
      if (!parseQuoted()) return false;        // string value
    } else {
      if (!parseNumber()) return false;        // numeric value
    }
    skipWs();
    if (i >= n) return false;
    if (v.charAt(i) == ',') { i++; continue; }
    if (v.charAt(i) == '}') return i == n - 1;
    return false;
  }
  return false;
}

int findIoDiagnosticMarker(const String &jsonLine, int from, int &markerLen) {
  int exact = jsonLine.indexOf("IO[", from);
  int damaged = jsonLine.indexOf("O[", from);
  if (exact >= 0 && (damaged < 0 || exact <= damaged - 1)) {
    markerLen = 3;
    return exact;
  }
  if (damaged >= 0) {
    // O[ is accepted only outside a quoted string and when immediately after
    // a numeric scalar. Example: "11":1O[ 88]: 0
    bool inString = false;
    bool escaped = false;
    for (int i = 0; i < damaged; i++) {
      char c = jsonLine.charAt(i);
      if (inString) {
        if (escaped) escaped = false;
        else if (c == '\\') escaped = true;
        else if (c == '"') inString = false;
      } else if (c == '"') inString = true;
    }
    if (!inString && damaged > 0) {
      char prev = jsonLine.charAt(damaged - 1);
      if (isDigit(prev) || prev == '.') { markerLen = 2; return damaged; }
    }
  }
  markerLen = 0;
  return -1;
}

bool repairIoDiagnosticContamination(String &jsonLine) {
  bool repairedAny = false;
  int searchFrom = 0;
  while (true) {
    int markerLen = 0;
    int marker = findIoDiagnosticMarker(jsonLine, searchFrom, markerLen);
    if (marker < 0) break;

    int closeBracket = jsonLine.indexOf(']', marker + markerLen);
    if (closeBracket < 0) return false;
    int colon = jsonLine.indexOf(':', closeBracket + 1);
    if (colon < 0) return false;

    String ioId = jsonLine.substring(marker + markerLen, closeBracket);
    ioId.trim();
    if (!isDigitsOnly(ioId)) return false;

    int valueEnd = jsonLine.indexOf(',', colon + 1);
    if (valueEnd < 0) valueEnd = jsonLine.indexOf('}', colon + 1);
    if (valueEnd < 0) return false;

    String ioValue = jsonLine.substring(colon + 1, valueEnd);
    ioValue.trim();
    if (!isSafeJsonNumber(ioValue)) return false;

    String replacement = ",\"" + ioId + "\":" + ioValue;
    jsonLine = jsonLine.substring(0, marker) + replacement + jsonLine.substring(valueEnd);
    repairedAny = true;
    searchFrom = marker + replacement.length();
  }
  return repairedAny;
}

bool looksLikeTelemetryJson(const String &jsonLine) {
  String value = jsonLine;
  value.trim();
  if (!value.startsWith("{") || !value.endsWith("}")) return false;
  if (value.indexOf("\"T\":\"data\"") < 0) return false;
  if (value.indexOf("\"Q\":") < 0) return false;
  if (value.indexOf("\"S\":") < 0) return false;
  if (value.indexOf("Record Size:") >= 0) return false;
  if (value.indexOf("Record Saved:") >= 0) return false;
  if (value.indexOf("AVL.WRITE") >= 0) return false;
  if (value.indexOf("IO[") >= 0 || value.indexOf("O[") >= 0) return false;
  return hasSafeFlatJsonLexicalForm(value);
}

bool refreshTelemetrySizeField(String &jsonLine) {
  jsonLine.trim();
  int sPos = jsonLine.lastIndexOf(",\"S\":");
  int endBrace = jsonLine.lastIndexOf('}');
  if (sPos < 0 || endBrace < sPos) return false;
  String base = jsonLine.substring(0, sPos);
  int finalSize = jsonLine.length();
  String rebuilt;
  for (int i = 0; i < 6; i++) {
    rebuilt = base + ",\"S\":" + String(finalSize) + "}";
    int calculated = rebuilt.length();
    if (calculated == finalSize) break;
    finalSize = calculated;
  }
  jsonLine = rebuilt;
  return true;
}

bool ensureTelemetryIMEIForSend(String &jsonLine) {
  int marker = jsonLine.indexOf("\"A\":\"");
  if (marker < 0) return false;
  int valueStart = marker + 5;
  int valueEnd = jsonLine.indexOf('"', valueStart);
  if (valueEnd < 0) return false;

  String payloadIMEI = jsonLine.substring(valueStart, valueEnd);
  payloadIMEI.trim();
  if (isValidIMEIValue(payloadIMEI)) return true;

  String replacementIMEI = getBestIMEI();
  replacementIMEI.trim();
  if (!isValidIMEIValue(replacementIMEI)) return false;

  jsonLine = jsonLine.substring(0, valueStart) + jsonEscape(replacementIMEI) + jsonLine.substring(valueEnd);
  if (!refreshTelemetrySizeField(jsonLine)) return false;
  Serial.print("  🪪 Repaired telemetry IMEI before TCP send: ");
  Serial.println(replacementIMEI);
  return true;
}

bool repairStoredTelemetryJson(String &jsonLine) {
  // Backward repair for already-stored field data. Known diagnostic suffixes
  // are repaired conservatively. If the result still fails lexical/schema
  // validation, the caller quarantines the original file rather than blocking
  // the entire durable FIFO forever.
  bool repaired = false;

  int marker = jsonLine.indexOf("Record Size:");
  if (marker >= 0) {
    int sizeField = jsonLine.indexOf(",\"S\":", marker);
    if (sizeField < 0) return false;
    jsonLine = jsonLine.substring(0, marker) + jsonLine.substring(sizeField);
    repaired = true;
  }

  int markerLen = 0;
  if (findIoDiagnosticMarker(jsonLine, 0, markerLen) >= 0) {
    if (!repairIoDiagnosticContamination(jsonLine)) return false;
    repaired = true;
  }

  jsonLine.trim();
  if (!looksLikeTelemetryJson(jsonLine)) return false;
  if (repaired) Serial.println("  🛠️ Repaired stored telemetry JSON contamination before TCP send");
  return true;
}

String buildJSON(String record) {
  String recordIMEI = "";
  float hdop = 0;
  String datetime = "";
  int priority = 0;
  int longitude = 0;
  int latitude = 0;
  int altitude = 0;
  int angle = 0;
  int sat = 0;
  int speed = 0;
  int eventIO = 0;
  struct IOField { String key; String value; bool isHex; };
  IOField ioFields[100];
  int ioCount = 0;
  record.replace("\r\t", "\n");
  // Parse the last IO line even when the diagnostic stream has no final newline.
  if (!record.endsWith("\n")) record += "\n";
  record.replace("\r", "\n");
  int from = 0;
  while (true) {
    int idx = record.indexOf('\n', from);
    if (idx == -1) break;
    String line = record.substring(from, idx);
    from = idx + 1;
    line.trim();
    if (line.length() == 0) continue;
    if (line.startsWith("HDOP Actual:")) hdop = line.substring(line.indexOf(":") + 1).toFloat();
    else if (line.startsWith("Timestamp:")) {
      String val = line.substring(line.indexOf(":") + 1);
      val.trim();
      int64_t timestamp = (int64_t)atoll(val.c_str());
      datetime = convertTimestampToDateTime(timestamp);
    }
    else if (line.startsWith("Priority:")) priority = line.substring(line.indexOf(":") + 1).toInt();
    else if (line.startsWith("Longitude:")) longitude = line.substring(line.indexOf(":") + 1).toInt();
    else if (line.startsWith("Latitude:")) latitude = line.substring(line.indexOf(":") + 1).toInt();
    else if (line.startsWith("Altitude:")) altitude = line.substring(line.indexOf(":") + 1).toInt();
    else if (line.startsWith("Angle:")) angle = line.substring(line.indexOf(":") + 1).toInt();
    else if (line.startsWith("Sat:")) sat = line.substring(line.indexOf(":") + 1).toInt();
    else if (line.startsWith("Speed:")) speed = line.substring(line.indexOf(":") + 1).toInt();
    else if (line.startsWith("Event IO ID:")) eventIO = line.substring(line.indexOf(":") + 1).toInt();
    else if (line.startsWith("IO[") || (line.startsWith("[") && line.indexOf("]:") > 1)) {
      // Some FMC650 diagnostic outputs contain [10500]: instead of IO[10500]:.
      int p1 = line.indexOf("[") + 1;
      int p2 = line.indexOf("]");
      if (p1 > 0 && p2 > p1) {
        String key = line.substring(p1, p2);
        key.trim();
        int colon = line.indexOf(":");
        if (colon > 0) {
          String val = line.substring(colon + 1);
          val = sanitizeIOValue(val);
          key.trim();
          val.trim();
          if (!isDigitsOnly(key) || val.length() == 0) continue;

          bool eyeBeaconHex = (key == "10500" || key == "548" || key == "11317");
          bool hasHexPrefix = val.startsWith("0x") || val.startsWith("0X");
          bool isHex = eyeBeaconHex || hasHexPrefix;
          bool validValue = false;
          if (isHex) {
            int hexStart = hasHexPrefix ? 2 : 0;
            validValue = val.length() > hexStart;
            if (eyeBeaconHex && ((val.length() - hexStart) % 2 != 0)) validValue = false;
            for (int vi = hexStart; validValue && vi < val.length(); vi++) {
              char hc = val.charAt(vi);
              if (!isDigit(hc) && !(hc >= 'a' && hc <= 'f') && !(hc >= 'A' && hc <= 'F')) validValue = false;
            }
          } else {
            validValue = isSafeJsonNumber(val);
          }
          if (!validValue) {
            Serial.print("⚠️ Ignoring malformed IO field before JSON build: IO[");
            Serial.print(key); Serial.print("] value='"); Serial.print(val); Serial.println("'");
            continue;
          }

          if (key == "1161") {
            if (!isDigitsOnly(val) || val.length() < 10 || val.length() > 20) {
              Serial.println("⚠️ Ignoring invalid IMEI IO[1161] value");
              continue;
            }
            recordIMEI = val;
            handleIMEIDetection(recordIMEI, totalSavedRecords + 1);
          } else {
            // A duplicated IO ID in one FMC650 diagnostic record is replaced
            // by the latest valid occurrence instead of generating duplicate keys.
            int existing = -1;
            for (int ii = 0; ii < ioCount; ii++) if (ioFields[ii].key == key) { existing = ii; break; }
            if (existing >= 0) {
              ioFields[existing].value = val;
              ioFields[existing].isHex = isHex;
            } else if (ioCount < 100) {
              ioFields[ioCount].key = key;
              ioFields[ioCount].value = val;
              ioFields[ioCount].isHex = isHex;
              ioCount++;
            }
          }
        }
      }
    }
  }
  String payload;
  payload.reserve(4096);
  payload = "{";
  payload += "\"T\":\"data\"";
  payload += ",\"Q\":" + uint64ToString(globalQ);
  if (recordIMEI.length() > 0) payload += ",\"A\":\"" + recordIMEI + "\"";
  else if (currentIMEI.length() > 0) payload += ",\"A\":\"" + currentIMEI + "\"";
  else payload += ",\"A\":\"\"";
  if (datetime.length() > 0) payload += ",\"B\":\"" + datetime + "\"";
  else payload += ",\"B\":\"\"";
  payload += ",\"C\":" + String(hdop, 1);
  payload += ",\"D\":" + String(longitude);
  payload += ",\"E\":" + String(latitude);
  payload += ",\"F\":" + String(altitude);
  payload += ",\"G\":" + String(angle);
  payload += ",\"H\":" + String(sat);
  payload += ",\"I\":" + String(speed);
  payload += ",\"J\":" + String(priority);
  payload += ",\"K\":" + String(eventIO);
  for (int i = 0; i < ioCount; i++) {
    if (ioFields[i].isHex) payload += ",\"" + ioFields[i].key + "\":\"" + ioFields[i].value + "\"";
    else payload += ",\"" + ioFields[i].key + "\":" + ioFields[i].value;
  }
  payload += "}";
  int finalSize = payload.length();
  String finalPayload;
  for (int i = 0; i < 5; i++) {
    finalPayload = payload.substring(0, payload.length() - 1);
    finalPayload += ",\"S\":" + String(finalSize);
    finalPayload += "}";
    int calculatedSize = finalPayload.length();
    if (calculatedSize == finalSize) break;
    finalSize = calculatedSize;
  }
  return finalPayload;
}

void processRecord(String record) {
  Serial.println("\n═══════════════════════════════════════");
  if (rawDataEnabled) {
    Serial.println("📡 RAW SERIAL INPUT:");
    Serial.println("─────────────────────────────────");
    Serial.println(record);
    Serial.println("─────────────────────────────────");
  }
  Serial.print("Serial input (truncated): ");
  Serial.println(record.substring(0, 200));
  if (record.indexOf("Timestamp:") < 0) {
    Serial.println("WARNING: incomplete FMC650 record; not stored as empty telemetry");
    return;
  }
  String json = buildJSON(record);
  if (json.indexOf("\"K\":10500") >= 0 && json.indexOf("\"10500\":\"") < 0) {
    Serial.println("WARNING: EYE event lacks valid IO[10500] HEX; refusing empty beacon upload");
    return;
  }
  if (json.length() > 0 && !looksLikeTelemetryJson(json)) {
    Serial.println("❌ Parsed telemetry failed strict JSON validation. Record discarded; Q retained.");
    return;
  }
  if (json.length() > 0) {
    Serial.println("\n📊 JSON OUTPUT");
    Serial.println("─────────────────────────────────");
    Serial.print("JSON size: "); Serial.print(json.length()); Serial.println(" bytes");
    Serial.println("JSON content:"); Serial.println(json);
    Serial.println("─────────────────────────────────");
    if (saveToDynamicFile(json)) {
      incrementGlobalQ();
    } else {
      Serial.println("⚠️ Telemetry NOT committed to SD; ALELS Q is retained for the next successfully saved record.");
    }
  } else Serial.println("❌ Failed to generate JSON");
  Serial.println("═══════════════════════════════════════\n");
}

void processGPSData() {
  // Capture complete FMC650 diagnostics before parsing. In particular, the
  // EYE payload IO[10500] arrives AFTER "Record Size:"; '[' is not a boundary.
  while (SerialGPS.available()) {
    char c = (char)SerialGPS.read();
    if (buffer.length() >= MAX_BUFFER_SIZE) {
      // Never transmit a silently truncated AVL record as successful telemetry.
      Serial.println("ERROR: FMC650 record exceeded buffer; discarded (not ACKed/saved)");
      buffer = "";
      isReceivingGPS = false;
    }
    buffer += c;
    lastGPSCharTime = millis();
    isReceivingGPS = true;

    // The next AVL.WRITE date header marks the end of the previous record.
    // Do NOT split on IO[10500], [10500], Record Size, or a separator line.
    int nextHeader = buffer.lastIndexOf("\n[20");
    if (nextHeader > 0 && buffer.indexOf("[AVL.WRITE]") >= 0) {
      String complete = buffer.substring(0, nextHeader);
      buffer = buffer.substring(nextHeader + 1);
      if (complete.indexOf("Timestamp:") >= 0) processRecord(complete);
    }
  }
  // FMC650 records usually have a gap. Wait for the trailing IO payload
  // (including the final line) rather than flushing at "Record Size:".
  if (isReceivingGPS && millis() - lastGPSCharTime > (unsigned long)GPS_BUFFER_TIMEOUT + 500UL) {
    if (buffer.indexOf("Timestamp:") >= 0) processRecord(buffer);
    else if (buffer.length() > 0) Serial.println("WARNING: partial FMC650 record without Timestamp discarded");
    buffer = "";
    isReceivingGPS = false;
  }
}

// =============================
// === Dynamic File System =====
// =============================
bool checkFreeSpace() {
  if (!ensureSDReady()) {
    Serial.println("❌ SD Card unavailable/unmounted. Telemetry is NOT saved and Q will NOT advance.");
    return false;
  }

  uint64_t totalBytes = SD.totalBytes();
  uint64_t usedBytes = SD.usedBytes();
  if (totalBytes == 0 || usedBytes > totalBytes) {
    sdReady = false;
    Serial.println("❌ SD filesystem returned invalid capacity. Marking SD unavailable for automatic remount.");
    return false;
  }

  uint64_t freeBytes = totalBytes - usedBytes;
  int freePercent = (freeBytes * 100) / totalBytes;
  if (freePercent < MIN_FREE_SPACE_PERCENT) {
    Serial.printf("⚠️ WARNING: SD Card free space only %d%% (below %d%% threshold)!\n", freePercent, MIN_FREE_SPACE_PERCENT);
    return false;
  }
  return true;
}

String sdBaseName(const String &path) {
  int slash = path.lastIndexOf('/');
  return slash < 0 ? path : path.substring(slash + 1);
}

void scanAndAddPendingFiles() {
  if (pendingCount > 0) return;
  if (!ensureSDReady()) return;

  File root = SD.open("/gps_data");
  if (!root) {
    sdReady = false;
    pendingRescanNeeded = true;
    Serial.println("❌ Failed to open /gps_data; SD marked unavailable and will be remounted automatically.");
    return;
  }

  int foundFiles = 0;
  File file = root.openNextFile();
  while (file) {
    String fileName = sdBaseName(file.name());
    unsigned long fileSize = file.size();
    file.close();

    if (fileName.startsWith("data_") && fileName.endsWith(".json") && fileSize > 50) {
      String fullPath = "/gps_data/" + fileName;
      if (fullPath != currentWriterName) {
        foundFiles++;
        int startIdx = fileName.lastIndexOf("_") + 1;
        int endIdx = fileName.lastIndexOf(".json");
        int fileNumber = (startIdx > 0 && endIdx > startIdx)
                ? fileName.substring(startIdx, endIdx).toInt() : 0;

        File jsonFile = SD.open(fullPath, FILE_READ);
        int recordCount = 0;
        if (jsonFile) {
          while (jsonFile.available()) {
            String line = jsonFile.readStringUntil('\n');
            line.trim();
            if (line.startsWith("{")) recordCount++;
          }
          jsonFile.close();
        }

        if (recordCount > 0) {
          FileInfo candidate = {fullPath, fileNumber, recordCount, 0, 0, false, false};
          if (pendingCount < PENDING_WINDOW_SIZE) {
            pendingFiles[pendingCount++] = candidate;
          } else {
            int newestIndex = 0;
            for (int i = 1; i < pendingCount; i++) {
              if (pendingFiles[i].number > pendingFiles[newestIndex].number) newestIndex = i;
            }
            if (candidate.number < pendingFiles[newestIndex].number) pendingFiles[newestIndex] = candidate;
          }
        }
      }
    }
    file = root.openNextFile();
  }
  root.close();

  for (int i = 0; i < pendingCount - 1; i++) {
    for (int j = i + 1; j < pendingCount; j++) {
      if (pendingFiles[i].number > pendingFiles[j].number) {
        FileInfo temp = pendingFiles[i]; pendingFiles[i] = pendingFiles[j]; pendingFiles[j] = temp;
      }
    }
  }

  sdHasMorePending = foundFiles > pendingCount;
  pendingRescanNeeded = false;
  lastPendingRescanAt = millis();

  // Avoid hammering the SD card and serial console when the queue is empty.
  // Log only when an actual backlog is discovered.
  if (foundFiles > 0) {
    Serial.println("\n=== SD PENDING WINDOW REFILL ===");
    Serial.printf("SD backlog: %d closed files; loaded oldest %d into RAM window\n", foundFiles, pendingCount);
    if (sdHasMorePending) Serial.println("More files remain on SD and will be loaded after this RAM window drains.");
    Serial.println("================================\n");
  }
}

void recoverFromCrash() {
  Serial.println("\n=== CRASH RECOVERY ===");
  File root = SD.open("/gps_data");
  if (!root) { Serial.println("Failed to open directory"); return; }
  int lastFileNumber = 0;
  String lastFileName = "";
  File file = root.openNextFile();
  while (file) {
    String fileName = sdBaseName(file.name());
    file.close();
    if (fileName.startsWith("data_") && fileName.endsWith(".json")) {
      String numStr = fileName.substring(5, fileName.length() - 5);
      int fileNum = numStr.toInt();
      if (fileNum > lastFileNumber) { lastFileNumber = fileNum; lastFileName = fileName; }
    }
    file = root.openNextFile();
  }
  root.close();
  if (lastFileNumber > 0) currentWriterNumber = lastFileNumber + 1;
  else currentWriterNumber = 1;
  Serial.print("Next writer number: "); Serial.println(currentWriterNumber);
  Serial.println("====================\n");
  scanAndAddPendingFiles();
}

void createNewWriterFile() {
  if (!ensureSDReady()) return;
  if (writerFile) closeWriterFile();
  currentWriterName = "/gps_data/data_" + String(currentWriterNumber) + ".json";
  writerFile = SD.open(currentWriterName, FILE_WRITE);
  if (!writerFile) { Serial.println("❌ Failed to create file: " + currentWriterName); return; }
  recordsInWriter = 0;
  writerFile.println("[");
  writerFile.flush();
  Serial.println("\n📁 Created NEW file: " + currentWriterName);
  Serial.print("   File #"); Serial.print(currentWriterNumber); Serial.print(" | Max records: "); Serial.println(MAX_RECORDS_PER_FILE);
  currentWriterNumber++;
}

void closeWriterFile() {
  if (!writerFile) return;

  String closingName = currentWriterName;
  int closingRecords = recordsInWriter;

  if (closingRecords > 0) {
    writerFile.println("\n]");
    writerFile.flush();
  }
  writerFile.close();

  if (closingRecords > 0) {
    Serial.println("📁 Closed file: " + closingName);
    Serial.print("   Records: "); Serial.println(closingRecords);
    addToPendingList(closingName, closingRecords);
  } else if (closingName.length() > 0 && SD.exists(closingName)) {
    SD.remove(closingName);
  }

  currentWriterName = "";
  recordsInWriter = 0;
}

void processWriterIdleClose() {
  if (!writerFile || recordsInWriter <= 0 || lastRecordSavedAt == 0) return;
  if (millis() - lastRecordSavedAt < WRITER_IDLE_CLOSE_MS) return;

  Serial.println("📦 Writer idle timeout reached; finalizing last telemetry file for upload.");
  closeWriterFile();
}

bool saveToDynamicFile(String jsonData) {
  if (!checkFreeSpace()) return false;

  if (!writerFile || recordsInWriter >= MAX_RECORDS_PER_FILE) createNewWriterFile();
  if (!writerFile) {
    sdReady = false;
    Serial.println("❌ SD writer file is unavailable. Save aborted; automatic SD recovery scheduled.");
    return false;
  }

  String recordLine = (recordsInWriter > 0 ? String(",\r\n  ") : String("  ")) + jsonData;
  writerFile.clearWriteError();
  size_t written = writerFile.print(recordLine);
  writerFile.flush();

  if (written != recordLine.length() || writerFile.getWriteError()) {
    Serial.printf("❌ Incomplete SD write: %u/%u bytes. Q will NOT advance.\n", (unsigned)written, (unsigned)recordLine.length());
    writerFile.close();
    currentWriterName = "";
    recordsInWriter = 0;
    sdReady = false;
    return false;
  }

  recordsInWriter++;
  lastRecordSavedAt = millis();
  totalSavedRecords++;
  totalBytesWritten += jsonData.length();
  Serial.println("\n💾 SAVED TO SD CARD");
  Serial.println("─────────────────────────────────");
  Serial.print("File: "); Serial.println(currentWriterName);
  Serial.print("Record #"); Serial.print(recordsInWriter); Serial.print("/"); Serial.print(MAX_RECORDS_PER_FILE);
  Serial.print(" | Total records: "); Serial.println(totalSavedRecords);
  Serial.print("JSON size: "); Serial.print(jsonData.length()); Serial.println(" bytes");
  Serial.println("─────────────────────────────────");

  // If the configured file capacity has been reached, the file is already
  // complete and durable. Close it immediately so it becomes eligible for
  // upload without waiting for WRITER_IDLE_CLOSE_MS. This is especially
  // important for the default/test configuration MAX_RECORDS_PER_FILE=1.
  // The existing addToPendingList() global-FIFO guard still prevents a new
  // file from jumping ahead of an older SD backlog.
  if (recordsInWriter >= MAX_RECORDS_PER_FILE) {
    Serial.println("📦 File record limit reached; finalizing immediately for upload.");
    closeWriterFile();
  }

  return true;
}

void addToPendingList(String fileName, int recordCount) {
  // If a scan already proved there are older files outside the RAM window, do
  // not inject newly-created files into that window. They stay durable on SD
  // until all older windows have drained, preserving strict global FIFO.
  if (sdHasMorePending || pendingCount >= PENDING_WINDOW_SIZE) {
    sdHasMorePending = true;
    pendingRescanNeeded = true;
    Serial.println("ℹ️ Older SD backlog exists; new file stays durable on SD until the current oldest window drains.");
    return;
  }
  int fileNumber = 0;
  int startIdx = fileName.lastIndexOf("_") + 1;
  int endIdx = fileName.lastIndexOf(".json");
  if (startIdx > 0 && endIdx > startIdx) fileNumber = fileName.substring(startIdx, endIdx).toInt();
  pendingFiles[pendingCount] = {fileName, fileNumber, recordCount, 0, 0, false, false};
  pendingCount++;
  for (int i = pendingCount - 1; i > 0; i--) {
    if (pendingFiles[i].number < pendingFiles[i-1].number) {
      FileInfo temp = pendingFiles[i]; pendingFiles[i] = pendingFiles[i-1]; pendingFiles[i-1] = temp;
    } else break;
  }
  Serial.print("📋 Pending files: "); Serial.println(pendingCount);
}

void processPendingFiles() {
  if (!wifiConnected || !tcpConnected || !tcpIdentified || !tcpClient.connected()) return;

  // RAM is only a bounded window. SD card is the durable source of truth.
  // Refill automatically whenever the current window has been drained.
  if (pendingCount == 0) {
    unsigned long now = millis();
    bool safetyScanDue = (now - lastPendingRescanAt >= PENDING_SAFETY_RESCAN_INTERVAL);
    if (pendingRescanNeeded || sdHasMorePending || safetyScanDue) scanAndAddPendingFiles();
    if (pendingCount == 0) return;
  }

  // Strict FIFO: process only the oldest file. This prevents a broken connection
  // from increasing retry counters for every file in the queue and preserves trip order.
  FileInfo &item = pendingFiles[0];
  unsigned long now = millis();
  // MAX_RETRY_COUNT no longer means "drop after N retries". It only caps
  // exponential backoff. Delivery itself is unlimited until ACK succeeds.
  int configuredExponentCap = constrain(MAX_RETRY_COUNT, 0, 6);
  int retryExponent = min(item.retryCount, configuredExponentCap);
  uint64_t retryDelay64 = (uint64_t)BASE_RETRY_DELAY * (1ULL << retryExponent);
  unsigned long retryDelay = retryDelay64 > (uint64_t)MAX_PENDING_RETRY_DELAY
          ? MAX_PENDING_RETRY_DELAY
          : (unsigned long)retryDelay64;
  if (item.lastRetryTime != 0 && now - item.lastRetryTime < retryDelay) return;

  Serial.print("📤 Sending oldest pending: "); Serial.print(item.name);
  Serial.print(" (retry "); Serial.print(item.retryCount); Serial.println(")");

  PendingSendResult sendResult = sendFileToServer(item.name);
  if (sendResult == PENDING_SEND_ACKED || sendResult == PENDING_SEND_QUARANTINED || sendResult == PENDING_SEND_MISSING) {
    if (sendResult == PENDING_SEND_ACKED) Serial.println("  ✅ File fully ACKed and deleted from SD");
    else if (sendResult == PENDING_SEND_QUARANTINED) Serial.println("  ⚠️ Invalid file preserved as .bad; durable FIFO continues with the next file");
    else Serial.println("  ⚠️ SD mounted but pending path no longer exists; stale queue entry removed, not marked sent");
    for (int i = 1; i < pendingCount; i++) pendingFiles[i - 1] = pendingFiles[i];
    pendingCount--;
    if (pendingCount == 0 && sdHasMorePending) pendingRescanNeeded = true;
    return;
  }

  // Only transport/ACK failures retry forever. A deterministically corrupt file
  // is quarantined by sendFileToServer so it cannot block valid records.
  if (item.retryCount < 1000000) item.retryCount++;
  item.lastRetryTime = now;
  Serial.print("  ⚠️ Transport/ACK incomplete; retained on SD. Retry in up to ");
  Serial.print(retryDelay / 1000); Serial.println("s");
}

PendingSendResult sendFileToServer(String fileName) {
  String fullPath = fileName;
  if (!fileName.startsWith("/gps_data/")) fullPath = "/gps_data/" + fileName;
  if (!ensureSDReady()) return PENDING_SEND_RETRYABLE;
  if (!SD.exists(fullPath)) {
    // A missing queue path is not an ACK. Check the mounted directory first;
    // unreadable SD must retry rather than drain the in-memory queue.
    File directory = SD.open("/gps_data");
    if (!directory || !directory.isDirectory()) return PENDING_SEND_RETRYABLE;
    directory.close();
    return PENDING_SEND_MISSING;
  }

  File file = SD.open(fullPath, FILE_READ);
  if (!file) return PENDING_SEND_RETRYABLE;

  String line;
  int sentCount = 0;
  while (file.available()) {
    line = file.readStringUntil('\n');
    line.trim();
    if (line == "[" || line == "]" || line.length() == 0) continue;
    if (line.endsWith(",")) line = line.substring(0, line.length() - 1);
    line.trim();
    if (line.length() == 0) continue; // Standalone array comma was stripped above.
    if (!line.startsWith("{")) {
      file.close();
      if (quarantineCorruptPendingFile(fileName, "unexpected_record_content"))
        return PENDING_SEND_QUARANTINED;
      return PENDING_SEND_RETRYABLE;
    }

    if (!repairStoredTelemetryJson(line)) {
      file.close();
      Serial.print("  ❌ Unrecoverable telemetry JSON detected: ");
      Serial.println(fileName);
      if (quarantineCorruptPendingFile(fileName, "json_validation_failed")) {
        return PENDING_SEND_QUARANTINED;
      }
      Serial.println("  ⚠️ Invalid-file quarantine failed; original retained for retry.");
      return PENDING_SEND_RETRYABLE;
    }

    // Missing/invalid IMEI is an identity dependency, not corrupt telemetry.
    // Repair it from the current or remembered IMEI. If identity is not yet
    // known, retain the file and retry after IMEI detection; never delete it.
    if (!ensureTelemetryIMEIForSend(line)) {
      file.close();
      Serial.print("  ⏳ Telemetry identity not ready; retained on SD: ");
      Serial.println(fileName);
      return PENDING_SEND_RETRYABLE;
    }

    if (!sendToTCP(line)) {
      file.close();
      return PENDING_SEND_RETRYABLE;
    }
    sentCount++;
    delay(10);
  }
  file.close();

  if (sentCount <= 0) {
    if (quarantineCorruptPendingFile(fileName, "no_valid_telemetry_record")) {
      return PENDING_SEND_QUARANTINED;
    }
    return PENDING_SEND_RETRYABLE;
  }

  if (SD.remove(fullPath)) {
    Serial.print("  🗑️ Deleted: "); Serial.println(fileName);
    return PENDING_SEND_ACKED;
  }

  // ACK already succeeded but local delete failed. Retry is safe because ALELS
  // platform/gateway dedupe must tolerate at-least-once delivery.
  Serial.println("  ⚠️ ACK succeeded but SD delete failed; file retained for retry.");
  return PENDING_SEND_RETRYABLE;
}

bool quarantineCorruptPendingFile(String fileName, const String &reason) {
  String fullPath = fileName;
  if (!fileName.startsWith("/gps_data/")) fullPath = "/gps_data/" + fileName;
  if (!ensureSDReady()) return false;
  if (!SD.exists(fullPath)) return true;

  // Preserve the original bytes for diagnosis after interrupted SD writes.
  // A .bad suffix removes it from the pending JSON scan without data loss.
  String quarantinePath = fullPath + ".bad";
  if (SD.exists(quarantinePath)) return false;
  if (!SD.rename(fullPath, quarantinePath)) return false;

  Serial.print("  ⚠️ Quarantined invalid pending file: ");
  Serial.print(quarantinePath);
  Serial.print(" | reason=");
  Serial.println(reason);
  return true;
}

void markFileAsSent(String fileName) {
  for (int i = 0; i < pendingCount; i++) if (pendingFiles[i].name == fileName) { pendingFiles[i].isSent = true; break; }
}

void markFileAsFailed(String fileName) {
  for (int i = 0; i < pendingCount; i++) if (pendingFiles[i].name == fileName) { pendingFiles[i].isFailed = true; break; }
}

// =============================
// === SD Card Functions =======
// =============================
bool mountSDCard(bool verbose) {
  lastSDRecoveryAttempt = millis();
  if (verbose) Serial.print("Initializing SD card...");

  if (writerFile) writerFile.close();
  currentWriterName = "";
  recordsInWriter = 0;

  SD.end();
  SPI.end();
  delay(25);
  SPI.begin(18, 19, 23, SD_CS_PIN);
  delay(25);

  if (!SD.begin(SD_CS_PIN)) {
    sdReady = false;
    if (verbose) Serial.println(" FAILED!");
    else Serial.println("❌ SD remount failed; will retry automatically.");
    return false;
  }

  if (SD.cardType() == CARD_NONE || SD.totalBytes() == 0) {
    sdReady = false;
    SD.end();
    if (verbose) Serial.println(" FAILED (card/filesystem unavailable)!");
    else Serial.println("❌ SD remount returned no usable filesystem; will retry automatically.");
    return false;
  }

  if (!SD.exists("/gps_data") && !SD.mkdir("/gps_data")) {
    sdReady = false;
    if (verbose) Serial.println(" FAILED (cannot create /gps_data)!");
    return false;
  }

  File root = SD.open("/gps_data");
  if (!root) {
    sdReady = false;
    if (verbose) Serial.println(" FAILED (cannot open /gps_data)!");
    return false;
  }
  root.close();

  sdReady = true;
  if (verbose) Serial.println(" SUCCESS!");
  else Serial.println("✅ SD filesystem recovered and /gps_data is accessible.");
  return true;
}

bool ensureSDReady(bool force) {
  if (sdReady && SD.cardType() != CARD_NONE && SD.totalBytes() > 0) return true;

  sdReady = false;
  if (!force && millis() - lastSDRecoveryAttempt < SD_RECOVERY_INTERVAL_MS) return false;

  Serial.println("🩺 SD filesystem unavailable; attempting automatic remount...");
  if (!mountSDCard(false)) return false;

  pendingCount = 0;
  pendingRescanNeeded = true;
  sdHasMorePending = false;
  recoverFromCrash();
  if (!writerFile) createNewWriterFile();
  return true;
}

void processSDRecovery() {
  if (!sdReady) ensureSDReady(false);
}

void initSDCard() {
  if (!mountSDCard(true)) {
    Serial.println("⚠️ Firmware will continue running and automatically retry SD mount every 5 seconds.");
    return;
  }
  recoverFromCrash();
  showSDCardInfo();
  createNewWriterFile();
}

int countClosedPendingFilesOnSD() {
  if (!ensureSDReady()) return 0;
  File root = SD.open("/gps_data");
  if (!root) return 0;

  int count = 0;
  File file = root.openNextFile();
  while (file) {
    String fileName = sdBaseName(file.name());
    unsigned long fileSize = file.size();
    file.close();

    if (fileName.startsWith("data_") && fileName.endsWith(".json") && fileSize > 50) {
      String fullPath = "/gps_data/" + fileName;
      if (fullPath != currentWriterName) count++;
    }
    file = root.openNextFile();
  }
  root.close();
  return count;
}

void showSDCardInfo() {
  if (!ensureSDReady()) {
    Serial.println("\n=== SD CARD INFO ===");
    Serial.println("Status: UNAVAILABLE / NOT MOUNTED");
    Serial.println("Automatic recovery: ENABLED (retry every 5 seconds)");
    Serial.printf("Pending RAM window: %d/%d files\n", pendingCount, PENDING_WINDOW_SIZE);
    Serial.println("Pending SD backlog: unknown until filesystem is mounted");
    Serial.println("===================\n");
    return;
  }
  uint64_t totalBytes = SD.totalBytes();
  uint64_t usedBytes = SD.usedBytes();
  uint64_t freeBytes = totalBytes > usedBytes ? totalBytes - usedBytes : 0;
  int freePercent = totalBytes > 0 ? (freeBytes * 100) / totalBytes : 0;
  Serial.println("\n=== SD CARD INFO ===");
  Serial.printf("Total space: %llu MB\n", totalBytes / (1024 * 1024));
  Serial.printf("Used space: %llu MB\n", usedBytes / (1024 * 1024));
  Serial.printf("Free space: %llu MB (%d%%)\n", freeBytes / (1024 * 1024), freePercent);
  Serial.printf("Session saved records: %lu\n", totalSavedRecords);
  Serial.printf("Session saved bytes: %lu KB\n", totalBytesWritten / 1024);
  int sdBacklogFiles = countClosedPendingFilesOnSD();
  Serial.printf("Pending RAM window: %d/%d files\n", pendingCount, PENDING_WINDOW_SIZE);
  Serial.printf("Pending SD backlog: %d files\n", sdBacklogFiles);
  Serial.printf("Current file: %s (%d/%d records)\n", currentWriterName.c_str(), recordsInWriter, MAX_RECORDS_PER_FILE);
  Serial.println("===================\n");
}

void listAllFiles() {
  Serial.println("\n📁 FILES ON SD CARD");
  Serial.println("─────────────────────────────────");
  File root = SD.open("/gps_data");
  if (!root) { Serial.println("Failed to open directory"); return; }
  File file = root.openNextFile();
  int fileCount = 0;
  while (file) {
    String fileName = file.name();
    unsigned long fileSize = file.size();
    fileCount++;
    String status = "";
    if (currentWriterName.indexOf(fileName) >= 0) status = " ← WRITING";
    else {
      for (int i = 0; i < pendingCount; i++) if (pendingFiles[i].name.indexOf(fileName) >= 0) { status = " ← PENDING (retry " + String(pendingFiles[i].retryCount) + ")"; break; }
    }
    Serial.printf("%d. %s - %lu KB (%lu bytes)%s\n", fileCount, fileName.c_str(), fileSize / 1024, fileSize, status.c_str());
    file.close(); file = root.openNextFile();
  }
  root.close();
  if (fileCount == 0) Serial.println("No files found");
  Serial.println("─────────────────────────────────\n");
}

void viewFile(String fileName) {
  String fullPath = "/gps_data/" + fileName;
  if (!SD.exists(fullPath)) { Serial.println("File not found: " + fileName); return; }
  File file = SD.open(fullPath, FILE_READ);
  if (!file) { Serial.println("Failed to open file"); return; }
  Serial.println("\n📄 VIEWING: " + fileName);
  Serial.println("─────────────────────────────────");
  Serial.print("File size: "); Serial.print(file.size() / 1024); Serial.println(" KB");
  Serial.println("─────────────────────────────────\n");
  while (file.available()) Serial.println(file.readStringUntil('\n'));
  file.close();
  Serial.println("\n─────────────────────────────────\n");
}

void viewLastFile() {
  if (currentWriterName.length() > 0) {
    String fileName = currentWriterName.substring(currentWriterName.lastIndexOf("/") + 1);
    viewFile(fileName);
  } else Serial.println("No active file");
}

void viewAllJSON() {
  Serial.println("\n📊 ALL JSON FILES");
  Serial.println("=========================================\n");
  File root = SD.open("/gps_data");
  if (!root) return;
  File file = root.openNextFile();
  while (file) {
    String name = file.name();
    file.close();
    viewFile(name);
    file = root.openNextFile();
  }
  root.close();
}

void cleanSDData() {
  Serial.println("\n=== CLEANING SD CARD ===");
  if (writerFile) writerFile.close();
  File root = SD.open("/gps_data");
  if (root) {
    File file = root.openNextFile();
    while (file) {
      String fileName = file.name();
      file.close();
      SD.remove("/gps_data/" + fileName);
      Serial.println("Deleted: " + fileName);
      file = root.openNextFile();
    }
    root.close();
  }
  totalSavedRecords = 0;
  totalBytesWritten = 0;
  recordsInWriter = 0;
  pendingCount = 0;
  currentWriterNumber = 1;
  createNewWriterFile();
  Serial.println("Clean complete!");
  Serial.println("📁 JSON file counter restarted from data_1.json\n");
}

String buildStorageStatusJson() {
  uint64_t totalBytes = SD.totalBytes();
  uint64_t usedBytes = SD.usedBytes();
  uint64_t freeBytes = totalBytes > usedBytes ? totalBytes - usedBytes : 0;
  int freePercent = totalBytes > 0 ? (freeBytes * 100) / totalBytes : 0;
  String extra = "\"sd_total_mb\":" + String((unsigned long)(totalBytes / (1024 * 1024)));
  extra += ",\"sd_used_mb\":" + String((unsigned long)(usedBytes / (1024 * 1024)));
  extra += ",\"sd_free_mb\":" + String((unsigned long)(freeBytes / (1024 * 1024)));
  extra += ",\"sd_free_percent\":" + String(freePercent);
  extra += ",\"total_records\":" + String(totalSavedRecords);
  extra += ",\"total_kb\":" + String(totalBytesWritten / 1024);
  extra += ",\"pending_files\":" + String(pendingCount);
  extra += ",\"current_file\":\"" + jsonEscape(currentWriterName) + "\"";
  extra += ",\"records_in_current_file\":" + String(recordsInWriter);
  extra += ",\"datajson_start_from\":1";
  extra += ",\"current_writer_number\":" + String(currentWriterNumber);
  extra += ",\"Q\":" + uint64ToString(globalQ);
  return extra;
}

void formatSDCard() {
  Serial.println("\n=== FORMAT COMMAND ===");
  Serial.println("Cleaning SD data, restarting data JSON from 1, and resetting Q to 1...");
  cleanSDData();
  resetGlobalQ(1);
  Serial.println("✅ Format command complete");
  Serial.println("📁 Data JSON restarted from data_1.json");
  Serial.println("🔢 ALELS Q restarted from 1\n");
}

// =============================
// === JSON Command Helpers ====
// =============================
String jsonEscape(String value) {
  value.replace("\\", "\\\\");
  value.replace("\"", "\\\"");
  value.replace("\r", "\\r");
  value.replace("\n", "\\n");
  return value;
}

String jsonGetString(String json, String key) {
  String pattern = "\"" + key + "\"";
  int keyPos = json.indexOf(pattern);
  if (keyPos < 0) return "";
  int colon = json.indexOf(':', keyPos + pattern.length());
  if (colon < 0) return "";
  int startQuote = json.indexOf('"', colon + 1);
  if (startQuote < 0) return "";
  String result = "";
  bool escaped = false;
  for (int i = startQuote + 1; i < json.length(); i++) {
    char c = json.charAt(i);
    if (escaped) { if (c == 'n') result += '\n'; else if (c == 'r') result += '\r'; else result += c; escaped = false; }
    else if (c == '\\') escaped = true;
    else if (c == '"') break;
    else result += c;
  }
  result.trim();
  return result;
}

bool jsonGetInt(String json, String key, int &out) {
  String pattern = "\"" + key + "\"";
  int keyPos = json.indexOf(pattern);
  if (keyPos < 0) return false;
  int colon = json.indexOf(':', keyPos + pattern.length());
  if (colon < 0) return false;
  int start = colon + 1;
  while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\t')) start++;
  bool quoted = false;
  if (start < json.length() && json.charAt(start) == '"') { quoted = true; start++; }
  int end = start;
  while (end < json.length()) {
    char c = json.charAt(end);
    if ((c >= '0' && c <= '9') || c == '-') end++;
    else break;
  }
  if (end <= start) return false;
  if (quoted) {
    int quoteEnd = end;
    while (quoteEnd < json.length() && (json.charAt(quoteEnd) == ' ' || json.charAt(quoteEnd) == '\t')) quoteEnd++;
    if (quoteEnd >= json.length() || json.charAt(quoteEnd) != '"') return false;
  }
  out = json.substring(start, end).toInt();
  return true;
}

bool jsonGetULong(String json, String key, unsigned long &out) {
  int temp = 0;
  if (!jsonGetInt(json, key, temp)) return false;
  if (temp < 0) return false;
  out = (unsigned long)temp;
  return true;
}

void sendTCPCommandResponse(String cmd, String status, String message, String extraJson) {
  if (!tcpClient.connected()) return;
  String response;
  response.reserve(1024);
  response = "{\"T\":\"resp\"";
  response += ",\"cmd\":\"" + jsonEscape(cmd) + "\"";
  response += ",\"status\":\"" + jsonEscape(status) + "\"";
  response += ",\"message\":\"" + jsonEscape(message) + "\"";
  response += ",\"imei\":\"" + jsonEscape(getBestIMEI()) + "\"";
  response += ",\"millis\":" + String(millis());
  if (extraJson.length() > 0) response += "," + extraJson;
  response += "}";
  tcpClient.println(response);
  tcpClient.flush();
  Serial.print("📤 TCP Command Response: "); Serial.println(response);
}

void processTCPIncomingLine(String line) {
  line.trim();
  if (line.length() == 0) return;
  if (line.startsWith("{")) {
    String type = jsonGetString(line, "T");
    if (type == "cmd" || jsonGetString(line, "cmd").length() > 0) {
      processTCPJsonCommand(line);
      return;
    }
    Serial.print("📨 Server JSON: "); Serial.println(line);
  } else {
    Serial.print("📨 Server: "); Serial.println(line);
  }
}

void processTCPJsonCommand(String json) {
  String cmdRaw = jsonGetString(json, "cmd");
  cmdRaw.trim();
  String cmd = cmdRaw;
  cmd.toLowerCase();
  if (cmd.length() == 0) { sendTCPCommandResponse("", "error", "missing cmd field"); return; }
  Serial.print("📥 TCP JSON Command: "); Serial.println(json);

  if (cmd.startsWith("set ")) {
    parseAndSetConfig(cmdRaw);
    sendTCPCommandResponse("set", "ok", "configuration updated using legacy set format", "\"legacy_cmd\":\"" + jsonEscape(cmdRaw) + "\"");
    return;
  }
  if (cmd == "ping") sendTCPCommandResponse(cmd, "ok", "pong");
  else if (cmd == "network") {
    String extra = "\"wifi_connected\":" + String(wifiConnected ? "true" : "false");
    extra += ",\"tcp_connected\":" + String(tcpConnected ? "true" : "false");
    extra += ",\"tcp_identified\":" + String(tcpIdentified ? "true" : "false");
    extra += ",\"ssid\":\"" + jsonEscape(WIFI_SSID) + "\"";
    extra += ",\"local_ip\":\"" + WiFi.localIP().toString() + "\"";
    extra += ",\"rssi\":" + String(wifiConnected ? WiFi.RSSI() : 0);
    extra += ",\"server\":\"" + jsonEscape(TCP_SERVER) + "\"";
    extra += ",\"port\":" + String(TCP_PORT);
    extra += ",\"pending_files\":" + String(pendingCount);
    sendTCPCommandResponse(cmd, "ok", "network status", extra);
  }
  else if (cmd == "config") {
    String extra = "\"records\":" + String(MAX_RECORDS_PER_FILE);
    extra += ",\"freespace\":" + String(MIN_FREE_SPACE_PERCENT);
    extra += ",\"retry\":" + String(MAX_RETRY_COUNT);
    extra += ",\"delay\":" + String(BASE_RETRY_DELAY);
    extra += ",\"ssid\":\"" + jsonEscape(WIFI_SSID) + "\"";
    extra += ",\"password\":\"" + jsonEscape(WIFI_PASSWORD) + "\"";
    extra += ",\"serialkey\":\"" + jsonEscape(SERIAL_KEYWORD) + "\"";
    extra += ",\"server\":\"" + jsonEscape(TCP_SERVER) + "\"";
    extra += ",\"port\":" + String(TCP_PORT);
    extra += ",\"raw\":" + String(rawDataEnabled ? "true" : "false");
    sendTCPCommandResponse(cmd, "ok", "current configuration", extra);
  }
  else if (cmd == "info") sendTCPCommandResponse(cmd, "ok", "sd card info", buildStorageStatusJson());
  else if (cmd == "pending") {
    String files = "[";
    for (int i = 0; i < pendingCount; i++) {
      if (i > 0) files += ",";
      files += "{\"name\":\"" + jsonEscape(pendingFiles[i].name) + "\"";
      files += ",\"records\":" + String(pendingFiles[i].recordCount);
      files += ",\"retry\":" + String(pendingFiles[i].retryCount);
      files += ",\"sent\":" + String(pendingFiles[i].isSent ? "true" : "false");
      files += ",\"failed\":" + String(pendingFiles[i].isFailed ? "true" : "false");
      files += "}";
    }
    files += "]";
    sendTCPCommandResponse(cmd, "ok", "pending file list", "\"pending_count\":" + String(pendingCount) + ",\"files\":" + files);
  }
  else if (cmd == "raw") {
    String value = jsonGetString(json, "value"); value.trim(); value.toLowerCase();
    if (value == "on" || value == "true" || value == "1") { rawDataEnabled = true; sendTCPCommandResponse(cmd, "ok", "raw mode enabled", "\"raw\":true"); }
    else if (value == "off" || value == "false" || value == "0") { rawDataEnabled = false; sendTCPCommandResponse(cmd, "ok", "raw mode disabled", "\"raw\":false"); }
    else { rawDataEnabled = !rawDataEnabled; sendTCPCommandResponse(cmd, "ok", "raw mode toggled", "\"raw\":" + String(rawDataEnabled ? "true" : "false")); }
  }
  else if (cmd == "set") {
    bool changed = false;
    String changedFields = "[";
    int intValue;
    unsigned long ulongValue;
    String strValue;
    if (jsonGetInt(json, "records", intValue)) { if (intValue >= 1 && intValue <= 1000) { MAX_RECORDS_PER_FILE = intValue; changed = true; changedFields += "\"records\""; } else { sendTCPCommandResponse(cmd, "error", "invalid records range 1-1000"); return; } }
    if (jsonGetInt(json, "freespace", intValue)) { if (intValue >= 1 && intValue <= 90) { MIN_FREE_SPACE_PERCENT = intValue; if (changedFields.length() > 1) changedFields += ","; changedFields += "\"freespace\""; changed = true; } else { sendTCPCommandResponse(cmd, "error", "invalid freespace range 1-90"); return; } }
    if (jsonGetInt(json, "retry", intValue)) { if (intValue >= 0 && intValue <= 20) { MAX_RETRY_COUNT = intValue; if (changedFields.length() > 1) changedFields += ","; changedFields += "\"retry\""; changed = true; } else { sendTCPCommandResponse(cmd, "error", "invalid retry range 0-20"); return; } }
    if (jsonGetULong(json, "delay", ulongValue)) { if (ulongValue >= 1000 && ulongValue <= 300000) { BASE_RETRY_DELAY = ulongValue; if (changedFields.length() > 1) changedFields += ","; changedFields += "\"delay\""; changed = true; } else { sendTCPCommandResponse(cmd, "error", "invalid delay range 1000-300000"); return; } }
    strValue = jsonGetString(json, "ssid"); if (strValue.length() > 0) { if (strValue.length() <= 32) { WIFI_SSID = strValue; if (changedFields.length() > 1) changedFields += ","; changedFields += "\"ssid\""; changed = true; scheduleNetworkRestart(); } else { sendTCPCommandResponse(cmd, "error", "invalid ssid length 1-32"); return; } }
    strValue = jsonGetString(json, "pass"); if (strValue.length() == 0) strValue = jsonGetString(json, "password"); if (strValue.length() > 0) { if (strValue.length() <= 64) { WIFI_PASSWORD = strValue; if (changedFields.length() > 1) changedFields += ","; changedFields += "\"password\""; changed = true; scheduleNetworkRestart(); } else { sendTCPCommandResponse(cmd, "error", "invalid password length 0-64"); return; } }
    strValue = jsonGetString(json, "server"); if (strValue.length() > 0) { if (strValue.length() <= 64) { TCP_SERVER = strValue; if (changedFields.length() > 1) changedFields += ","; changedFields += "\"server\""; changed = true; scheduleNetworkRestart(); } else { sendTCPCommandResponse(cmd, "error", "invalid server length"); return; } }
    if (jsonGetInt(json, "port", intValue)) { if (intValue >= 1 && intValue <= 65535) { TCP_PORT = intValue; if (changedFields.length() > 1) changedFields += ","; changedFields += "\"port\""; changed = true; scheduleNetworkRestart(); } else { sendTCPCommandResponse(cmd, "error", "invalid port range 1-65535"); return; } }
    changedFields += "]";
    if (changed) { saveConfig(); saveWiFiConfig(); sendTCPCommandResponse(cmd, "ok", "configuration updated", "\"changed\":" + changedFields); }
    else sendTCPCommandResponse(cmd, "error", "no valid set field found");
  }
  else if (cmd == "clean") { cleanSDData(); sendTCPCommandResponse(cmd, "ok", "SD data cleaned and data JSON restarted from data_1.json", buildStorageStatusJson()); }
  else if (cmd == "format") { formatSDCard(); sendTCPCommandResponse(cmd, "ok", "format complete: SD data cleaned, data JSON restarted from 1, and Q restarted from 1", buildStorageStatusJson()); }
  else if (cmd == "reset_q" || cmd == "resetq" || cmd == "qreset" || cmd == "reset q") { resetGlobalQ(1); sendTCPCommandResponse(cmd, "ok", "global Q counter reset to 1", buildStorageStatusJson()); }
  else if (cmd == "resend") { pendingRescanNeeded = true; scanAndAddPendingFiles(); sendTCPCommandResponse(cmd, "ok", "pending files rescanned", "\"pending_count\":" + String(pendingCount)); }
  else if (cmd == "close") { closeWriterFile(); createNewWriterFile(); sendTCPCommandResponse(cmd, "ok", "current file closed and new file created", "\"current_file\":\"" + jsonEscape(currentWriterName) + "\""); }
  else if (cmd == "restart" || cmd == "reboot") { sendTCPCommandResponse(cmd, "ok", "ESP32 restart command accepted; restarting after countdown", "\"countdown_seconds\":5"); delay(200); restartDevice("tcp"); }
  else if (cmd == "reset_network") { resetWiFiConfig(); sendTCPCommandResponse(cmd, "ok", "network config reset to default"); }
  else sendTCPCommandResponse(cmd, "error", "unknown command");
}

// =============================
// === WiFi / TCP ==============
// =============================
void initWiFi() {
  Serial.println("\n=== INITIALIZING WiFi ===");
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  wifiConnected = false;
  tcpConnected = false;
  tcpIdentified = false;
  wifiConnecting = false;
  tcpRxBuffer = "";
  connectToWiFi();
}

void connectToWiFi() {
  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    wifiConnecting = false;
    return;
  }
  if (wifiConnecting) return;
  Serial.print("Starting WiFi connection to: "); Serial.println(WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(false);
  WiFi.begin(WIFI_SSID.c_str(), WIFI_PASSWORD.c_str());
  wifiConnecting = true;
  wifiConnectStartTime = millis();
  lastWiFiReconnectAttempt = wifiConnectStartTime;
}

void connectToTCPServer() {
  if (!wifiConnected) return;
  String imeiForId = getBestIMEI();
  imeiForId.trim();
  if (!isValidIMEIValue(imeiForId)) {
    // Never identify to the gateway with an empty device identity.
    tcpConnected = false;
    tcpIdentified = false;
    if (tcpClient.connected()) tcpClient.stop();
    return;
  }
  if (tcpClient.connected() && tcpConnected && tcpIdentified) return;
  if (tcpClient.connected()) tcpClient.stop();
  Serial.print("Connecting to TCP server: "); Serial.print(TCP_SERVER); Serial.print(":"); Serial.println(TCP_PORT);
  tcpClient.stop();
  tcpClient.setTimeout(TCP_CONNECT_TIMEOUT);
  if (tcpClient.connect(TCP_SERVER.c_str(), TCP_PORT, TCP_CONNECT_TIMEOUT)) {
    tcpConnected = false;
    tcpIdentified = false;
    tcpRxBuffer = "";
    lastTCPKeepAlive = millis();
    lastHeartbeatTime = millis();
    Serial.println("✅ TCP Connected!");
    String identMsg = "{\"T\":\"id\",\"imei\":\"" + jsonEscape(imeiForId) + "\",\"message\":\"device connected\"}";
    Serial.print("📤 TCP ID: "); Serial.println(identMsg);
    tcpClient.println(identMsg);
    tcpClient.flush();
    if (waitServerAck01(2000)) {
      tcpIdentified = true;
      tcpConnected = true;
      // A restored connection should retry the oldest durable file now,
      // without inheriting its backoff from the failed TCP session.
      if (pendingCount > 0) pendingFiles[0].lastRetryTime = 0;
      pendingRescanNeeded = true;
      Serial.println("✅ TCP ID ACK OK, telemetry upload enabled");
    } else {
      tcpIdentified = false;
      tcpConnected = false;
      tcpClient.stop();
      Serial.println("❌ TCP ID ACK failed, reconnect later");
    }
  } else {
    tcpConnected = false;
    tcpIdentified = false;
    tcpClient.stop();
    Serial.println("❌ TCP Connection Failed!");
  }
}

bool sendToTCP(String jsonData) {
  if (!wifiConnected || !tcpConnected || !tcpIdentified || !tcpClient.connected()) {
    tcpConnected = false;
    tcpIdentified = false;
    return false;
  }
  size_t written = tcpClient.println(jsonData);
  tcpClient.flush();
  if (written != jsonData.length() + 2) {
    tcpConnected = false;
    tcpIdentified = false;
    tcpClient.stop();
    return false;
  }
  Serial.print("📤 TCP TX: "); Serial.println(jsonData);
  if (waitServerAck01(3000)) return true;
  tcpConnected = false;
  tcpIdentified = false;
  tcpClient.stop();
  return false;
}

void sendHeartbeat() {
  if (!wifiConnected || !tcpConnected || !tcpIdentified || !tcpClient.connected()) return;
  String imei = getBestIMEI();
  String hb;
  hb.reserve(256);
  hb = "{\"T\":\"hb\"";
  hb += ",\"A\":\"" + jsonEscape(imei) + "\"";
  hb += ",\"Q\":" + uint64ToString(globalQ);
  hb += ",\"W\":" + String(WiFi.RSSI());
  hb += ",\"P\":" + String(pendingCount);
  hb += ",\"H\":" + String(ESP.getFreeHeap());
  hb += ",\"U\":" + String(millis());
  hb += "}";
  tcpClient.println(hb);
  tcpClient.flush();
  Serial.print("💓 TCP HB: "); Serial.println(hb);
  if (!waitServerAck01(1000)) {
    // An uncorrelated late heartbeat ACK must not acknowledge the next record.
    tcpConnected = false;
    tcpIdentified = false;
    tcpClient.stop();
  }
}

void processTCPConnection() {
  unsigned long now = millis();
  if (WiFi.status() != WL_CONNECTED) {
    if (wifiConnected) {
      wifiConnected = false;
      tcpConnected = false;
      tcpIdentified = false;
      tcpRxBuffer = "";
      if (tcpClient.connected()) tcpClient.stop();
      Serial.println("⚠️ WiFi connection lost!");
    }
    if (wifiConnecting) {
      if (now - wifiConnectStartTime >= WIFI_CONNECT_TIMEOUT) {
        wifiConnecting = false;
        WiFi.disconnect(false);
        Serial.println("❌ WiFi connection timeout");
      }
      return;
    }
    if (lastWiFiReconnectAttempt == 0 || now - lastWiFiReconnectAttempt >= WIFI_RECONNECT_INTERVAL) connectToWiFi();
    return;
  }
  if (!wifiConnected) {
    wifiConnected = true;
    wifiConnecting = false;
    tcpConnected = false;
    tcpIdentified = false;
    tcpRxBuffer = "";
    Serial.println("\n✅ WiFi Connected!");
    Serial.print("IP Address: "); Serial.println(WiFi.localIP());
    lastTCPReconnectAttempt = 0;
  }
  if (!tcpClient.connected()) {
    if (tcpConnected || tcpIdentified) {
      tcpConnected = false;
      tcpIdentified = false;
      tcpRxBuffer = "";
      Serial.println("⚠️ TCP connection lost!");
    }
    if (lastTCPReconnectAttempt == 0 || now - lastTCPReconnectAttempt >= TCP_RECONNECT_INTERVAL) {
      lastTCPReconnectAttempt = now;
      connectToTCPServer();
    }
    return;
  }
  if (!tcpConnected || !tcpIdentified) return;
  if (now - lastHeartbeatTime >= HEARTBEAT_INTERVAL && pendingCount == 0) {
    lastHeartbeatTime = now;
    sendHeartbeat();
  }
  while (tcpClient.available()) {
    char c = tcpClient.read();
    if (c == '\n') {
      processTCPIncomingLine(tcpRxBuffer);
      tcpRxBuffer = "";
    } else if (c != '\r') {
      tcpRxBuffer += c;
      if (tcpRxBuffer.length() > TCP_RX_BUFFER_MAX) {
        Serial.println("⚠️ TCP RX buffer overflow, clearing buffer");
        tcpRxBuffer = "";
        sendTCPCommandResponse("", "error", "tcp rx buffer overflow");
      }
    }
  }
}

void showNetworkStatus() {
  Serial.println("\n=== NETWORK STATUS ===");
  Serial.print("WiFi SSID: "); Serial.println(WIFI_SSID);
  Serial.print("WiFi Status: ");
  if (wifiConnected) Serial.println("Connected");
  else if (wifiConnecting) Serial.println("Connecting");
  else Serial.println("Disconnected");
  if (wifiConnected) {
    Serial.print("IP Address: "); Serial.println(WiFi.localIP());
    Serial.print("RSSI: "); Serial.print(WiFi.RSSI()); Serial.println(" dBm");
  }
  Serial.print("TCP Server: "); Serial.print(TCP_SERVER); Serial.print(":"); Serial.println(TCP_PORT);
  Serial.print("TCP Status: "); Serial.println(tcpConnected ? "Connected" : "Disconnected");
  Serial.print("TCP Identified: "); Serial.println(tcpIdentified ? "YES" : "NO");
  Serial.print("Pending files: "); Serial.println(pendingCount);
  if (pendingNetworkRestart) {
    unsigned long remaining = (networkRestartTimer + NETWORK_RESTART_DELAY - millis()) / 1000;
    Serial.printf("Network restart pending in: %lu seconds\n", remaining);
  }
  Serial.println("=====================\n");
}

// =============================
// === Serial Command Handler ==
// =============================
void processSerialCommand(String cmdRaw) {
  cmdRaw.trim();
  if (cmdRaw.length() == 0) return;
  if (!serialAuthenticated) {
    if (cmdRaw == SERIAL_KEYWORD) {
      serialAuthenticated = true;
      serialPromptShown = false;
      showSerialUnlockedInfo();
    } else Serial.println("❌ Invalid keyword. Serial command console remains locked.");
    return;
  }
  String cmd = cmdRaw;
  cmd.trim(); cmd.toLowerCase();
  if (cmd == "logout" || cmd == "lock") { serialAuthenticated = false; serialPromptShown = false; Serial.println("🔒 Serial console locked"); showSerialLockedPrompt(); }
  else if (cmd.startsWith("serialkey ")) { String newKey = cmdRaw.substring(cmdRaw.indexOf(' ') + 1); setSerialKeyword(newKey); }
  else if (cmd == "info") showSDCardInfo();
  else if (cmd == "clean") cleanSDData();
  else if (cmd == "format") formatSDCard();
  else if (cmd == "list") listAllFiles();
  else if (cmd == "view") viewLastFile();
  else if (cmd == "viewall") viewAllJSON();
  else if (cmd == "close") closeWriterFile();
  else if (cmd == "network") showNetworkStatus();
  else if (cmd == "config") showConfig();
  else if (cmd == "pending") {
    int sdBacklogFiles = countClosedPendingFilesOnSD();
    Serial.printf("\n📋 Pending RAM window: %d/%d | SD backlog: %d files\n", pendingCount, PENDING_WINDOW_SIZE, sdBacklogFiles);
    for (int i = 0; i < pendingCount; i++) {
      Serial.printf("  %d. %s - records: %d, retry: %d, sent: %d, failed: %d\n", i+1, pendingFiles[i].name.c_str(), pendingFiles[i].recordCount, pendingFiles[i].retryCount, pendingFiles[i].isSent, pendingFiles[i].isFailed);
    }
  }
  else if (cmd == "resend") { pendingRescanNeeded = true; scanAndAddPendingFiles(); }
  else if (cmd == "raw") toggleRawData();
  else if (cmd == "raw on") { if (!rawDataEnabled) toggleRawData(); else showRawDataStatus(); }
  else if (cmd == "raw off") { if (rawDataEnabled) toggleRawData(); else showRawDataStatus(); }
  else if (cmd == "raw status") showRawDataStatus();
  else if (cmd == "reset q" || cmd == "resetq" || cmd == "qreset") { resetGlobalQ(1); Serial.println("✅ ALELS Global Q counter reset to 1"); }
  else if (cmd == "restart" || cmd == "reboot") restartDevice("serial");
  else if (cmd == "reset config") { setMaxRecordsPerFile(DEFAULT_MAX_RECORDS_PER_FILE); setMinFreeSpacePercent(DEFAULT_MIN_FREE_SPACE_PERCENT); setMaxRetryCount(DEFAULT_MAX_RETRY_COUNT); setBaseRetryDelay(DEFAULT_BASE_RETRY_DELAY); Serial.println("✅ All file system config reset to default!"); }
  else if (cmd == "reset network") resetWiFiConfig();
  else if (cmd.startsWith("set ")) parseAndSetConfig(cmdRaw);
  else if (cmd.startsWith("view ")) { String fileName = cmdRaw.substring(cmdRaw.indexOf(' ') + 1); fileName.trim(); viewFile(fileName); }
  else Serial.println("❌ Unknown serial command: " + cmdRaw);
}

// =============================
// === Setup ===================
// =============================
void setup() {
  Serial.begin(115200);
  SerialGPS.begin(115200, SERIAL_8N1, SERIAL_RX_PIN, SERIAL_TX_PIN);
  Serial.println("\n╔═══════════════════════════════════════╗");
  Serial.println("║   GPS to JSON Parser with SD Card    ║");
  Serial.println("║        + Dynamic File System         ║");
  Serial.println("║            + WiFi & TCP               ║");
  Serial.println("║    + Stable SD Backlog Core v2.9.1   ║");
  Serial.println("║        + Auto IMEI Detection         ║");
  Serial.println("║        + RAW Data Toggle             ║");
  Serial.println("║        + Serial Keyword Security     ║");
  Serial.println("╚═══════════════════════════════════════╝");
  Serial.println();
  loadConfig();
  loadWiFiConfig();
  loadGlobalQ();
  loadRememberedIMEI();
  loadSerialSecurityConfig();
  initSDCard();
  initWiFi();
  Serial.println("\n📋 COMMANDS:");
  Serial.println("  info / clean / format / list / view / viewall / close");
  Serial.println("  network / config / pending / resend / raw / raw on / raw off");
  Serial.println("  set records=50 freespace=15 retry=3 delay=10000");
  Serial.println("  set ssid=MyWiFi pass=1234 server=192.168.1.1 port=5050");
  Serial.println("  serialkey <keyword> / reset q / restart / logout");
  Serial.println("\n✅ Ready, waiting for GPS data...\n");
}

void loop() {
  processGPSData();
  processSDRecovery();
  processWriterIdleClose();
  processTCPConnection();
  processPendingFiles();
  processNetworkRestart();
  processSerialSecurityState();
  while (Serial.available()) {
    String cmdRaw = Serial.readStringUntil('\n');
    processSerialCommand(cmdRaw);
  }
  delay(10);
}
