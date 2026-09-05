# Offline Mesh Tracker (OMT)

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(minSdk%2026%2C%20targetSdk%2034)-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.6.1-orange.svg)](https://developer.android.com/training/data-storage/room)
[![Release](https://img.shields.io/badge/Release-v1.1.0-blue.svg)](https://github.com/Sandeshsandy001/offline-mesh-tracker/releases)

**Offline Mesh Tracker (OMT)** is a 100% internet-free, zero-battery-drain Android tracking application operating purely over cellular signaling channels (SMS). It enables peer-to-peer location tracking with zero cloud dependencies, cryptographic authentication, dual-SIM support, parallel GPS acquisition, and one-tap Google Maps visualization.

---

## 📲 Download & Installation

You do not need Android Studio or a computer to install this app. You can install the pre-built APK directly onto any Android device:

👉 **[Download Latest APK (v1.1.0)](https://github.com/Sandeshsandy001/offline-mesh-tracker/releases/latest)**

### How to Install:
1. Download `app-debug.apk` onto your Android phone.
2. Tap the downloaded file to install it.
3. If prompted by Android (*"Install unknown apps"*), allow permission from your browser or file manager.
4. Open the app and grant the requested **Location** and **SMS** permissions.

---

## 🚀 Quick Start Guide (Testing With 2 Phones)

Because OMT is an offline peer-to-peer system that uses cellular SMS instead of an internet cloud server, tracking works between **two devices**:

```
[Phone A: Requester] ──(1. SMS Request)──► [Phone B: Target]
                                                │ (Grabs GPS)
[Phone A: Requester] ◄─(2. SMS Response)─┘
 ↳ Displays Live Coordinates, Battery %, and Map Button!
```

### Step 1: Install on Both Devices
Install `app-debug.apk` on **Phone A** and **Phone B**.

### Step 2: Configure Network Credentials (Must Match!)
On **both** devices, enter the exact same network credentials:
* **Family Group ID**: `FAM_01` (Must be identical on both)
* **Secret Auth Key**: `SECRET_KEY_123` (Must be identical on both)

### Step 3: Assign Device IDs
* On **Phone A**:
  * **Device ID**: `DEV_01`
  * **Name**: `Dad` (or `My Phone`)
  * **Phone Number**: Enter Phone A's real SIM phone number.
* On **Phone B**:
  * **Device ID**: `DEV_02`
  * **Name**: `Kid` (or `Traveler`)
  * **Phone Number**: Enter Phone B's real SIM phone number.

### Step 4: Add Peer Device to Dashboard
* On **Phone A**: Tap **"+" (Add Device)** and add Phone B:
  * ID: `DEV_02`, Name: `Kid`, Phone Number: Phone B's number.
* Tap **"Locate"**:
  * Phone A sends an encrypted location request SMS.
  * Phone B receives the request in the background, resolves fresh GPS coordinates, and replies with its location SMS.
  * Phone A's dashboard automatically displays the coordinates, battery %, and enables the **Google Maps** button!

---

## 🏛️ System Architecture

```
REQUESTER DEVICE (Admin / Family Member)
      │
      │ 1. User taps "Locate [Target]"
      ▼
[Dashboard UI / ViewModel]
      │ Reads Local Device ID, Target Phone Number, Group ID, Auth Key from Room DB
      ▼
[MeshProtocolParser] ──> Serializes: "FAM_01|TYPE:REQ|FROM:DEV_01|TO:DEV_02|AUTH:SEC_HASH"
      │
      ▼
[SmsManager API] ──> Sends direct cellular SMS (No Mobile Data / No Internet Required)
      │
════════════════════════════════════════════════════════════════════════════
                      CELLULAR SIGNALING CHANNEL (SMS)
════════════════════════════════════════════════════════════════════════════
      │
      ▼
TARGET DEVICE (Traveler / Child / Asset)
      │
      ▼
[Telephony Baseband] ──> Receives SMS (0% CPU baseline prior to event)
      │
      ▼
[SmsTriggerReceiver] (Static BroadcastReceiver with Priority 999)
      ├─► Check 1: Normalize phone number (Match last 10 digits against Room DB Whitelist)
      ├─► Check 2: Parse payload (Group ID == Expected, Target ID == Local ID, Auth Key == Valid)
      │
      └─► [VALID MATCH] ──> Launch Foreground Service (type="location") + goAsync() safety
            │
            ▼
      [LocationDispatcherService] (Foreground Service)
            │
            ├── Parallel Race Strategy:
            │   ├─ Fast Path: Read cached coordinates via fusedLocationClient.getLastLocation()
            │   └─ Accurate Path: Request fresh GPS fix via fusedLocationClient.getCurrentLocation(HIGH_ACCURACY)
            │   └─ Timeout Resolver: 10-second hard limit. Uses fresh GPS if resolved, else cached fix.
            │
            ├─► [MeshProtocolParser] ──> Serializes:
            │   "FAM_01|TYPE:RESP|FROM:DEV_02|TO:DEV_01|LAT:27.678412|LON:84.431290|ACC:6|BAT:84|TS:1725201300"
            │
            ├─► [SmsManager API] (Scoped to receiving subId) ──> Dispatches response SMS to Requester
            │
            └─► [Teardown] ──> Release WakeLock ──> Call stopSelf() ──> Immediate 0% CPU Deep Sleep
```

---

## 📡 Deterministic SMS Protocol

### 1. Request Payload (`TYPE:REQ`)
```text
<FAMILY_GROUP_ID>|TYPE:REQ|FROM:<REQUESTER_DEV_ID>|TO:<TARGET_DEV_ID>|AUTH:<AUTH_KEY>
```
*Example:* `FAM_01|TYPE:REQ|FROM:DEV_01|TO:DEV_02|AUTH:SECRET_KEY_123`

### 2. Response Payload (`TYPE:RESP`)
```text
<FAMILY_GROUP_ID>|TYPE:RESP|FROM:<RESPONDER_DEV_ID>|TO:<REQUESTER_DEV_ID>|LAT:<LATITUDE>|LON:<LONGITUDE>|ACC:<ACCURACY_METERS>|BAT:<BATTERY_PERCENT>|TS:<TIMESTAMP_MILLIS>
```
*Example:* `FAM_01|TYPE:RESP|FROM:DEV_02|TO:DEV_01|LAT:27.678412|LON:84.431290|ACC:6|BAT:84|TS:1725201300000`

---

## ⚙️ Device Provisioning & Post-Install Checklist

To bypass aggressive OEM background killers across devices (Xiaomi, Samsung, Vivo, Oppo), configure the following after installing:

| Category | Setting | Requirement |
|---|---|---|
| **Core Permissions** | Location | Select **"Allow all the time"** |
| | SMS | Allow `SEND_SMS` and `RECEIVE_SMS` |
| | Phone State | Allow (for Dual-SIM `subId` resolution) |
| **Battery & OS** | Stock Android | `Settings > Apps > OMT > Battery > Unrestricted` |
| | Xiaomi / HyperOS | `App Info > Enable Autostart` & `Battery Saver: No restrictions` |
| | Samsung (OneUI) | `Device Care > Battery > Background usage limits > Never auto sleeping apps` |
| **Mesh Pairing** | Group ID | Set identical `Family Group ID` (e.g. `FAM_01`) across all family devices |
| | Secret Auth Key | Set identical `Secret Auth Key` across all family devices |
| | Device IDs | Assign unique IDs (e.g. `DEV_01`, `DEV_02`, `DEV_03`) |

---

## 📂 Project Structure

```
app/src/main/
├── AndroidManifest.xml
├── res/
│   ├── values/
│   │   ├── colors.xml
│   │   ├── strings.xml
│   │   └── themes.xml
│   └── xml/
│       ├── backup_rules.xml
│       └── data_extraction_rules.xml
└── java/com/offlinetracker/
    ├── MainActivity.kt
    ├── data/
    │   ├── local/
    │   │   ├── AppConfigEntity.kt
    │   │   ├── FamilyDeviceEntity.kt
    │   │   ├── LocationLogEntity.kt
    │   │   ├── AppConfigDao.kt
    │   │   ├── FamilyDeviceDao.kt
    │   │   ├── LocationLogDao.kt
    │   │   └── OfflineMeshDatabase.kt
    │   └── repository/
    │       └── TrackerRepository.kt
    ├── protocol/
    │   ├── MeshMessage.kt
    │   ├── MeshProtocolParser.kt
    │   └── PhoneNumberNormalizer.kt
    ├── receiver/
    │   └── SmsTriggerReceiver.kt
    ├── service/
    │   └── LocationDispatcherService.kt
    └── ui/
        ├── theme/
        │   ├── Color.kt
        │   ├── Theme.kt
        │   └── Type.kt
        ├── onboarding/
        │   ├── OnboardingViewModel.kt
        │   └── OnboardingScreen.kt
        └── dashboard/
            ├── FamilyMemberUiModel.kt
            ├── DashboardViewModel.kt
            └── FamilyDashboardScreen.kt
```

---

## 🛠️ Build & Development

- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin 1.9.24 (JDK 21 / JVM 17)
- **Room Compiler**: KSP 1.9.24-1.0.20
- **Jetpack Compose**: Kotlin Compiler Extension 1.5.14 with Material 3 BOM `2024.06.00`
