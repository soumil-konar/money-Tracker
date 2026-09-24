# Money Tracker

An enterprise-grade, privacy-first automated personal finance management platform engineered for Android 15. Built with a tri-channel ingestion architecture combining Google Tensor G4 hardware acceleration on Pixel 9 devices, real-time Android Notification Access, 100% on-device direct TLS Google Email sync, automated SMS bank alert ingestion, dynamic exclusion filtering, intelligent transaction description synthesis, real-time bank balance tracking, 3-tier Retrieval-Augmented Generation (RAG), and a modern Expressive Material 3 fintech design system.

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
 [ Room Database v7 ]                                                             [ Vector Embedding Store ]
 • Accounts (currentBalance, balanceUpdatedAtMillis)                               • Gemini Embedding-2
 • Transactions (availableBalance, budgetInclusion, note)                         • 256-dim Vector Store
 • FTS4 Virtual Table (Automated Triggers)                                         • Local Cosine Similarity
 • Budgets & Recurring Subscriptions
        │                                                                                     │
        └──────────────────────────────────────────┬──────────────────────────────────────────┘
                                                   │
                                                   ▼
                                         [ FinanceRagEngine ]
                         (Tier 1: Exact SQL | Tier 2: FTS4 | Tier 3: Vector)
                                                   │
                                                   ▼
                                     [ Interactive UI & Assistants ]
                     • Live Bank Balance Carousel (Accounts & Cards)
                     • Transaction Cards with Contextual AI Badges & Notes
                     • Dynamic Exclusion Filters & Password Visibility Controls
                     • SpendingAssistantSheet (Auto Pixel-First / On-Device / Cloud)
                     • Biometric App Lock & Custom Tactile Haptic Actuators
```

---

## Key Features

### 1. Tri-Channel On-Device Ingestion Pipeline (Notifications + SMS + Gmail)
- **Real-Time Notification Access (`NotificationListenerService`):** Captures incoming status bar notifications from Gmail (`com.google.android.gm`), UPI apps (Google Pay, PhonePe, Paytm, CRED, BHIM, Navi, Amazon Pay), and banking apps. Operates with **zero passwords**, zero OAuth friction, and zero background polling.
- **Real-Time SMS Ingestion:** Intercepts incoming transactional SMS across Indian financial institutions (HDFC, SBI, ICICI, Axis, Kotak, PNB, Bank of Baroda, IndusInd) and payment gateways. Protected with `goAsync()` and an 8-second watchdog timer to eliminate ANRs.
- **100% On-Device Direct TLS Gmail IMAP:** Native socket client (`imap.gmail.com:993`) that connects directly to Google via TLS, supporting 16-letter Google App Passwords and standard credentials with automated Unicode whitespace/delimiter normalization.
- **Unified Pipeline:** All three channels pass through identical deduplication, exclusion matching, entity extraction, categorization, and account balance adjustment routines.

### 2. Dynamic Transaction Exclusion Engine (Zero Hardcoding)
- **User-Configurable Rules:** Define custom exclusion rules directly in Settings to automatically skip transactions (e.g., Steam purchases, Epic Games, PlayStation Network, OTPs, refunds).
- **Smart Boundary Matching:** Uses word-boundary regex (`\bOTP\b`) for short acronyms to eliminate false positives in regular words (e.g., "hotpot" or "prototype"), paired with case-insensitive phrase matching for multi-word merchants.
- **Settings Management UI:** Interactive Chip flow-layout with one-tap rule deletion, reset to defaults, and an "Add Keyword" dialog.

### 3. AI Rich Description Synthesis & Metadata
- **Contextual Transaction Notes:** Both Gemini Cloud AI and the On-Device TPU Engine synthesize natural, human-readable explanations stored in `TransactionEntity.note` (e.g., *"Dinner order on Swiggy via HDFC Credit Card"*, *"Chai & snacks at Indiranagar outlet"*, *"Monthly Netflix subscription auto-debit"*).
- **Expressive Transaction UI:** Transaction cards display contextual metadata badges with dynamic icons (`AutoAwesome` for AI insights, `Place` for physical outlets, and `Notes` for transaction channels).

### 4. Promotional Marketing & Spam Rejection
- **Strict Financial Classification:** Eliminates false transactions created by bank marketing blasts, loan pitches, and discount offers (e.g., *"Up to ₹30,000 off on electronics with ICICI Bank Credit card"*, *"Save up to ₹30,000 on EMI purchases"*, *"Pre-approved personal loan"*).
- **Past-Tense Confirmation Required:** Ingestion strictly requires verified past-tense debit or credit confirmation (`"debited"`, `"spent"`, `"paid to"`, `"credited"`, `"deposited"`, or credit card bill payment).
- **Subject-Line Pre-Filtering:** Automatically filters out marketing blast email subjects during Gmail sync, saving battery and preventing ledger pollution.
- **Automated Bogus Entry Purging:** Built-in deduplication automatically identifies and purges marketing artifacts and boilerplate placeholders.

### 5. Ground-Up Transaction Intelligence & Accounting
- **Credit Card Bill Payments Treated as Transfers:** Credit card repayments (e.g., *"Payment received towards your credit card"*, *"paid via CRED"*) are strictly classified as `TransactionCategory.TRANSFER`, flagged with `isCardBillPayment = true`, and excluded from monthly budget calculations (`countsTowardBudget = false`).
- **Utility Bill Payments Unblocked:** Cleanly distinguishes executed bill payments (electricity, water, broadband debits) from unpaid due notices (*"bill is due on 25-05-2026"*).
- **Authoritative Balance Extraction:** Intelligently extracts live available balances (`Avl bal Rs.`, `Avail Bal`, `Bal: INR`, `₹`) from alerts.
- **Unicode Indian Rupee Symbol:** Full native parsing support for `₹` alongside `INR`, `Rs.`, and `Re.`.

### 6. Live Bank Accounts & Balance Tracking (Room v7)
- **Authoritative & Delta Arithmetic:** When an alert includes an authoritative balance, the account balance updates immediately. For alerts without explicit balances, account balances dynamically adjust via delta arithmetic (`+amount` for CREDIT, `-amount` for DEBIT).
- **Accounts & Balances Carousel:** Horizontal carousel on the Home Screen displaying each linked bank and card with masked account digits, institution branding, live formatted balances (e.g., `₹40,000`), and timestamps.
- **Tracked Balance Calculation:** Dynamically aggregates total liquid funds across all active bank accounts.

### 7. Google Pixel 9 On-Device AI Engine & Cloud Augmentation
- **Tensor G4 TPU Acceleration:** Automatically detects Google Pixel hardware (`Build.HARDWARE` / `SOC_MODEL` tensor detection) to execute sub-millisecond on-device transaction classification.
- **Three Configurable AI Modes:**
  1. **Auto (Pixel-First) [Recommended]:** Runs transactional parsing locally on-device and leverages Gemini Flash Lite only for deep semantic conversational synthesis when an API key is configured.
  2. **On-Device Only (Air-Gapped):** 100% offline. Zero network calls. All analytics and assistant queries run via local heuristics.
  3. **Cloud Only:** Routes complex queries and synthesis via Google AI Studio's `gemini-3.5-flash-lite`.

### 8. Biometric App Lock & Device Security
- **BiometricPrompt Integration:** Protects sensitive financial ledgers using fingerprint, face unlock, or device PIN/password (`BIOMETRIC_STRONG` with `DEVICE_CREDENTIAL` fallback).
- **Lifecycle Auto-Lock:** Automatically locks the application when placed in the background (`ON_STOP`) and prompts for authentication upon returning (`ON_RESUME`).
- **Expressive AppLockScreen:** Full-screen Material 3 lock screen overlay with an explicit unlock trigger.

### 9. Tactile & Haptic System
- **Device-Calibrated Actuator Feedback:** Custom vibration effects scaled to your device's linear resonant actuator (`HapticFeedbackManager`).
- **Configurable Intensities:** Choose between `SUBTLE`, `BALANCED`, and `STRONG` vibration profiles, with tactile clicks on buttons, tabs, ledger approvals, and dialog actions.

### 10. Modern Fintech UI & Adaptive App Icon
- **Expressive Fintech Theme:** Dark obsidian surface (`#0C0C12`) with glowing Ember Flame (`#FF5E2B`), Emerald Mint (`#10B981`), Electric Blue (`#38BDF8`), and Champagne Gold accents.
- **Password Visibility Toggle:** Show / hide password eye icon button in Gmail connectivity settings for seamless credential verification.
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
| **System Services** | `NotificationListenerService` | Android 8.0 - 15 |
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
│   ├── GeminiApiClient.kt       # Google AI Studio Gemini API client with strict financial prompts
│   └── OnDeviceAiEngine.kt      # Tensor G4 TPU detector, spam filter & offline heuristics
├── data/
│   ├── db/
│   │   ├── FinanceDao.kt        # Room DAOs (AccountDao, TransactionDao, FTS4)
│   │   ├── FinanceDatabase.kt   # Room database & schema migrations (v1 through v7)
│   │   └── FinanceEntities.kt   # Entity definitions, FTS virtual tables & indices
│   ├── local/
│   │   ├── AiPreferences.kt     # Encrypted preferences for AI engine and API key
│   │   ├── EmailPreferences.kt  # On-device storage for Gmail credentials and sync state
│   │   ├── ExclusionPreferences.kt # Persistent dynamic keyword exclusion engine
│   │   ├── HapticPreferences.kt # Tactile vibration toggle and intensity settings
│   │   ├── NotificationPreferences.kt # Managed packages & notification listener state
│   │   ├── SecurityPreferences.kt # Biometric app lock toggle
│   │   └── SetupPreferences.kt  # Onboarding & initial setup tracking
│   ├── model/
│   │   └── FinanceModels.kt     # Domain models, parsed transactions, and UI states
│   └── repo/
│       └── FinanceRepository.kt # Central repository unifying Notifications, SMS, Email, DB & AI
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
2. **Build and run unit tests (67 tests):**
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
4. **Direct Install via ADB:**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Ingestion Channel Setup

### Option 1: Android Notification Access (Recommended — Zero Passwords)
1. Open **Money Tracker** on your device and navigate to **Settings**.
2. Under **Transaction Notification Access**, tap **Grant Permission in Android Settings**.
3. Toggle on **Money Tracker** in the system Device & App Notifications menu.
4. Return to the app and ensure monitoring is active for **Gmail**, **UPI Apps (GPay, PhonePe, Paytm, CRED)**, and **Bank Mobile Apps**.
5. Any transaction alert posted to your status bar will be parsed, enriched with AI notes, and recorded in real time.

### Option 2: On-Device Google Email (Gmail) Sync
1. Enable 2-Step Verification on your Google Account.
2. Generate an **App Password** for Money Tracker at [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) (16 letters, e.g. `abcd efgh ijkl mnop`).
3. In Money Tracker, navigate to **Settings > Google Email Alerts (100% On-Device)**.
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
