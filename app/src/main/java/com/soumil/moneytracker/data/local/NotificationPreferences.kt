package com.soumil.moneytracker.data.local

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class NotificationPreferences(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isNotificationListenerEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_NOTIFICATION_LISTENER_ENABLED, true),
    )
    val isNotificationListenerEnabled: StateFlow<Boolean> = _isNotificationListenerEnabled

    private val _isGmailMonitoringEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_GMAIL_MONITORING_ENABLED, true),
    )
    val isGmailMonitoringEnabled: StateFlow<Boolean> = _isGmailMonitoringEnabled

    private val _isPaymentAppsMonitoringEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_PAYMENT_APPS_MONITORING_ENABLED, true),
    )
    val isPaymentAppsMonitoringEnabled: StateFlow<Boolean> = _isPaymentAppsMonitoringEnabled

    private val _isBankAppsMonitoringEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_BANK_APPS_MONITORING_ENABLED, true),
    )
    val isBankAppsMonitoringEnabled: StateFlow<Boolean> = _isBankAppsMonitoringEnabled

    private val _lastCapturedTimestamp = MutableStateFlow(
        preferences.getLong(KEY_LAST_CAPTURED_TIMESTAMP, 0L),
    )
    val lastCapturedTimestamp: StateFlow<Long> = _lastCapturedTimestamp

    private val _lastCapturedPackage = MutableStateFlow(
        preferences.getString(KEY_LAST_CAPTURED_PACKAGE, null),
    )
    val lastCapturedPackage: StateFlow<String?> = _lastCapturedPackage

    private val _capturedCount = MutableStateFlow(
        preferences.getInt(KEY_CAPTURED_COUNT, 0),
    )
    val capturedCount: StateFlow<Int> = _capturedCount

    fun setNotificationListenerEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_NOTIFICATION_LISTENER_ENABLED, enabled).apply()
        _isNotificationListenerEnabled.value = enabled
    }

    fun setGmailMonitoringEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_GMAIL_MONITORING_ENABLED, enabled).apply()
        _isGmailMonitoringEnabled.value = enabled
    }

    fun setPaymentAppsMonitoringEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PAYMENT_APPS_MONITORING_ENABLED, enabled).apply()
        _isPaymentAppsMonitoringEnabled.value = enabled
    }

    fun setBankAppsMonitoringEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_BANK_APPS_MONITORING_ENABLED, enabled).apply()
        _isBankAppsMonitoringEnabled.value = enabled
    }

    fun recordCapturedNotification(packageName: String, timestamp: Long) {
        val newCount = _capturedCount.value + 1
        preferences.edit()
            .putLong(KEY_LAST_CAPTURED_TIMESTAMP, timestamp)
            .putString(KEY_LAST_CAPTURED_PACKAGE, packageName)
            .putInt(KEY_CAPTURED_COUNT, newCount)
            .apply()
        _lastCapturedTimestamp.value = timestamp
        _lastCapturedPackage.value = packageName
        _capturedCount.value = newCount
    }

    fun isSystemPermissionGranted(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    fun buildSystemSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                val component = ComponentName(
                    context.packageName,
                    "com.soumil.moneytracker.notification.TransactionNotificationListenerService",
                )
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString())
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }

    companion object {
        internal const val PREFS_NAME = "money_tracker_notification_prefs"
        internal const val KEY_NOTIFICATION_LISTENER_ENABLED = "notification_listener_enabled"
        internal const val KEY_GMAIL_MONITORING_ENABLED = "gmail_monitoring_enabled"
        internal const val KEY_PAYMENT_APPS_MONITORING_ENABLED = "payment_apps_monitoring_enabled"
        internal const val KEY_BANK_APPS_MONITORING_ENABLED = "bank_apps_monitoring_enabled"
        internal const val KEY_LAST_CAPTURED_TIMESTAMP = "last_captured_timestamp"
        internal const val KEY_LAST_CAPTURED_PACKAGE = "last_captured_package"
        internal const val KEY_CAPTURED_COUNT = "notification_captured_count"

        const val PACKAGE_GMAIL = "com.google.android.gm"
        const val PACKAGE_GPAY = "com.google.android.apps.nbu.paisa.user"
        const val PACKAGE_PHONEPE = "com.phonepe.app"
        const val PACKAGE_PAYTM = "net.one97.paytm"
        const val PACKAGE_CRED = "com.dreamplug.androidapp"
        const val PACKAGE_BHIM = "in.org.npci.upiapp"
        const val PACKAGE_AMAZON_PAY = "in.amazon.mShop.android.shopping"

        val PAYMENT_APP_PACKAGES = setOf(
            PACKAGE_GPAY,
            PACKAGE_PHONEPE,
            PACKAGE_PAYTM,
            PACKAGE_CRED,
            PACKAGE_BHIM,
            PACKAGE_AMAZON_PAY,
        )

        val BANK_APP_PACKAGES = setOf(
            "com.snapwork.hdfc", // HDFC Bank
            "com.csam.icici.bank.imobile", // ICICI iMobile
            "com.sbi.lotusintouch", // SBI YONO
            "com.axis.mobile", // Axis Mobile
            "com.msf.kbank.mobile", // Kotak 811
            "com.indusind.mpassbook", // IndusInd
            "com.pnb.pnbone", // PNB ONE
            "com.bankofbaroda.mconnect", // bob World
            "com.canarabank.mobility", // Canara ai1
            "com.sc.scmobile.in", // Standard Chartered India
            "money.jupiter", // Jupiter
            "money.fi.app", // Fi Money
            "org.flipkart.slice", // Slice
            "com.slicepay.slice", // Slice legacy
        )
    }
}
