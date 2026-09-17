# Belagavi Tourism Planner

> A full-stack tourism discovery and navigation web platform for exploring the heritage, natural landscapes, and cultural attractions of Belagavi District.

[![Live Demo](https://img.shields.io/badge/🌐_Live_Demo-belagavi--tourism--planner.web.app-2ea44f?style=flat-square)](https://belagavi-tourism-planner.web.app)
[![JavaScript](https://img.shields.io/badge/JavaScript-ES6+-F7DF1E?style=flat-square&logo=javascript&logoColor=black)](https://developer.mozilla.org/en-US/docs/Web/JavaScript)
[![Firebase](https://img.shields.io/badge/Firebase-Auth_%26_Firestore-FFCA28?style=flat-square&logo=firebase&logoColor=black)](https://firebase.google.com/)
[![Google Maps](https://img.shields.io/badge/Google_Maps-API-4285F4?style=flat-square&logo=googlemaps&logoColor=white)](https://developers.google.com/maps)
[![PWA](https://img.shields.io/badge/PWA-Ready-5A0FC8?style=flat-square)](https://web.dev/progressive-web-apps/)

---

## 🌐 Live Demo

Explore the live application:  
👉 **[https://belagavi-tourism-planner.web.app](https://belagavi-tourism-planner.web.app)**

---

## 📌 Overview

**Belagavi Tourism Planner** is designed to provide tourists, travelers, and locals with an all-in-one guide to Belagavi District (Karnataka, India). The application combines GIS mapping, routing, user reviews, expense budgeting, and multi-device access into a clean, modern web application and Progressive Web App (PWA).

---

## ✨ Features

- **Interactive Map Exploration**: Full-screen GIS map view using Google Maps JavaScript API with marker clustering, category filtering (Forts, Waterfalls, Temples, Nature, Heritage), and location search.
- **Traffic-Aware Navigation & Routing**: Real-time distance calculation, estimated travel duration, and turn-by-turn road navigation from user's current location.
- **Travel Budget & Expense Manager**: Built-in trip expense tracker allowing travelers to record food, stay, travel, and miscellaneous costs with live budget tallying.
- **Wishlist & Bookmarks**: Save favorite destinations to personal itineraries synced in real-time.
- **Community Reviews & Ratings**: User-contributed reviews, star ratings, and travel tips stored in Cloud Firestore.
- **Authentication**: Secure user sign-in via Firebase Authentication (Email/Password and Google OAuth).
- **Progressive Web App (PWA)**: Installable on mobile and desktop devices with offline caching of core assets.

---

## 🛠️ Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| **Frontend** | HTML5, CSS3, JavaScript (ES6+), Bootstrap 5 | Responsive UI and client-side application logic |
| **Maps & GIS** | Google Maps JavaScript API, MarkerClusterer | Map rendering, markers, geolocation, and routing |
| **Authentication** | Firebase Authentication | Google OAuth and Email/Password auth |
| **Database** | Google Cloud Firestore | Real-time storage for places, reviews, wishlists, and expenses |
| **Backend / API** | Python / Flask (or Serverless Firebase) | Service endpoints and administrative tooling |
| **Hosting** | Firebase Hosting | Fast global CDN delivery |

---

## 📂 Project Structure

```
Belagavi_Tourism/
├── index.html               # Main single-page application shell
├── css/
│   └── style.css            # Custom layout & theme stylesheets
├── js/
│   ├── app.js               # Core application logic & UI controllers
│   ├── map.js               # Google Maps integration, clustering & routing
│   ├── firebase-config.js   # Firebase client SDK initialization
│   ├── auth.js              # Authentication state handlers
│   └── expenses.js          # Trip expense calculator & storage
├── data/
│   └── places.json          # Curated destination database & metadata
├── android-app/             # Native Android / Jetpack Compose companion app
├── manifest.json            # PWA Web App Manifest
├── firebase.json            # Firebase Hosting configuration
└── README.md
```

---

## 🚀 Getting Started (Local Development)

### 1. Clone Repository
```bash
git clone https://github.com/Yuvaraj-ui132/Belagavi_Tourism.git
cd Belagavi_Tourism
```

### 2. Run Locally
Serve using any static web server:
```bash
npx http-server -p 5000
# or: python -m http.server 5000
```

Open [http://localhost:5000](http://localhost:5000) in your browser.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

---

## 👤 Author

**Yuvaraj Murkunde**  
- GitHub: [@Yuvaraj-ui132](https://github.com/Yuvaraj-ui132)  
- Live Project: [belagavi-tourism-planner.web.app](https://belagavi-tourism-planner.web.app)
