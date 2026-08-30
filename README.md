# 📱 SURE Touch (SURE ProEd) Android Application

[![Android Build](https://img.shields.io/badge/Android-Jetpack%20Compose-green.svg)](https://developer.android.com/jetpack/compose)
[![Version](https://img.shields.io/badge/Release-v1.1.0-blue.svg)](https://github.com/Albertpradeep-007/SureTouch-APP/releases/tag/v1.1.0)
[![Target SDK](https://img.shields.io/badge/Target%20SDK-35-orange.svg)](https://developer.android.com/about/versions/15)
[![License](https://img.shields.io/badge/License-SURE%20Trust-red.svg)](https://suretrust.org)

Official native Android application for **SURE Trust (SURE ProEd)** — enabling students, mentors, volunteers, and trustees to track academic progress, live classes, course assignments, screening exams, certificates, and real-time attendance seamlessly.

---

## 🌟 Key Features

### 🎓 Student Dashboard & Academic Journey
- **Live Class Schedule & Timetable**: Real-time sync with upcoming mentor sessions and live attendance tracking.
- **Course Enrollment & Assignments**: View enrolled courses, syllabus progress, submit assignments, and review grades.
- **Screening & Module Exams**: Secure in-app screening examination portal with instant scoring and result tracking.
- **Certificates & Verification**: Digital credential verification with direct download and QR validation.
- **Document Viewer**: Native in-app PDF and resource preview without external app dependencies.

### 👨‍🏫 Mentor & Trustee Management
- **Mentor Desk**: Track cohort students, evaluate assignments, and schedule recurring doubt-clearing sessions.
- **Trustee & Volunteer Workspaces**: View real-time organizational metrics, student outreach statistics, and session reports.
- **Broadcast Announcements**: Push critical alerts, exam updates, and circulars directly to student devices.

### 🔄 Over-The-Air (OTA) In-App Updates
- Seamless background version checks powered by Django backend API (/api/app/version-check/).
- GitHub Releases CDN-backed APK streaming with live progress bar and zero downtime.
- Automatic installation dialogs with optional or mandatory update flags.

---

## 🏗️ Technical Architecture

- **Architecture**: Clean Architecture + MVVM (Model-View-ViewModel) + Repository Pattern
- **UI Framework**: Modern **Jetpack Compose** with Material 3 Design & dynamic dark/light theme support
- **Networking**: Retrofit 2 + OkHttp 4 + Kotlin Coroutines & Flow
- **Authentication**: JWT Bearer Tokens with automatic refresh flow & encrypted secure storage
- **Background Tasks**: Android AlarmManager + Foreground Notification Channels for class schedule reminders

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- **Android Studio Ladybug (2024.2+)** or newer
- **JDK 17 / JDK 21**
- **Android SDK Platform 35**

### Clone & Open
`ash
git clone https://github.com/Albertpradeep-007/SureTouch-APP.git
cd SureTouch-APP
`

### Build APK via CLI
#### Production Release Build:
`ash
gradlew.bat assembleRelease
`
*Generated APK:* pp/build/outputs/apk/release/app-release.apk

#### Debug / OTA Build:
`ash
gradlew.bat assembleDebug
`
*Generated APK:* pp/build/outputs/apk/debug/app-debug.apk

---

## 📦 Version History & Releases

| Version | Version Code | Release Date | Key Highlights |
|---|---|---|---|
| **v1.1.0** | 2 | August 2026 | OTA update system integration, profile management, live timetable sync, exam submission fixes |
| **v1.0.0** | 1 | August 2026 | Initial baseline release with student dashboard, course catalogs, and JWT auth |

---

## 🔒 Security & Privacy
- Zero cleartext traffic allowed in production (usesCleartextTraffic=false).
- JWT tokens and session credentials stored in Android Keystore encrypted preferences.
- Strict Proguard & R8 code shrinking rules enabled for release builds.

---

## 📄 License & Ownership
Copyright © 2026 **SURE Trust (Skill Upgradation for Rural-youth Empowerment)**. All rights reserved.
