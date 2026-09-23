package com.soumil.moneytracker.ui.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.staticCompositionLocalOf
import com.soumil.moneytracker.data.local.HapticIntensity
import com.soumil.moneytracker.data.local.HapticPreferences

interface AppHaptics {
    fun tick()
    fun click()
    fun selection()
    fun toggle(isOn: Boolean = true)
    fun success()
    fun warning()
    fun heavy()
}

val LocalAppHaptics = staticCompositionLocalOf<AppHaptics> {
    NoOpAppHaptics
}

object NoOpAppHaptics : AppHaptics {
    override fun tick() {}
    override fun click() {}
    override fun selection() {}
    override fun toggle(isOn: Boolean) {}
    override fun success() {}
    override fun warning() {}
    override fun heavy() {}
}

class HapticFeedbackManager(
    private val context: Context,
    private val preferences: HapticPreferences,
) : AppHaptics {

    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (e: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun isHapticsAllowed(): Boolean {
        if (!preferences.isHapticEnabled.value) return false
        val vib = vibrator ?: return false
        if (!vib.hasVibrator()) return false

        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.HAPTIC_FEEDBACK_ENABLED,
                1,
            ) != 0
        } catch (e: Exception) {
            true
        }
    }

    private fun scaleAmplitude(baseAmplitude: Int): Int {
        val factor = preferences.hapticIntensity.value.scaleFactor
        return (baseAmplitude * factor).toInt().coerceIn(1, 255)
    }

    private fun playEffect(effect: VibrationEffect) {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val attrs = VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
                vib.vibrate(effect, attrs)
            } else {
                vib.vibrate(effect)
            }
        } catch (e: Exception) {
            // Ignore hardware vibration failures
        }
    }

    override fun tick() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vib.hasAmplitudeControl()) {
                val amp = scaleAmplitude(35)
                playEffect(VibrationEffect.createOneShot(10L, amp))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                playEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(10L)
            }
        } catch (e: Exception) {
            // Fallback gracefully
        }
    }

    override fun click() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vib.hasAmplitudeControl()) {
                val amp = scaleAmplitude(110)
                playEffect(VibrationEffect.createOneShot(18L, amp))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                playEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(20L)
            }
        } catch (e: Exception) {
            // Fallback gracefully
        }
    }

    override fun selection() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vib.hasAmplitudeControl()) {
                val amp = scaleAmplitude(70)
                playEffect(VibrationEffect.createOneShot(12L, amp))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                playEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(12L)
            }
        } catch (e: Exception) {
            // Fallback gracefully
        }
    }

    override fun toggle(isOn: Boolean) {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vib.hasAmplitudeControl()) {
                val amp1 = scaleAmplitude(if (isOn) 50 else 110)
                val amp2 = scaleAmplitude(if (isOn) 140 else 40)
                val timings = longArrayOf(0, 12, 25, 16)
                val amplitudes = intArrayOf(0, amp1, 0, amp2)
                playEffect(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                click()
            }
        } catch (e: Exception) {
            click()
        }
    }

    override fun success() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vib.hasAmplitudeControl()) {
                // Rhythmic double-pulse confirmation
                val timings = longArrayOf(0, 15, 40, 25)
                val amplitudes = intArrayOf(0, scaleAmplitude(80), 0, scaleAmplitude(190))
                playEffect(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                playEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 20, 40, 25), -1)
            }
        } catch (e: Exception) {
            click()
        }
    }

    override fun warning() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && vib.hasAmplitudeControl()) {
                // Distinct thud / rejection pulse
                val timings = longArrayOf(0, 30, 25, 45)
                val amplitudes = intArrayOf(0, scaleAmplitude(150), 0, scaleAmplitude(230))
                playEffect(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                playEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(50L)
            }
        } catch (e: Exception) {
            click()
        }
    }

    override fun heavy() {
        if (!isHapticsAllowed()) return
        val vib = vibrator ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && vib.hasAmplitudeControl()) {
                val amp = scaleAmplitude(240)
                playEffect(VibrationEffect.createOneShot(45L, amp))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                playEffect(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(45L)
            }
        } catch (e: Exception) {
            click()
        }
    }
}
