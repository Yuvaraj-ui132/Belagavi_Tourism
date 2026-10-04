# Belagavi Tourism
### AI-Powered Tourism & Travel Planning Platform for Belagavi

Belagavi Tourism is a full-stack, cross-platform tourism engineering platform designed for exploring the heritage, natural landscapes, and cultural landmarks of Belagavi District (Karnataka, India). The platform integrates a native Android application and a responsive web application backed by a serverless FastAPI backend, leveraging Retrieval-Augmented Generation (RAG) with Google Gemini and Tavily web research, Cloud Firestore synchronization, and PostgreSQL with pgvector for semantic discovery.

| Resource | Link |
|---|---|
| 🌐 **Live Web Application** | [belagavi-tourism-planner.web.app](https://belagavi-tourism-planner.web.app) |
| 📱 **Android Production Release** | [GitHub Release v1.0.0](https://github.com/Yuvaraj-ui132/Belagavi_Tourism/releases/tag/v1.0.0) |
| ⚙️ **Production API Base** | [belagavi-tourism-yuvaraj21.vercel.app](https://belagavi-tourism-yuvaraj21.vercel.app) |
| 📦 **Repository** | [github.com/Yuvaraj-ui132/Belagavi_Tourism](https://github.com/Yuvaraj-ui132/Belagavi_Tourism) |

---

## 1. Overview

Regional tourism information is frequently fragmented across outdated blogs, unverified directory listings, and static travel guides. Travelers often struggle to find accurate opening hours, seasonal road accessibility (such as Western Ghats monsoon conditions), verified entry fees, and contextual historical background.

**Belagavi Tourism** addresses this by unifying destination discovery, GIS mapping, trip budget tracking, user reviews, and an authenticated AI travel assistant into a production-grade ecosystem. The system pairs deterministic database records (verified GPS coordinates, fees, categories) with generative AI grounded via semantic vector search and real-time web verification, ensuring high-fidelity answers without hallucinated travel information.

---

## 2. Platforms & Deployment Status

| Platform | Role | Technology Stack | Deployment / Distribution | Status |
|---|---|---|---|---|
| **Android Application** | Native Mobile Client | Kotlin, Jetpack Compose, Material 3, Hilt, Coroutines, Retrofit | GitHub Release (Signed APK, Target SDK 34, Min SDK 24, R8 enabled) | ✅ Production (`v1.0.0`) |
| **Web Application** | Progressive Web App | HTML5, Modern CSS3, ES6+ JavaScript, Leaflet GIS, Firebase SDK | Firebase Hosting (Global CDN, SSL, Custom PWA caching) | ✅ Production |
| **Backend API** | AI / RAG & Search API | Python 3.10+, FastAPI, Pydantic, SQLAlchemy 2.0, Firebase Admin | Vercel Serverless (Edge routing, path normalization) | ✅ Production |
| **Primary Relational DB** | Destination & Vector Store | PostgreSQL + pgvector extension | Cloud PostgreSQL instance (Connection pooled) | ✅ Production |
| **App State & Sync DB** | User Profiles, Reviews & Wishlist | Google Cloud Firestore (NoSQL) | Managed Firebase Cloud Firestore (Rules secured) | ✅ Production |

---

## 3. Features

### Destination Discovery & Exploration
- **Curated Regional Catalog**: Detailed directory of heritage forts (Belagavi Fort, Kittur, Parasgad), waterfalls (Gokak Falls, Sada Falls, Vajrapoha), ancient temples, and wildlife sanctuaries across Belagavi district.
- **Rich Attraction Profiles**: Structured attributes including operational timings, seasonal recommendations, entry fees, photography rules, and curated photo galleries.
- **Category & Semantic Filtering**: Filter by nature, heritage, pilgrimage, and adventure, or query via natural language.

### Interactive GIS Mapping & Navigation
- **Web GIS Interface**: Leaflet-powered GIS mapping with OpenStreetMap tiles, custom destination markers, and cluster views.
- **Geolocation & Routing**: Real-time geolocation detection, route calculation, and turn-by-turn navigation links.
- **Android Location Awareness**: Integrated Google Play Services Location SDK for proximity calculation and distance sorting on mobile devices.

### AI Travel Assistant (RAG Pipeline)
- **Grounded Conversational Guide**: Real-time conversational assistant answering complex travel queries ("What are quiet historical spots near Belagavi with parking?").
- **Hybrid Retrieval**: Combines semantic embeddings with dynamic web research via Tavily to answer temporal queries (weather, road closures, current festivals).
- **Zero Hallucination Overlay**: Factual attributes (coordinates, fees, timings) are deterministically merged from verified database rows.

### Trip Planning & Cloud Synchronization
- **Personal Wishlist**: Save and organize destinations, synced instantly across sessions via Cloud Firestore.
- **Budget & Expense Tracker**: Category-tagged travel expense management (travel, food, accommodation, tickets) with real-time balance calculations.
- **Community Ratings & Reviews**: User-submitted reviews and ratings stored with cryptographic authentication guarantees.
- **User Authentication**: Secure authentication via Firebase Auth supporting Email/Password and Google OAuth sign-in.

---

## 4. System Architecture

```mermaid
flowchart TD
    subgraph Clients["Client Tier"]
        Android["Android App (Jetpack Compose / Kotlin)"]
        Web["Web Application (Vanilla JS / Leaflet / PWA)"]
    end

    subgraph FirebaseServices["Identity & Document Sync Tier"]
        FirebaseAuth["Firebase Authentication (OAuth / JWT)"]
        Firestore["Cloud Firestore (Users, Wishlists, Expenses, Reviews)"]
    end

    subgraph BackendTier["FastAPI Backend (Vercel Serverless)"]
        Router["FastAPI Route Controller (/api/chat, /api/search, /api/health)"]
        TokenVerifier["Firebase Admin SDK Token Verifier (Cryptographic Check)"]
        RateLimiter["PostgreSQL Atomic Rate Limiter (Row Locks)"]
        QueryRouter["Query Router (Intent & Web Research Classifier)"]
        RAGPipeline["RAG Orchestrator"]
    end

    subgraph AIExternalTier["AI & Search Tier"]
        GeminiEmbed["Gemini Embedding Model (gemini-embedding-001)"]
        GeminiLLM["Gemini LLM (Grounded Context Synthesis)"]
        Tavily["Tavily Web Search API (Live Fallback / Real-time Data)"]
    end

    subgraph DataTier["Relational & Vector Data Tier"]
        PG["PostgreSQL Database"]
        PGVector["pgvector Extension (Cosine Similarity Index)"]
    end

    %% Client Interactions
    Android -- "Sign in / Token" --> FirebaseAuth
    Web -- "Sign in / Token" --> FirebaseAuth
    Android -- "Sync Profile, Wishlist, Expenses" --> Firestore
    Web -- "Sync Profile, Wishlist, Expenses" --> Firestore

    Android -- "Bearer ID Token + Query" --> Router
    Web -- "Bearer ID Token + Query" --> Router

    %% Backend Flow
    Router --> TokenVerifier
    TokenVerifier --> RateLimiter
    RateLimiter --> PG
    RateLimiter --> QueryRouter

    QueryRouter --> RAGPipeline
    RAGPipeline --> GeminiEmbed
    RAGPipeline --> PGVector
    PGVector -. "Top-K Documents" .-> RAGPipeline
    QueryRouter -- "When Web Context Needed" --> Tavily
    Tavily -. "Live Web Snippets" .-> RAGPipeline

    RAGPipeline --> GeminiLLM
    GeminiLLM -. "Structured Synthesis" .-> RAGPipeline
    RAGPipeline -- "Deterministic DB Overlay + Response" --> Router
    Router -- "JSON ChatResponse" --> Android
    Router -- "JSON ChatResponse" --> Web
```

---

## 5. Technology Stack

### Android Mobile Client
- **Language**: Kotlin 1.9
- **UI Toolkit**: Jetpack Compose with Material 3 (Compose BOM `2024.04.00`)
- **Architecture**: MVVM with unidirectional data flow
- **Dependency Injection**: Dagger Hilt `2.50`
- **Networking**: Retrofit `2.9.0` + OkHttp `4.12.0` (with logging interceptors)
- **Asynchronous Execution**: Kotlin Coroutines & Flow `1.7.3`
- **Image Pipeline**: Coil Compose `2.6.0`
- **Location Services**: Google Play Services Location `21.2.0`
- **Security & Optimization**: ProGuard / R8 code shrinking and resource optimization enabled

### Web Frontend
- **Structure & Styling**: HTML5, Vanilla CSS3 (custom CSS custom-property design tokens, glassmorphism UI, dark aesthetic)
- **Typography & Icons**: DM Serif Display, Inter (Google Fonts), FontAwesome 6.4.0
- **Mapping & GIS**: Leaflet `1.9.4` with OpenStreetMap cartography tiles
- **Client Scripts**: Modern Modular JavaScript (ES6+), native browser Geolocation API
- **PWA & Offline**: Web App Manifest (`manifest.json`), Service Worker (`sw.js`) cache-first asset strategy
- **Cloud Integration**: Firebase JavaScript SDK v10 (Authentication & Cloud Firestore)

### Backend & API
- **Runtime**: Python 3.10+
- **Web Framework**: FastAPI `0.115.0`
- **ASGI Server**: Uvicorn `0.30.6`
- **Schema Validation**: Pydantic `2.8.2` and `pydantic-settings`
- **Database Engine**: SQLAlchemy `2.0.35` (AsyncIO extension)
- **Async Driver**: `asyncpg` `0.29.0`
- **Migration & ORM Tooling**: Alembic `1.13.3`
- **Serverless Adapter**: Custom path-normalization middleware for Vercel Python runtime

### AI, Vector Search & Retrieval
- **Generative AI SDK**: Google GenAI SDK (`google-genai` `1.5.0`)
- **Large Language Model**: Google Gemini (`gemini-2.5-flash` / configurable via environment)
- **Vector Embeddings**: Google Gemini `gemini-embedding-001` (768-dimensional dense vectors)
- **Vector Search Engine**: PostgreSQL `pgvector` `0.3.2` (cosine distance indexing `<=>`)
- **External Web Grounding**: Tavily Search API (`tavily-python` `0.3.0`) for real-time web research

### Cloud & DevOps Infrastructure
- **Web Hosting**: Firebase Hosting (HTTP/2, automated SSL, global edge CDN)
- **API Deployment**: Vercel Serverless Functions (auto-scaling serverless execution)
- **Mobile Distribution**: GitHub Releases (signed release APK with SHA-256 validation)
- **Continuous Integration**: Pytest test suite (102 passing unit/integration tests)

---

## 6. AI & RAG Pipeline Architecture

The conversational AI system implements a multi-tier Retrieval-Augmented Generation pipeline engineered to prevent hallucinations and provide prompt responses:

```
[User Query]
      │
      ▼
1. Firebase Auth Verification ──► [Server-side ID Token cryptographic validation]
      │
      ▼
2. Rate Limiting Check ─────────► [PostgreSQL atomic row lock: 20 req/min per UID]
      │
      ▼
3. Greeting Fast-Path ──────────► [Regex/lexical match returns instant response, bypasses LLM]
      │
      ▼
4. Query Classification ────────► [QueryRouter determines if query requires live web research]
      │
      ├─── LOCAL RAG ROUTE ─────────────┬─── HYBRID / WEB ROUTE ──────────┐
      │                                 │                                 │
      ▼                                 ▼                                 ▼
5. Embed Query via Gemini        5. Embed Query via Gemini        5. Tavily Search API
   (gemini-embedding-001, 768d)     (gemini-embedding-001, 768d)     (Live web search query)
      │                                 │                                 │
      ▼                                 ▼                                 │
6. Vector Similarity Search      6. Vector Similarity Search              │
   (pgvector cosine distance)       (pgvector cosine distance)            │
      │                                 │                                 │
      └────────────────► 7. Context Builder ◄─────────────────────────────┘
                                [Assemble verified DB text + web citations]
                                        │
                                        ▼
                         8. Gemini LLM Synthesis
                            [System prompt with strict anti-hallucination rules]
                                        │
                                        ▼
                         9. Deterministic Metadata Overlay
                            [Re-inject verified GPS, entry fees, and place IDs]
                                        │
                                        ▼
                         10. Structured ChatResponse JSON
```

### Key Engineering Guardrails
1. **Fallback Logic**: If the local vector store yields zero relevant documents for a non-trivial prompt, the pipeline dynamically triggers a Tavily web research fallback. If Tavily times out (5-second threshold) or fails, the pipeline safely degrades to local context.
2. **Deterministic Metadata Overlay**: Even when the LLM generates recommendation text, critical transactional values—such as `entry_fee`, `latitude`, `longitude`, and `visit_duration`—are injected directly from verified PostgreSQL rows, completely bypassing generative text synthesis for facts.
3. **Server-Side Token Verification**: The client never passes user identifiers in request bodies. The backend decodes the authenticated Firebase JWT ID token server-side to extract the verified `uid`.

---

## 7. Security & Compliance

- **Cryptographic Token Verification**: All protected endpoints (`/api/chat`) strictly enforce standard Bearer authentication. Tokens are verified using Firebase Admin SDK / Google OAuth2 public certificates. Expired or revoked tokens receive HTTP `401 Unauthorized`.
- **Database-Backed Rate Limiting**: Throttling is enforced atomically in PostgreSQL using `SELECT ... FOR UPDATE` row-level locks, ensuring strict quota enforcement across concurrent serverless instances.
- **Firestore Security Rules**: Complete UID-scoped access control defined in `firestore.rules`. Users can only read and write their own profile records, private AI chat histories, wishlist entries, and expense records.
- **Zero Secret Exposure**: All sensitive credentials—including database passwords, Gemini API keys, Tavily keys, and Firebase service accounts—are injected via environment variables.
- **Git Hygiene**: Comprehensive `.gitignore` and `.vercelignore` rules ensure release keystores (`.jks`), `local.properties`, `google-services.json`, and `.env` files are excluded from source control.
- **Android Hardening**: Release builds are compiled with R8 code shrinking and obfuscation enabled (`isMinifyEnabled = true`, `isShrinkResources = true`). Specific ProGuard rules retain Retrofit coroutine signatures to prevent runtime deserialization crashes.

---

## 8. Verification & Quality Assurance

The system has undergone systematic verification across all tiers:

| Component | Test / Verification Method | Result |
|---|---|---|
| **Backend Unit & Integration Tests** | `pytest tests/` (Database, RAG, auth, routers, rate limiting) | ✅ 102/102 Passing |
| **System Health Endpoint** | `GET /api/health` against production serverless API | ✅ 200 OK (`{"status":"ok"}`) |
| **Authentication Enforcement** | `POST /api/chat` without token or with forged token | ✅ 401 Unauthorized verified |
| **Rate Limiter Throttling** | Rapid succession requests exceeding window threshold | ✅ 429 Too Many Requests |
| **Android Release Binary** | Physical hardware verification (Android 14) with signed release APK | ✅ Passed (No crashes) |
| **Android Retrofit Coroutines** | R8 generic signature preservation rules verified in release mode | ✅ AI chat operational |
| **Web Cross-Browser Testing** | Incognito verification across Chrome, Safari, and Firefox | ✅ Auth, GIS & Chat verified |

---

## 9. Deployment Architecture

```
                                  ┌─────────────────────────────┐
                                  │   GitHub Repository (main)  │
                                  └──────────────┬──────────────┘
                                                 │
                  ┌──────────────────────────────┼──────────────────────────────┐
                  │                              │                              │
                  ▼                              ▼                              ▼
     ┌────────────────────────┐    ┌───────────────────────────┐   ┌────────────────────────┐
     │   Firebase Hosting     │    │     Vercel Serverless     │   │     GitHub Releases    │
     │  (Web Frontend / PWA)  │    │   (FastAPI Backend API)   │   │  (Android Signed APK)  │
     └────────────┬───────────┘    └─────────────┬─────────────┘   └───────────┬────────────┘
                  │                              │                             │
                  ▼                              ▼                             ▼
    https://belagavi-tourism-      https://belagavi-tourism-       v1.0.0 Release Binary
    planner.web.app                yuvaraj21.vercel.app            (Production signed)
```

- **Frontend Hosting**: Deployed using `firebase deploy --only hosting`. Configured with Single-Page Application rewrites directing all traffic through `public/index.html`.
- **Backend API**: Deployed on Vercel's Python Serverless infrastructure (`api/index.py`), utilizing root-path normalization to handle edge URL routing seamlessly.
- **Android App**: Pre-compiled and signed using a dedicated release keystore. Distributed via GitHub Releases with target SDK 34 compatibility.

---

## 10. Application Interface

| Platform / View | Description | Preview |
|---|---|---|
| **Web — Home & Hero** | Welcome view with curated destination tags, quick-start auth modal, and district highlights | *(Screenshot Placeholder: Web Home)* |
| **Web — GIS Explorer** | Interactive Leaflet GIS map with category filtering, destination markers, and routing | *(Screenshot Placeholder: Web Map)* |
| **Web — AI Assistant** | Grounded conversational tourism guide with live recommendations and web sources | *(Screenshot Placeholder: Web AI Chat)* |
| **Android — Home Screen** | Native Jetpack Compose dashboard with categorized attraction cards and search | *(Screenshot Placeholder: Android Home)* |
| **Android — Place Details** | Full attraction profile with image carousel, timings, fees, and location actions | *(Screenshot Placeholder: Android Details)* |
| **Android — AI Chat Guide** | Mobile conversational interface with real-time RAG recommendations | *(Screenshot Placeholder: Android AI Chat)* |

---

## 11. Repository Structure

```
Belagavi_Tourism/
├── android-app/                       # Native Android application
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/belagavi/tourism/
│   │   │   │   ├── data/              # Repositories, models, Retrofit API interfaces
│   │   │   │   ├── di/                # Hilt dependency injection modules
│   │   │   │   ├── ui/                # Jetpack Compose UI screens, viewmodels, theme
│   │   │   │   └── SmartTourismApp.kt # Application class
│   │   │   └── res/                   # Drawables, mipmaps, XML configurations
│   │   ├── build.gradle.kts           # App Gradle build configuration (SDK 34, R8 rules)
│   │   └── proguard-rules.pro         # ProGuard rules for R8, Retrofit & Coroutines
│   ├── build.gradle.kts               # Root Gradle script
│   └── settings.gradle.kts            # Project settings & plugin repositories
│
├── backend/                           # Python FastAPI AI / RAG backend
│   ├── api/
│   │   └── index.py                   # Vercel serverless entrypoint & path normalizer
│   ├── app/
│   │   ├── api/routes.py              # API endpoints (/api/health, /api/search, /api/chat)
│   │   ├── auth/                      # Firebase token verification & PostgreSQL rate limiter
│   │   ├── config.py                  # Pydantic environment configuration
│   │   ├── db/                        # Database session manager & schema initializers
│   │   ├── models/                    # SQLAlchemy ORM models (Destinations, Rate limits)
│   │   ├── rag/                       # RAG prompt templates, LLM client, context builder
│   │   ├── schemas/                   # Pydantic request/response schemas
│   │   ├── services/                  # RAG service, search service, web research service
│   │   └── main.py                    # FastAPI application factory & CORS configuration
│   ├── requirements.txt               # Pinned Python production dependencies
│   ├── vercel.json                    # Vercel deployment configuration
│   └── Dockerfile                     # Container definition for containerized deployments
│
├── public/                            # Web application frontend (PWA)
│   ├── index.html                     # Main Single Page Application HTML
│   ├── static/
│   │   ├── ai-assistant.js            # Frontend AI assistant client & session manager
│   │   ├── main.js                    # Web application controller, map logic, auth flow
│   │   ├── data.js                    # Local destination fallback dataset
│   │   ├── reviews.js                 # Firestore review submission & rendering logic
│   │   ├── settings.js                # Profile and preference settings manager
│   │   ├── style.css                  # Custom Vanilla CSS design system & dark theme
│   │   ├── manifest.json              # Web App Manifest
│   │   ├── sw.js                      # Service Worker for offline asset caching
│   │   └── icon-192.png / icon-512.png# Application icons
│
├── tests/                             # Comprehensive test suite (102 tests)
├── firebase.json                      # Firebase Hosting configuration & SPA rewrites
├── firestore.rules                    # Cloud Firestore security rules
└── README.md                          # Project documentation
```

---

## 12. Local Development & Setup

### Prerequisites
- **Android**: Android Studio Hedgehog (or newer), JDK 17, Android SDK API 34
- **Backend**: Python 3.10+, PostgreSQL with `pgvector` extension
- **Web**: Node.js (for local HTTP serving) or Python `http.server`

---

### Backend API Setup

1. **Navigate to the backend directory and configure the virtual environment**:
   ```bash
   cd backend
   python -m venv venv
   source venv/bin/activate       # On Windows: .\venv\Scripts\activate
   pip install -r requirements.txt
   ```

2. **Configure environment variables**:
   Create a `.env` file in the `backend/` directory:
   ```env
   # Application Environment
   ENVIRONMENT=development
   LOG_LEVEL=INFO
   CORS_ORIGINS=["http://localhost:5000","http://127.0.0.1:5000","https://belagavi-tourism-planner.web.app"]

   # Database Configuration (PostgreSQL + pgvector)
   POSTGRES_HOST=localhost
   POSTGRES_PORT=5432
   POSTGRES_DB=belagavi_tourism
   POSTGRES_USER=postgres
   POSTGRES_PASSWORD=your_postgres_password
   DATABASE_URL=postgresql+asyncpg://postgres:your_postgres_password@localhost:5432/belagavi_tourism

   # AI & Search Providers
   GEMINI_API_KEY=your_gemini_api_key
   GEMINI_LLM_MODEL=gemini-2.5-flash
   GEMINI_EMBEDDING_MODEL=gemini-embedding-001
   TAVILY_API_KEY=your_tavily_api_key
   WEB_RESEARCH_ENABLED=true

   # Firebase Security & Token Verification
   FIREBASE_PROJECT_ID=belagavi-tourism-planner
   # Either set the path to service account JSON or supply raw JSON string:
   # GOOGLE_APPLICATION_CREDENTIALS=/path/to/serviceAccountKey.json
   # FIREBASE_SERVICE_ACCOUNT_JSON={"type":"service_account",...}

   # Rate Limiting
   RATE_LIMIT_ENABLED=true
   RATE_LIMIT_PER_MINUTE=20
   RATE_LIMIT_WINDOW_SECONDS=60
   ```

3. **Run database migrations and start the server**:
   ```bash
   uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
   ```
   The interactive Swagger documentation will be available at `http://localhost:8000/docs`.

4. **Execute the test suite**:
   ```bash
   pytest tests/ -v
   ```

---

### Web Frontend Setup

1. **Serve the `public/` directory locally**:
   ```bash
   # From the project root:
   npx http-server public -p 5000
   # Or using Python:
   python -m http.server 5000 -d public
   ```
2. Open `http://localhost:5000` in your web browser.

---

### Android Application Setup

1. **Open the project**:
   Launch Android Studio and open the `android-app` directory.
2. **SDK Configuration**:
   Ensure `local.properties` specifies your Android SDK directory:
   ```properties
   # Windows example: C:/Users/<Username>/AppData/Local/Android/Sdk
   # macOS/Linux example: /Users/<Username>/Library/Android/sdk
   sdk.dir=/path/to/your/Android/Sdk
   ```
3. **Firebase Configuration**:
   Place your verified `google-services.json` file inside `android-app/app/`.
4. **Build & Run**:
   Compile and run the debug build on an Android emulator or connected physical device:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 13. Production Endpoints

| Service | Access URL | Method / Role |
|---|---|---|
| **Web Frontend** | `https://belagavi-tourism-planner.web.app` | Static / Single Page App |
| **API Root** | `https://belagavi-tourism-yuvaraj21.vercel.app/` | Service Status |
| **System Health** | `https://belagavi-tourism-yuvaraj21.vercel.app/api/health` | `GET` — Health Check |
| **Semantic Search** | `https://belagavi-tourism-yuvaraj21.vercel.app/api/search` | `POST` — Vector Similarity Search |
| **AI Tourism Assistant** | `https://belagavi-tourism-yuvaraj21.vercel.app/api/chat` | `POST` — Authenticated RAG Chat |
| **Android Release** | `https://github.com/Yuvaraj-ui132/Belagavi_Tourism/releases/tag/v1.0.0` | APK Binary Download |

---

## 14. Roadmap & Future Scope

- **Offline Vector Embeddings**: On-device vector retrieval for essential destination queries in areas with limited cellular connectivity across the Western Ghats.
- **Multilingual Audio Guides**: Automated text-to-speech audio narratives in Kannada, Marathi, Hindi, and English.
- **Dynamic Multi-Day Itinerary Optimization**: Constraint-based itinerary generator accounting for transit times, road conditions, and operating hours.
- **Expanded Regional Karnataka Coverage**: Extending destination datasets to cover neighboring districts including Dharwad, Uttara Kannada, and Bagalkot.

---

## 15. Author

**Yuvaraj Murkunde**
- **GitHub**: [@Yuvaraj-ui132](https://github.com/Yuvaraj-ui132)
- **Repository**: [Belagavi_Tourism](https://github.com/Yuvaraj-ui132/Belagavi_Tourism)
- **Web App**: [belagavi-tourism-planner.web.app](https://belagavi-tourism-planner.web.app)

---

## 16. License

This project is licensed under the [MIT License](LICENSE).
