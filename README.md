# Offline Mesh Tracker (OMT)

[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(minSdk%2026%2C%20targetSdk%2034)-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-purple.svg)](https://developer.android.com/jetpack/compose)
[![Room](https://img.shields.io/badge/Room%20DB-2.6.1-orange.svg)](https://developer.android.com/training/data-storage/room)

**Offline Mesh Tracker (OMT)** is a 100% internet-free, zero-battery-drain Android tracking application operating purely over cellular signaling channels (SMS). It provides peer-to-peer location tracking with multi-device mesh routing, local cryptographic authentication, parallel GPS acquisition, and deep-linked map visualization.

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

## 📂 Project Directory Structure

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
    ├── OfflineTrackerApp.kt
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

## ⚙️ Device Provisioning & Post-Install Setup Checklist

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

## 🛠️ Build & Development

- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 34 (Android 14)
- **Language**: Kotlin 1.9.24 (JVM 17)
- **Room Compiler**: KSP 1.9.24-1.0.20
- **Jetpack Compose**: Kotlin Compiler Extension 1.5.14 with Material 3 BOM `2024.06.00`
