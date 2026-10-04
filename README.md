# Belagavi Tourism

> AI-powered tourism and local discovery platform for Belagavi district, Karnataka — available as a web application and native Android app.

[![Live Demo](https://img.shields.io/badge/🌐_Live_Demo-belagavi--tourism--planner.web.app-2ea44f?style=flat-square)](https://belagavi-tourism-planner.web.app)
[![Android](https://img.shields.io/badge/Android-v1.0.0-3DDC84?style=flat-square&logo=android&logoColor=white)](https://github.com/Yuvaraj-ui132/Belagavi_Tourism/releases/latest)
[![Firebase](https://img.shields.io/badge/Firebase-Auth_%26_Firestore-FFCA28?style=flat-square&logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack_Compose-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Python](https://img.shields.io/badge/Python-FastAPI_Backend-3776AB?style=flat-square&logo=python&logoColor=white)](https://fastapi.tiangolo.com/)

---

## 🌐 Live Demo

👉 **[https://belagavi-tourism-planner.web.app](https://belagavi-tourism-planner.web.app)**

---

## 📱 Download Android App

**[⬇️ Download Latest APK — v1.0.0](https://github.com/Yuvaraj-ui132/Belagavi_Tourism/releases/latest)**

The latest signed release APK is available under [GitHub Releases](https://github.com/Yuvaraj-ui132/Belagavi_Tourism/releases).

**Requirements:**
- Android 7.0 (API 24) or higher
- Allow installation from unknown sources (Settings → Security → Install unknown apps)

---

## 📌 Overview

**Belagavi Tourism** is a full-stack tourism discovery platform for exploring the heritage, natural landscapes, and cultural attractions of Belagavi District (Karnataka, India). It combines an AI-powered assistant, interactive mapping, real-time Firebase synchronization, and travel budget tracking into a seamless cross-platform experience.

---

## ✨ Features

### Android App
- **AI Tourism Assistant** — Conversational AI powered by Gemini and Tavily web research for real-time destination answers
- **Local Destination Discovery** — Browse, search, and filter curated destinations across Belagavi district
- **Interactive Explore & Map** — Map-based exploration with location-aware destination discovery
- **GPS / Location Services** — Location-aware features and distance calculations
- **Budget & Expense Tracking** — Built-in trip expense recorder with category tracking
- **Saved Destinations** — Bookmark and sync favourite places across devices
- **User Authentication** — Firebase Auth with email/password and Google sign-in
- **Profile & Account Sync** — Cloud-backed profile synced in real-time via Firestore
- **Forgot Password / Reset** — Firebase password reset flow

### Web Application
- **Interactive Map** — Full-screen GIS map with Google Maps API, marker clustering, and category filters
- **Traffic-Aware Routing** — Real-time distance and turn-by-turn navigation from user location
- **Community Reviews & Ratings** — User-contributed reviews stored in Cloud Firestore
- **Wishlist & Bookmarks** — Save favourite destinations synced to Firestore
- **PWA Support** — Installable Progressive Web App with offline caching

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| **Android UI** | Kotlin, Jetpack Compose, Material 3 |
| **Android DI** | Dagger Hilt |
| **Android Networking** | Retrofit 2, OkHttp |
| **AI Assistant** | Gemini API, Tavily Search API |
| **Authentication** | Firebase Authentication (Email + Google OAuth) |
| **Database** | Cloud Firestore |
| **Location** | Google Play Services Location |
| **AI Backend** | Python, FastAPI |
| **RAG Pipeline** | Gemini + Tavily web research |
| **Web Frontend** | HTML5, CSS3, JavaScript (ES6+), Bootstrap 5 |
| **Maps** | Google Maps JavaScript API |
| **Hosting** | Firebase Hosting |

---

## 📂 Project Structure

```
Belagavi_Tourism/
├── android-app/                    # Native Android app (Jetpack Compose)
│   ├── app/
│   │   ├── src/main/java/com/belagavi/tourism/
│   │   │   ├── data/               # Repositories, API services, models
│   │   │   ├── di/                 # Hilt dependency injection modules
│   │   │   ├── ui/                 # Compose screens (auth, dashboard, navigation)
│   │   │   └── SmartTourismApp.kt  # Application entry point
│   │   ├── build.gradle.kts        # App-level Gradle config
│   │   └── proguard-rules.pro
│   ├── build.gradle.kts            # Project-level Gradle config
│   └── settings.gradle.kts
├── backend/                        # Python FastAPI AI backend
│   ├── app/
│   │   ├── services/               # AI, RAG, and web research services
│   │   └── main.py
│   └── requirements.txt
├── public/                         # Web application source
│   ├── index.html
│   ├── css/
│   └── js/
├── firebase.json                   # Firebase Hosting config
├── firestore.rules                 # Firestore security rules
└── README.md
```

---

## 🚀 Android — Local Build

### Prerequisites
- Android Studio Hedgehog or newer
- Android SDK API 34
- JDK 8+

### 1. Clone Repository
```bash
git clone https://github.com/Yuvaraj-ui132/Belagavi_Tourism.git
cd Belagavi_Tourism/android-app
```

### 2. Configure local.properties
Create `android-app/local.properties` with your SDK path:
```properties
sdk.dir=/path/to/your/Android/Sdk
```

### 3. Add google-services.json
Place your Firebase `google-services.json` in `android-app/app/`.  
(Obtain from the [Firebase Console](https://console.firebase.google.com/))

### 4. Build & Run
```bash
./gradlew assembleDebug
```
Or open in Android Studio and run directly on a device or emulator.

---

## 🌐 Web — Local Development

```bash
git clone https://github.com/Yuvaraj-ui132/Belagavi_Tourism.git
cd Belagavi_Tourism
npx http-server -p 5000
# Open http://localhost:5000
```

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

## 👤 Author

**Yuvaraj Murkunde**
- GitHub: [@Yuvaraj-ui132](https://github.com/Yuvaraj-ui132)
- Live Project: [belagavi-tourism-planner.web.app](https://belagavi-tourism-planner.web.app)
