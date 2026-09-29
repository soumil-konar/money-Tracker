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

# Google AI Edge / Android AICore AIDL & Reflection interfaces
-dontwarn module-info
-keep class com.google.ai.edge.aicore.** { *; }
-keep interface com.google.ai.edge.aicore.** { *; }
-keep class com.google.android.apps.aicore.** { *; }
-keep interface com.google.android.apps.aicore.** { *; }
-keep class com.google.android.gms.internal.aicore.** { *; }
-keepclassmembers class * extends com.google.android.gms.internal.aicore.zzex {
  <fields>;
}

# Koin WorkManager: preserve Worker (Context, WorkerParameters) constructors
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
