# Money Tracker

An enterprise-grade, privacy-first automated personal finance management platform engineered for Android 15. Built with a dual-engine architecture combining Google Tensor G4 hardware acceleration on Pixel 9 devices, 100% on-device direct TLS Google Email sync, automated SMS bank alert ingestion, real-time dynamic bank balance tracking, 3-tier Retrieval-Augmented Generation (RAG), and a modern Expressive Material 3 fintech design system.

---

## Architecture Overview

```
 [ Incoming Banking / UPI SMS ]              [ Google Email Alerts (Gmail) ]
 (HDFC, SBI, ICICI, Axis, UPI)             (Direct TLS IMAP: imap.gmail.com:993)
               │                                              │
               ▼                                              ▼
 [ SmsReceiver (goAsync + Watchdog) ]              [ EmailSyncManager ]
               │                                 (100% On-Device Socket Fetch)
               └──────────────────────┬───────────────────────┘
                                      │
                                      ▼
                               [ SmsParser ]
          • Context & regex extraction
          • Credit card bill transfer classification (no budget inflation)
          • Utility bill payment detection (executed vs due notices)
          • Authoritative balance extraction ("Avl bal", "Bal: INR")
                                      │
                                      ▼
                           [ OnDeviceAiEngine ]
          (Tensor G4 TPU Accelerated Parsing, Offline Embeddings & Heuristics)
                                      │
                                      ▼
                          [ FinanceRepository ]
                                      │
       ┌──────────────────────────────┴──────────────────────────────┐
       ▼                                                             ▼
[ Room Database v7 ]                                     [ Vector Embedding Store ]
• Accounts (currentBalance, balanceUpdatedAtMillis)       • Gemini Embedding-2
• Transactions (availableBalance, budgetInclusion)        • 256-dim Vector Store
• FTS4 Virtual Table (Automated Triggers)                 • Local Cosine Similarity
• Budgets & Recurring Subscriptions
       │                                                             │
       └──────────────────────────────┬──────────────────────────────┘
                                      │
                                      ▼
                            [ FinanceRagEngine ]
            (Tier 1: Exact SQL | Tier 2: FTS4 | Tier 3: Vector)
                                      │
                                      ▼
                        [ Interactive UI & Assistants ]
        • Accounts & Balances Carousel (Live Bank Balance Cards)
        • SpendingAssistantSheet (Auto Pixel-First / On-Device / Cloud)
        • Biometric App Lock & Custom Tactile Haptic Actuators
```

---

## Key Features

### 1. Dual-Source On-Device Ingestion Pipeline (SMS + Gmail)
- **Real-Time SMS Ingestion:** Intercepts incoming transactional SMS across Indian banking institutions (HDFC, SBI, ICICI, Axis, Kotak, PNB, Bank of Baroda, IndusInd) and payment apps (Google Pay, PhonePe, Paytm, CRED). Protected with `goAsync()` and an 8-second watchdog timer to eliminate ANRs.
- **100% On-Device Google Email (Gmail) IMAP Fetch:** Built-in direct TLS socket client (`imap.gmail.com:993`) that securely logs in using a Google App Password directly on your device. Fetches alerts from monitored bank domains (`*@hdfcbank.net`, `*@icicibank.com`, `*@sbi.co.in`, `*@axisbank.com`, `*@kotak.com`, `*@cred.club`) with zero intermediary servers or third-party email parsers.
- **Single Ingestion Pipeline:** Both SMS and Email alerts pass through identical deduplication, entity extraction, categorization, and account balance adjustment routines.

### 2. Ground-Up Transaction Intelligence & Parsing
- **Credit Card Bill Payments Treated as Transfers:** Fixed the critical flaw where credit card repayment receipts (e.g., *"Payment received towards your credit card"*, *"paid via CRED"*) were misclassified as income (`CREDIT`) or double-counted as expenses. They are strictly classified as `TransactionCategory.TRANSFER`, flagged with `isCardBillPayment = true`, and given `countsTowardBudget = false`.
- **Utility Bill Payments Unblocked:** Cleanly distinguishes executed bill payments (electricity, water, broadband debits) from unpaid due notices (*"bill is due on 25-05-2026"*). Executed bills are recorded as `DEBIT` under `TransactionCategory.BILLS`.
- **Authoritative Balance Extraction:** Intelligently extracts live available balances (`Avl bal Rs.`, `Avail Bal`, `Bal: INR`) embedded inside transaction notifications.

### 3. Live Bank Accounts & Balance Tracking (Room v7)
- **Authoritative & Delta Arithmetic:** When an alert includes an authoritative balance, the bank account balance is updated immediately. For transactions without explicit balances, the account balance is dynamically incremented or decremented via delta arithmetic (`+amount` for CREDIT, `-amount` for DEBIT).
- **Accounts & Balances Carousel:** Horizontal carousel on the Home Screen displaying each linked bank and card with masked account digits, institution branding, live formatted balance (e.g., `₹40,000`), and last updated timestamps.
- **Tracked Balance Calculation:** Dynamically computes total available funds across all active bank accounts.

### 4. Google Pixel 9 On-Device AI Engine & Cloud Augmentation
- **Tensor G4 TPU Acceleration:** Automatically detects Google Pixel hardware (`Build.HARDWARE` / `SOC_MODEL` tensor detection) to execute sub-millisecond on-device transaction classification.
- **Three Configurable AI Modes:**
  1. **Auto (Pixel-First) [Recommended]:** Runs transactional parsing locally on-device and leverages Gemini Flash Lite only for deep semantic conversational synthesis when an API key is configured.
  2. **On-Device Only (Air-Gapped):** 100% offline. Zero network calls. All analytics and assistant queries run via local heuristics.
  3. **Cloud Only:** Routes complex queries and synthesis via Google AI Studio's `gemini-3.5-flash-lite`.

### 5. Biometric App Lock & Device Security
- **BiometricPrompt Integration:** Protects sensitive financial ledgers using fingerprint, face unlock, or device PIN/password (`BIOMETRIC_STRONG` with `DEVICE_CREDENTIAL` fallback).
- **Lifecycle Auto-Lock:** Automatically locks the application when placed in the background (`ON_STOP`) and prompts for authentication upon returning (`ON_RESUME`).
- **Expressive AppLockScreen:** Full-screen Material 3 lock screen overlay with an explicit unlock trigger.

### 6. Tactile & Haptic System
- **Device-Calibrated Actuator Feedback:** Custom vibration effects scaled to your device's linear resonant actuator (`HapticFeedbackManager`).
- **Configurable Intensities:** Choose between `SUBTLE`, `BALANCED`, and `STRONG` vibration profiles, with tactile clicks on buttons, tabs, ledger approvals, and dialog actions.

### 7. Modern Fintech UI & Adaptive App Icon
- **Expressive Fintech Theme:** Dark obsidian surface (`#0C0C12`) with glowing Ember Flame (`#FF5E2B`), Emerald Mint (`#10B981`), Electric Blue (`#38BDF8`), and Champagne Gold accents.
- **Adaptive Launcher Icon:** 3D metallic Rupee symbol (**₹**) with financial growth arc and ambient glow across all mipmap densities (`mdpi` through `xxxhdpi`), with circular launcher masks and Android 13+ Material You themed monochrome icon support.

---

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| **Language** | Kotlin | 2.0.21 |
| **UI Framework** | Jetpack Compose (Material 3) | BOM 2024.10.01 |
| **Local Database** | Room with SQLite FTS4 & Triggers | 2.6.1 |
| **Security & Auth** | AndroidX Biometric | 1.2.0-alpha05 |
| **Concurrency** | Kotlin Coroutines & StateFlow | 1.8.1 |
| **Background Processing** | WorkManager | 2.9.1 |
| **TLS & Protocols** | Native Java/Kotlin SSLSocket (IMAP TLS) | TLSv1.2 / TLSv1.3 |
| **Target SDK** | Android 15 (API 35) | Min SDK 26 (Android 8.0) |
| **Hardware Target** | Google Tensor G4 (Pixel 9) + Fallback Heuristics | |

---

## Project Structure

```
app/src/main/java/com/soumil/moneytracker/
├── MoneyTrackerApp.kt           # Application class & container initialization
├── AppContainer.kt              # Dependency container with on-demand lazy singletons
├── MainActivity.kt              # FragmentActivity hosting Jetpack Compose & BiometricPrompt
├── ai/
│   ├── FinanceRagEngine.kt      # 3-tier hybrid RAG retrieval pipeline
│   ├── GeminiApiClient.kt       # Google AI Studio Gemini API client with quota backoff
│   └── OnDeviceAiEngine.kt      # Tensor G4 TPU detector & offline heuristics
├── data/
│   ├── db/
│   │   ├── FinanceDao.kt        # Room DAOs (AccountDao, TransactionDao, FTS4)
│   │   ├── FinanceDatabase.kt   # Room database & schema migrations (v1 through v7)
│   │   └── FinanceEntities.kt   # Entity definitions, FTS virtual tables & indices
│   ├── local/
│   │   ├── AiPreferences.kt     # Encrypted preferences for AI engine and API key
│   │   ├── EmailPreferences.kt  # On-device storage for Gmail credentials and sync state
│   │   ├── HapticPreferences.kt # Tactile vibration toggle and intensity settings
│   │   ├── SecurityPreferences.kt # Biometric app lock toggle
│   │   └── SetupPreferences.kt  # Onboarding & initial setup tracking
│   ├── model/
│   │   └── FinanceModels.kt     # Domain models, parsed transactions, and UI states
│   └── repo/
│       └── FinanceRepository.kt # Central repository unifying SMS, Email, DB & AI
├── email/
│   └── EmailSyncManager.kt      # Direct TLS socket IMAP client for imap.gmail.com:993
├── parser/
│   └── SmsParser.kt             # Regex rules, available balance & transfer classification
├── sms/
│   ├── SmsReceiver.kt           # Broadcast receiver with goAsync ANR protection
│   └── SmsImportManager.kt      # Historical SMS batch import scanner
├── ui/
│   ├── components/              # Expressive UI cards, charts, and dialogs
│   ├── haptics/                 # HapticFeedbackManager & Compose CompositionLocal
│   ├── navigation/              # Navigation host, bottom bar & route destinations
│   ├── screen/                  # Home, Transactions, BudgetHistory, More, Settings, Assistant
│   ├── security/                # BiometricAuthHelper & AppLockScreen
│   ├── theme/                   # Fintech color palette & typography tokens
│   └── MainViewModel.kt         # Primary ViewModel coordinating UI states and flows
└── worker/
    └── DailyInsightsWorker.kt   # WorkManager periodic recurring transaction detector
```

---

## Database Schema & Migrations

The database is powered by Room with automated schema versioning:
- **Migration 5 to 6:**
  - Adds SQLite FTS4 virtual table `transactions_fts` with automated database triggers (`transactions_ai`, `transactions_ad`, `transactions_au`).
  - Adds `transaction_embeddings` table for 256-dimensional semantic vector persistence.
- **Migration 6 to 7:**
  - Updates `accounts` table: adds `currentBalance REAL NOT NULL DEFAULT 0.0` and `balanceUpdatedAtMillis INTEGER`.
  - Updates `transactions` table: adds `availableBalance REAL`.

---

## Getting Started

### Prerequisites
1. **Java Development Kit (JDK):** JDK 17 or JDK 21.
2. **Android SDK:** Android 15 SDK (API Level 35) with Build-Tools `35.0.0`.
3. **Android Studio:** Android Studio Ladybug (2024.2.1) or newer.

### Build & Run
1. **Clone the repository:**
   ```bash
   git clone https://github.com/soumil-konar/money-Tracker.git
   cd money-Tracker
   ```
2. **Build and run unit tests:**
   ```bash
   ./gradlew testDebugUnitTest
   ```
3. **Assemble Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   The output APK will be located at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Setting Up On-Device Google Email (Gmail) Sync

1. Open your Google Account at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords).
2. Generate an **App Password** for Money Tracker (16 characters, e.g. `abcd efgh ijkl mnop`).
3. In Money Tracker, navigate to **Settings > Google Email Alerts (100% On-Device)**.
4. Toggle **Enable Email Sync**, enter your Gmail address and the 16-character App Password, and tap **Save Credentials**.
5. Tap **Sync Recent Bank Emails**. The app connects directly to Google's IMAP server via encrypted TLS port 993, extracts bank transaction emails, and updates your balances instantly.

---

## Permissions & Privacy Policy

Money Tracker is designed to ensure that **no confidential financial data leaves your phone**:
- `android.permission.RECEIVE_SMS`: Intercepts transaction alerts from your financial institutions in real time.
- `android.permission.READ_SMS`: Performs historical sync of transactions upon explicit user request.
- `android.permission.INTERNET`: Used strictly for direct TLS email sync with `imap.gmail.com` and optional Gemini API queries when enabled by the user.
- `android.permission.USE_BIOMETRIC`: Authenticates the user via device biometrics before granting access to financial ledgers.
- `android.permission.VIBRATE`: Provides tactile haptic feedback on linear resonant actuators.

---

## License

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
