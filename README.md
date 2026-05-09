# Money Tracker

Android SMS-first money tracker built with `Kotlin`, `Jetpack Compose`, `Room`, and `WorkManager`.

## What is implemented

- India-oriented SMS parsing for debit and credit messages
- Event-driven SMS ingestion with duplicate protection
- Local-first Room persistence for transactions, budgets, accounts, and subscriptions
- Review queue for low-confidence transactions
- Manual transaction entry and manual subscription entry
- Recurring subscription suggestions via daily WorkManager scan
- Reference-inspired warm fintech dashboard UI with:
  - Home
  - Transactions
  - More
  - Settings

## Important notes

- This repository was created in an empty workspace from scratch.
- The Gradle wrapper is included. Android Studio will download the Gradle distribution on first sync.
- The current machine did not have Java, Gradle, or Android SDK tooling available on `PATH`, so build verification was not completed here.
- `READ_SMS` and `RECEIVE_SMS` are Play Store sensitive permissions and will require policy review for production distribution.

## Next steps

1. Open the project in Android Studio on a machine with the Android SDK installed.
2. Let Gradle sync and download the Gradle distribution and dependencies.
3. Run the unit tests and app on an Android device or emulator.
4. Tighten parser rules with real bank and UPI SMS samples before wider use.
