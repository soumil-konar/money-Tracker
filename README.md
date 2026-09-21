# Money Tracker

An enterprise-grade, SMS-first automated personal finance management application engineered for Android 15. Designed with a dual-engine architecture combining Google Tensor G4 hardware acceleration on Google Pixel 9 devices with Google Gemini cloud models, 3-tier Retrieval-Augmented Generation (RAG), and a curated fintech design system.

---

## Architecture Overview

```
                          [ Incoming Banking / UPI SMS ]
                                        │
                                        ▼
                        [ SmsReceiver (goAsync + Watchdog) ]
                                        │
                                        ▼
                                [ SmsParser ]
                   (Rule-based Regex + Context Extraction)
                                        │
                                        ▼
                             [ OnDeviceAiEngine ]
               (Tensor G4 TPU Accelerated Parsing & Categorization)
                                        │
                                        ▼
                            [ FinanceRepository ]
                                        │
             ┌──────────────────────────┴──────────────────────────┐
             ▼                                                     ▼
    [ Room Database v6 ]                               [ Embedding Engine ]
  • Transactions Table                               • Gemini Embedding-2
  • FTS4 Virtual Table (Automated Triggers)           • 256-dim Vector Store
  • Accounts, Budgets, Subscriptions
             │                                                     │
             └──────────────────────────┬──────────────────────────┘
                                        │
                                        ▼
                              [ FinanceRagEngine ]
              (Tier 1: Exact SQL | Tier 2: FTS4 | Tier 3: Vector)
                                        │
                                        ▼
                             [ AI Assistant Sheet ]
          (Auto Pixel-First / Cloud Gemini Flash-Lite / On-Device)
```

---

## Key Features

### 1. Automated SMS Banking & UPI Ingestion
- Real-time ingestion of transactional SMS across major Indian banking and financial institutions (HDFC, SBI, ICICI, Axis, Kotak, PNB, Bank of Baroda, etc.) and UPI services (Google Pay, PhonePe, Paytm, CRED).
- Extracts transaction amount, type (Debit/Credit), merchant/beneficiary, account mask, timestamp, and reference numbers.
- Automated duplicate protection using transaction hashes and duplicate time windows.
- Background ingestion protected by `goAsync()` with an 8-second watchdog timer to guarantee zero Application Not Responding (ANR) events on the main thread.

### 2. Google Pixel 9 On-Device AI Engine
- **Tensor G4 TPU Acceleration:** Automatically detects Google Pixel 9 hardware capabilities (`Build.HARDWARE` / `SOC_MODEL` tensor detection) to run high-throughput on-device inference.
- **Offline Parsing & Categorization:** Heuristic extraction engine capable of resolving merchants, categories, and payment channels with zero network latency.
- **100% Air-Gapped Privacy:** In `ON_DEVICE_ONLY` mode, SMS data, transactions, and spending analytics never leave the physical device.

### 3. Hybrid Cloud RAG Engine
- Powered by Google AI Studio's `gemini-3.5-flash-lite` and `gemini-embedding-2`.
- Strict rate-limiting and quota optimization designed for high token efficiency.
- Exponential backoff retry handler with graceful fallback to local on-device heuristics in the event of network failures or HTTP 429 quota exhaustion.

### 4. 3-Tier Multi-Modal Search & Retrieval
1. **Tier 1 - Deterministic SQL:** Filter transactions with sub-millisecond execution by date ranges, categories, amount bounds, and account masks.
2. **Tier 2 - SQLite FTS4 Full-Text Search:** Full-text indexed transaction searches powered by automated Room database triggers on `INSERT`, `UPDATE`, and `DELETE`.
3. **Tier 3 - Vector Cosine Similarity Search:** Semantic search using 256-dimensional embeddings for complex natural language queries (e.g., *"weekend coffee runs in Indiranagar"*).

### 5. Interactive Floating AI Assistant
- Floating action entry point launching an interactive slide-up bottom sheet (`SpendingAssistantSheet`).
- Features pre-configured quick prompts (*Pacing vs budget*, *Dining out*, *Spends with places*, *Saving advice*, *Card debits*).
- Real-time streaming conversational responses with contextual spending breakdowns.

### 6. Design System & Aesthetics
- Clean, distraction-free fintech visual architecture with zero emojis for an enterprise-ready experience.
- Harmonic color palette: warm sand, deep obsidian, muted terracotta, mint accents, and warm gold highlights.
- Jetpack Compose with `derivedStateOf` to prevent unnecessary recompositions and maintain a smooth 120Hz frame rate.

---

## Technology Stack

| Layer | Technology |
|---|---|
| **Language** | Kotlin 2.0.21 |
| **UI Framework** | Jetpack Compose (Material 3) |
| **Local Database** | Room 2.6.1 with SQLite FTS4 & Automated Triggers |
| **Asynchronous** | Kotlin Coroutines & StateFlow / SharedFlow |
| **Background Tasks** | WorkManager 2.10.0 |
| **Networking** | OkHttp 4.12.0 with Server-Sent Events (SSE) streaming |
| **Serialization** | Kotlinx Serialization JSON 1.7.3 |
| **Target Platforms** | Android 8.0 (API 26) up to Android 15 (API 35) |
| **Hardware Target** | Optimized for Google Pixel 9 (Tensor G4) |

---

## Getting Started

### Prerequisites

1. **Java Development Kit (JDK):** JDK 21 (e.g., OpenJDK 21 or Azul Zulu 21).
2. **Android SDK:** Android 15 SDK (API Level 35) with Build-Tools `35.0.0`.
3. **Android Studio:** Android Studio Ladybug (2024.2.1) or newer.
4. **Google AI Studio API Key (Optional):** Required only if enabling Cloud Gemini RAG features.

---

### Environment Setup

1. **Clone the repository:**
   ```bash
   git clone https://github.com/soumil-konar/money-Tracker.git
   cd money-Tracker
   ```

2. **Configure SDK Location:**
   Create a `local.properties` file in the project root:
   ```properties
   sdk.dir=/path/to/your/android/sdk
   ```
   *(On Linux: typically `/home/<user>/Android/Sdk`; on macOS: `/Users/<user>/Library/Android/sdk`)*

3. **Configure JDK 21 in `gradle.properties`:**
   ```properties
   org.gradle.java.home=/path/to/jdk-21
   ```

---

### Build & Verification Commands

All build scripts are managed through the Gradle wrapper:

- **Run all unit tests:**
  ```bash
  ./gradlew testDebugUnitTest
  ```
  *(Verifies SMS parser rules, on-device AI classification, and multi-tier RAG logic).*

- **Assemble Debug APK:**
  ```bash
  ./gradlew assembleDebug
  ```
  The generated APK will be located at:
  ```
  app/build/outputs/apk/debug/app-debug.apk
  ```

- **Install on a connected device / emulator:**
  ```bash
  ./gradlew installDebug
  ```
  Or using Android Debug Bridge directly:
  ```bash
  adb install -r app/build/outputs/apk/debug/app-debug.apk
  ```

---

## Configuration & AI Modes

The application provides three configurable AI operating modes under **Settings > AI Engine Mode**:

1. **Auto (Pixel-First) [Recommended]:**
   - Detects Google Tensor G4 hardware on Pixel 9 devices.
   - Executes transactional parsing and categorization locally on-device.
   - Employs Cloud Gemini for complex spending synthesis when an API key is present.
2. **On-Device Only (Air-Gapped):**
   - 100% offline. Zero network transmissions.
   - All categorization, queries, and assistant interactions are handled via on-device heuristics.
3. **Cloud Only:**
   - Routes RAG semantic queries and assistant synthesis through Google AI Studio Gemini API (`gemini-3.5-flash-lite`).

### Setting the Gemini API Key
- Navigate to the **Settings** screen in the application.
- Enter your Google AI Studio API key under **Gemini API Configuration**.
- Tap **Save**. The key is stored locally in encrypted application preferences and never logged.

---

## Project Structure

```
app/src/main/java/com/soumil/moneytracker/
├── ai/
│   ├── FinanceRagEngine.kt       # 3-tier hybrid RAG retrieval pipeline
│   ├── GeminiApiClient.kt        # Cloud Gemini API client with quota backoff
│   └── OnDeviceAiEngine.kt       # Tensor G4 TPU detector & offline heuristics
├── data/
│   ├── db/
│   │   ├── FinanceDao.kt         # Room DAOs including FTS4 full-text search
│   │   ├── FinanceDatabase.kt    # Database definition & schema migrations (v1-v6)
│   │   └── FinanceEntities.kt    # Entity definitions & FTS virtual tables
│   ├── local/
│   │   └── AiPreferences.kt      # Encrypted storage for AI configuration
│   ├── model/
│   │   └── FinanceModels.kt      # Domain models, enums, and data classes
│   └── repo/
│       └── FinanceRepository.kt  # Unified repository managing DB & AI operations
├── parser/
│   └── SmsParser.kt              # Regex rules and entity extraction for SMS
├── sms/
│   └── SmsReceiver.kt            # Broadcast receiver with goAsync ANR protection
├── ui/
│   ├── components/               # Custom UI components & cards
│   ├── navigation/               # Navigation graph & bottom bar setup
│   ├── screen/                   # Compose screens (Home, Transactions, Assistant, Settings)
│   ├── theme/                    # Fintech color scheme & typography tokens
│   └── MainViewModel.kt          # Primary ViewModel managing UI state
└── worker/
    └── SubscriptionWorker.kt     # Periodic WorkManager recurring payment detector
```

---

## Database Migrations

The database is built on Room with automated schema management:
- **Migration 5 to 6:** Introduces SQLite FTS4 virtual table `transactions_fts` with automatic SQLite triggers:
  - `transactions_ai`: Automatically populates FTS index after `INSERT` on `transactions`.
  - `transactions_ad`: Automatically removes index entries after `DELETE` on `transactions`.
  - `transactions_au`: Automatically updates index entries after `UPDATE` on `transactions`.
  - Introduces `transaction_embeddings` table for 256-dimensional vector persistence.

---

## Permissions & Privacy

This application is built with a **privacy-first** design philosophy:
- `android.permission.RECEIVE_SMS`: Required to intercept transaction SMS messages from financial institutions in real time.
- `android.permission.READ_SMS`: Used strictly during initial sync to ingest past financial records.
- **Privacy Assurance:** SMS contents are parsed locally. Transactional details are stored strictly in the local SQLite/Room database. In on-device mode, no data ever leaves the device.

---

## License

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
