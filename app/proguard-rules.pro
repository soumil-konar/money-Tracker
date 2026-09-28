# Room internal tables and schemas
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keepclassmembers class com.moneytracker.app.data.db.** { *; }

# Data transfer models used in JSON parsing
-keepclassmembers class com.moneytracker.app.data.model.** { *; }
-keepclassmembers class com.moneytracker.app.ai.** { *; }

# Coroutines, BiometricPrompt, and SSL socket protocols
-keepclassmembers class * extends kotlinx.coroutines.** { *; }
-keep class androidx.biometric.** { *; }
