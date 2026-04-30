package br.com.joaogerd.openvolumebooster.audio

import android.media.audiofx.LoudnessEnhancer

/**
 * Small wrapper around Android loudness enhancement.
 *
 * The effect is attached to the global output mix by using audio session 0.
 * Device and Android-version support can vary, so failures are reported as state
 * instead of crashing the application.
 */
class AudioBoostController {
    private var enhancer: LoudnessEnhancer? = null

    fun enable(targetGainMb: Int): BoostState {
        return try {
            if (enhancer == null) {
                enhancer = LoudnessEnhancer(0)
            }
            enhancer?.setTargetGain(targetGainMb.coerceIn(0, MAX_GAIN_MB))
            enhancer?.enabled = true
            BoostState.Enabled
        } catch (exception: RuntimeException) {
            release()
            BoostState.Error(exception.message ?: "Audio boost is not supported on this device")
        }
    }

    fun update(targetGainMb: Int): BoostState {
        return try {
            enhancer?.setTargetGain(targetGainMb.coerceIn(0, MAX_GAIN_MB))
            if (enhancer?.enabled == true) BoostState.Enabled else BoostState.Disabled
        } catch (exception: RuntimeException) {
            release()
            BoostState.Error(exception.message ?: "Unable to update audio boost")
        }
    }

    fun disable() {
        enhancer?.enabled = false
    }

    fun release() {
        enhancer?.release()
        enhancer = null
    }

    companion object {
        const val MAX_GAIN_MB = 2000
    }
}

sealed interface BoostState {
    data object Enabled : BoostState
    data object Disabled : BoostState
    data class Error(val message: String) : BoostState
}
