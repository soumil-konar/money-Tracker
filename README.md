# Money Tracker

An enterprise-grade, privacy-first automated personal finance management platform engineered for Android 15. Built with a multi-channel ingestion architecture combining Google Tensor G4 hardware acceleration on Pixel 9 devices, real-time Android Notification Access, 100% on-device direct TLS Google Email sync, automated SMS bank alert ingestion, dynamic exclusion filtering, intelligent transaction description synthesis, real-time bank balance reconciliation with balance proof verification, manual balance true-ups, automated transfer-pair detection, AES-256-GCM encrypted database backups, an interactive Android home screen balance app widget, 3-tier Retrieval-Augmented Generation (RAG), and a modern Expressive Material 3 fintech design system.

---

## Architecture Overview

```
 [ Incoming Banking / UPI SMS ]     [ Status-Bar Notification Alerts ]      [ Google Email Alerts (Gmail) ]
 (HDFC, SBI, ICICI, Axis, UPI)      (Gmail, GPay, PhonePe, Paytm, CRED)    (Direct TLS IMAP: imap.gmail.com:993)
               │                                   │                                         │
               ▼                                   ▼                                         ▼
 [ SmsReceiver (goAsync) ]      [ TransactionNotificationListenerService ]          [ EmailSyncManager ]
               │                        (Real-Time Android Event Stream)          (100% On-Device Socket Fetch)
               └───────────────────────────────────┬─────────────────────────────────────────┘
                                                   │
                                                   ▼
                                     [ Dynamic Exclusion Engine ]
                                  (ExclusionPreferences - Zero Hardcoding)
                                  • Word-boundary checks (\bOTP\b)
                                  • Custom user keywords (Steam, Epic, Refund)
                                                   │
                                                   ▼
                                       [ Dual-Engine Parsing ]
                    ┌──────────────────────────────┴──────────────────────────────┐
                    ▼                                                             ▼
             [ SmsParser ]                                              [ OnDeviceAiEngine ]
       • Context & regex extraction                                (Tensor G4 TPU Accelerated Parsing)
       • Real debit/credit verification                            • Promotional offer & spam rejection
       • Credit card bill transfer logic                           • Detailed description synthesis
       • Authoritative balance ("Avl bal")                         • Unicode Rupee (₹/INR) regexes
                    │                                                             │
                    └──────────────────────────────┬──────────────────────────────┘
                                                   │
                                                   ▼
                                       [ Gemini Intelligence ]
                                      (gemini-3.5-flash-lite)
                               • Strict financial classification prompt
                               • Rejection of marketing / discount offers
                               • Rich contextual note generation
                                                   │
                                                   ▼
                                         [ FinanceRepository ]
                                                   │
        ┌──────────────────────────────────────────┴──────────────────────────────────────────┐
        ▼                                                                                     ▼
 [ Room Database v8 ]                                                             [ Vector Embedding Store ]
 • Accounts (balance, proof snippets, verified status)                            • Gemini Embedding-2
 • Transactions (availableBalance, budgetInclusion, note)                         • 256-dim Vector Store
 • FTS4 Virtual Table (Automated Triggers)                                         • Local Cosine Similarity
 • Budgets, Recurring Subscriptions & Transfer Pairs
        │                                                                                     │
        ├──────────────────────────────────────────┬──────────────────────────────────────────┘
        ▼                                          ▼
 [ BackupManager ]                         [ FinanceRagEngine ]
 (AES-256-GCM / PBKDF2 / .mtbackup)       (Tier 1: Exact SQL | Tier 2: FTS4 | Tier 3: Vector)
        │                                          │
        └──────────────────┬───────────────────────┘
                           ▼
             [ Interactive UI & Assistants ]
     • Home Screen Balance App Widget (4x2 Live Balance & Quick Add)
     • Live Bank Balance Carousel & Balance Proof Verification
     • Manual Balance "True-Up" / Baseline Reconciliation
     • Multi-Account Internal Transfer Pair Reconciliation
     • Dynamic Exclusion Filters & Password Visibility Controls
     • SpendingAssistantSheet (Auto Pixel-First / On-Device / Cloud)
     • Biometric App Lock & Custom Tactile Haptic Actuators
     • Categorized Settings with Live Service Status Strip
```

---

## Key Features

### 1. Tri-Channel On-Device Ingestion Pipeline (Notifications + SMS + Gmail)
- **Real-Time Notification Access (`NotificationListenerService`):** Captures incoming status bar notifications from Gmail (`com.google.android.gm`), UPI apps (Google Pay, PhonePe, Paytm, CRED, BHIM, Navi, Amazon Pay), and banking apps. Operates with **zero passwords**, zero OAuth friction, and zero background polling.
- **Real-Time SMS Ingestion:** Intercepts incoming transactional SMS across Indian financial institutions (HDFC, SBI, ICICI, Axis, Kotak, PNB, Bank of Baroda, IndusInd) and payment gateways. Protected with `goAsync()` and an 8-second watchdog timer to eliminate ANRs.
- **100% On-Device Direct TLS Gmail IMAP:** Native socket client (`imap.gmail.com:993`) that connects directly to Google via TLS, supporting 16-letter Google App Passwords with automated Unicode whitespace and delimiter normalization.
- **Unified Pipeline:** All three channels pass through identical deduplication, exclusion matching, entity extraction, categorization, and account balance adjustment routines.

### 2. Live Bank Account Reconciliation & Balance Proof Verification
- **Fintech Ledger Reconciliation:** Automatically separates genuine financial institutions (HDFC, ICICI, SBI, Axis) from payment rails (UPI) and merchants.
- **Authoritative Balance Proofs:** Extracts verbatim SMS proof snippets (e.g., *"Avl bal Rs 45,200.00"*) and timestamped evidence for full auditability.
- **Running Delta Computation:** Calculates accurate balances across transaction histories relative to verified anchor snapshots.

### 3. Manual Balance "True-Up" / Baseline Reconciliation Action
- **User-Anchored Precedence:** Allows users to manually calibrate/true-up an account's baseline balance at any time (e.g. after ATM cash withdrawals, offline interest credits, or physical passbook checks).
- **Forward Running Deltas:** The true-up anchor takes precedence over older bank SMS snapshots, maintaining mathematical accuracy by automatically computing forward deltas for all subsequent transactions.
- **Audit Trails:** Logs true-up timestamps and optional user verification notes directly into the account verification metadata.

### 4. Multi-Account Internal Transfer Pair Detection
- **Self-Transfer Recognition:** Automatically identifies and links matching debit and credit transaction pairs when transferring funds between personal accounts (e.g., HDFC Savings → SBI Account).
- **Temporal & Reference Matching:** Correlates simultaneous transactions occurring within a 5-minute temporal window sharing UPI, RRN, or UTR reference numbers.
- **Zero Budget Skew:** Categorizes detected transfer pairs as `TRANSFER` and marks `countsTowardBudget = false`, preventing internal transfers from artificially inflating monthly expense figures.

### 5. Encrypted Database Backup & Restore (`.mtbackup`)
- **Military-Grade Encryption:** Utilizes **AES-256-GCM** authenticated encryption paired with **PBKDF2WithHmacSHA256** key derivation (10,000 iterations, 16-byte cryptographic salt, 12-byte initialization vector, and 128-bit authentication tag).
- **Storage Access Framework (SAF):** Exports and imports backups to Google Drive, SD cards, or local Downloads without requiring legacy storage permissions.
- **Relational Integrity:** Restores Accounts, Transactions, Budgets, and Subscriptions while automatically remapping foreign key account IDs (`oldAccountId -> newAccountId`).

### 6. Interactive Home Screen Account Balance App Widget
- **At-a-Glance Net Worth:** 4x2 Android home screen widget displaying total tracked liquid balance in Indian Rupees (`₹`).
- **Top Accounts Breakdown:** Shows up to 3 primary bank accounts with masked last-4 digits (e.g., `HDFC •••• 4128`) and individual balances.
- **Interactive Controls:**
  - **Instant Refresh (⟳):** Broadcasts a live balance recalculation request, displaying an updated timestamp (`"Updated 01:45 PM • 3 accounts"`).
  - **Quick Add (+):** Launches directly into the quick transaction logger dialog in the app.
  - **Auto-Sync:** Updates automatically whenever transactions are ingested or balances are reconciled.

### 7. Decluttered & Categorized Settings Screen
- **Clean Category Tabs:** Organizes settings into 4 intuitive categories: **Ingestion**, **Intelligence**, **Personalization**, and **Security & System**.
- **Live Service Status Strip:** Glanceable header showing real-time operational status (Notification Listener Active/Inactive, SMS Ingestion Ready, Last Email Sync Timestamp).
- **Dynamic Exclusion Engine:** Configure custom keywords (e.g., `Steam`, `Epic Games`, `OTP`, `Refund`) to automatically suppress unwanted transactions.
- **Password Visibility Toggles:** Eye icon toggle for seamless input verification when configuring Gmail App Passwords.

### 8. Google Pixel 9 On-Device AI Engine & Cloud Augmentation
- **Tensor G4 TPU Acceleration:** Automatically detects Google Pixel hardware (`Build.HARDWARE` / `SOC_MODEL` tensor detection) to execute sub-millisecond on-device transaction classification.
- **Three Configurable AI Modes:**
  1. **Auto (Pixel-First) [Recommended]:** Runs transactional parsing locally on-device and leverages Gemini Flash Lite only for deep semantic conversational synthesis when an API key is configured.
  2. **On-Device Only (Air-Gapped):** 100% offline. Zero network calls. All analytics and assistant queries run via local heuristics.
  3. **Cloud Only:** Routes complex queries and synthesis via Google AI Studio's `gemini-3.5-flash-lite`.

### 9. Biometric App Lock & Device Security
- **BiometricPrompt Integration:** Protects sensitive financial ledgers using fingerprint, face unlock, or device PIN/password (`BIOMETRIC_STRONG` with `DEVICE_CREDENTIAL` fallback).
- **Lifecycle Auto-Lock:** Automatically locks the application when placed in the background (`ON_STOP`) and prompts for authentication upon returning (`ON_RESUME`).
- **Expressive AppLockScreen:** Full-screen Material 3 lock screen overlay with an explicit unlock trigger.

### 10. Material 3 Expressive Theming & Curated Accent System
- **Full Material 3 Expressive Implementation:** Native dynamic theming with high-chroma tonal scales, springy shape curvature, vibrant container surfaces, and tailored tokens in both **Light** and **Dark** modes.
- **Theme Mode Selector:** Seamless runtime toggle between `System Default`, `Dark Mode`, and `Light Mode`.
- **Prebuilt Accent Section:** Instant selection between 5 handcrafted bespoke accent palettes:
  - **Black & White (Monochrome):** Minimalist grayscale, deep onyx, and stark zinc white styling.
  - **Crimson:** Bold ruby crimson and rosewood tones.
  - **Ocean:** Deep sapphire azure and marine cyan accents.
  - **Sage:** Calming organic eucalyptus and botanical moss.
  - **Amber:** Warm honey gold and radiant sunset topaz.
- **Adaptive Launcher Icon:** 3D metallic Rupee symbol (**₹**) with financial growth arc and ambient glow across all mipmap densities, with circular launcher masks and Android 13+ Material You themed monochrome icon support.

---

## Technology Stack

| Layer | Technology | Version |
|---|---|---|
| **Language** | Kotlin | 2.0.21 |
| **UI Framework** | Jetpack Compose (Material 3 Expressive) | BOM 2024.10.01 |
| **Local Database** | Room with SQLite FTS4 & Triggers | 2.6.1 (Schema v8) |
| **Security & Auth** | AndroidX Biometric & AES-256-GCM / PBKDF2 | 1.2.0-alpha05 |
| **Home Screen Widgets** | Android AppWidgetProvider with RemoteViews | API 26 - 35 |
| **Concurrency** | Kotlin Coroutines & StateFlow | 1.8.1 |
| **Background Processing**| WorkManager | 2.9.1 |
| **System Services** | `NotificationListenerService` | Android 8.0 - 15 |
| **TLS & Protocols** | Native Java/Kotlin SSLSocket (IMAP TLS) | TLSv1.2 / TLSv1.3 |
| **Target SDK** | Android 15 (API 35) | Min SDK 26 (Android 8.0) |
| **Hardware Target** | Google Tensor G4 (Pixel 9) + Fallback Heuristics | |

---

## Project Structure

```
app/src/main/java/com/moneytracker/app/
├── MoneyTrackerApp.kt           # Application class & container initialization
├── AppContainer.kt              # Dependency container with on-demand lazy singletons
├── MainActivity.kt              # FragmentActivity hosting Jetpack Compose & BiometricPrompt
├── ai/
│   ├── FinanceRagEngine.kt      # 3-tier hybrid RAG retrieval pipeline
│   ├── GeminiApiClient.kt       # Google AI Studio Gemini API client with strict financial prompts
│   └── OnDeviceAiEngine.kt      # Tensor G4 TPU detector, spam filter & offline heuristics
├── backup/
│   └── BackupManager.kt         # AES-256-GCM authenticated encryption & database restore
├── data/
│   ├── db/
│   │   ├── FinanceDao.kt        # Room DAOs (AccountDao, TransactionDao, BudgetDao, FTS4)
│   │   ├── FinanceDatabase.kt   # Room database & schema migrations (v1 through v8)
│   │   └── FinanceEntities.kt   # Entity definitions, FTS virtual tables & indices
│   ├── local/
│   │   ├── AiPreferences.kt     # Encrypted preferences for AI engine and API key
│   │   ├── EmailPreferences.kt  # On-device storage for Gmail credentials and sync state
│   │   ├── ExclusionPreferences.kt # Persistent dynamic keyword exclusion engine
│   │   ├── HapticPreferences.kt # Tactile vibration toggle and intensity settings
│   │   ├── NotificationPreferences.kt # Managed packages & notification listener state
│   │   ├── SecurityPreferences.kt # Biometric app lock toggle
│   │   ├── SetupPreferences.kt  # Onboarding & initial setup tracking
│   │   └── ThemePreferences.kt  # Material 3 Expressive mode & prebuilt accents
│   ├── model/
│   │   └── FinanceModels.kt     # Domain models, parsed transactions, and UI states
│   └── repo/
│       └── FinanceRepository.kt # Central repository unifying Ingestion, DB, Backup & AI
├── email/
│   └── EmailSyncManager.kt      # Direct TLS socket IMAP client with spam pre-filtering
├── notification/
│   └── TransactionNotificationListenerService.kt # Real-time Android status bar alert listener
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
│   ├── theme/                   # Fintech color palette, Material 3 Expressive tokens & accents
│   └── MainViewModel.kt         # Primary ViewModel coordinating UI states and flows
├── widget/
│   └── BalanceWidgetProvider.kt # Home screen 4x2 balance widget with refresh & quick add
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
- **Migration 7 to 8:**
  - Updates `accounts` table: adds `balanceProofSnippet TEXT`, `balanceProofSource TEXT`, and `isBalanceVerified INTEGER NOT NULL DEFAULT 0` for forensic balance auditability.

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
3. **Assemble Release APK:**
   ```bash
   ./gradlew assembleRelease
   ```
   The output APK will be located at:
   ```
   app/build/outputs/apk/release/app-release.apk
   ```
4. **Direct Install via ADB:**
   ```bash
   adb install -r app/build/outputs/apk/release/app-release.apk
   ```

---

## Ingestion Channel Setup

### Option 1: Android Notification Access (Recommended — Zero Passwords)
1. Open **Money Tracker** on your device and navigate to **Settings**.
2. Under the **Ingestion** tab, tap **Grant Permission in Android Settings**.
3. Toggle on **Money Tracker** in the system Device & App Notifications menu.
4. Return to the app and ensure monitoring is active for **Gmail**, **UPI Apps (GPay, PhonePe, Paytm, CRED)**, and **Bank Mobile Apps**.
5. Any transaction alert posted to your status bar will be parsed, enriched with AI notes, and recorded in real time.

### Option 2: On-Device Google Email (Gmail) Sync
1. Enable 2-Step Verification on your Google Account.
2. Generate an **App Password** for Money Tracker at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) (16 letters, e.g. `abcd efgh ijkl mnop`).
3. In Money Tracker, navigate to **Settings > Ingestion > Google Email Alerts (100% On-Device)**.
4. Toggle **Enable Email Sync**, enter your Gmail address and the 16-letter App Password (use the visibility eye icon to verify input), and tap **Save Credentials**.
5. Tap **Sync Recent Bank Emails**. The app connects directly to `imap.gmail.com:993` via encrypted TLS, filters out marketing blasts, extracts bank transaction emails, and updates your ledger.

---

## Permissions & Privacy Policy

Money Tracker is designed to ensure that **no confidential financial data leaves your phone**:
- `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`: Captures transaction notifications from banking and UPI apps in real time.
- `android.permission.RECEIVE_SMS`: Intercepts transaction alerts from your financial institutions in real time.
- `android.permission.READ_SMS`: Performs historical sync of transactions upon explicit user request.
- `android.permission.INTERNET`: Used strictly for direct TLS email sync with `imap.gmail.com` and optional Gemini API queries when configured.
- `android.permission.USE_BIOMETRIC`: Authenticates the user via device biometrics before granting access to financial ledgers.
- `android.permission.VIBRATE`: Provides tactile haptic feedback on linear resonant actuators.

---

## License

This project is licensed under the Apache License, Version 2.0. See the LICENSE file for details.
