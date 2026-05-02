package ai.opencode.app

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.json.JSONObject

class HapticBridge(private val context: Context) {

    fun haptic(params: JSONObject, callback: BridgeCallback) {
        val style = params.optString("style", "light")
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let { v ->
            when (style) {
                "light" -> v.vibrate(VibrationEffect.createOneShot(10, 30))
                "medium" -> v.vibrate(VibrationEffect.createOneShot(15, 60))
                "heavy" -> v.vibrate(VibrationEffect.createOneShot(20, 100))
                "success" -> {
                    val timings = longArrayOf(0, 20, 40, 30)
                    val amplitudes = intArrayOf(0, 80, 0, 80)
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                "warning" -> {
                    val timings = longArrayOf(0, 30, 30, 30)
                    val amplitudes = intArrayOf(0, 120, 0, 120)
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
                "error" -> {
                    val timings = longArrayOf(0, 30, 30, 30, 30, 50)
                    val amplitudes = intArrayOf(0, 150, 0, 150, 0, 200)
                    v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                }
            }
        }
        callback(null, null)
    }
}
