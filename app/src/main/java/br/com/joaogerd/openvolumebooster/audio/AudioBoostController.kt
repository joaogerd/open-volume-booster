package br.com.joaogerd.openvolumebooster.audio

import android.media.audiofx.LoudnessEnhancer

/**
 * Minimal and predictable booster controller.
 *
 * This intentionally uses only LoudnessEnhancer. Equalizer and BassBoost were
 * removed from the booster path because combining them with loudness gain caused
 * early clipping on real devices.
 *
 * The public input is a boost percent from 0 to 100. Internally it is converted
 * to millibels using a BoostX-like curve: targetGain = percent * 25.
 */
class AudioBoostController {
    private val chains = linkedMapOf<Int, EffectChain>()
    private var boostPercent: Int = 0

    fun enable(percent: Int, sessionId: Int = GLOBAL_AUDIO_SESSION): BoostState {
        boostPercent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val chain = chains.getOrPut(sessionId) { EffectChain(sessionId) }
        return chain.enable(boostPercent)
    }

    fun update(percent: Int): BoostState {
        boostPercent = percent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        if (chains.isEmpty()) {
            return enable(boostPercent, GLOBAL_AUDIO_SESSION)
        }

        val states = chains.map { (_, chain) -> chain.enable(boostPercent) }
        val enabled = states.filterIsInstance<BoostState.Enabled>()
        return if (enabled.isNotEmpty()) {
            BoostState.Enabled(enabled.joinToString(separator = " | ") { it.message })
        } else {
            BoostState.Error(states.joinToString(separator = "\n") { it.message })
        }
    }

    fun closeSession(sessionId: Int) {
        chains.remove(sessionId)?.release()
    }

    fun disable(): BoostState {
        chains.values.forEach { it.disable() }
        return BoostState.Disabled("Boost disabled")
    }

    fun release() {
        chains.values.forEach { it.release() }
        chains.clear()
    }

    private class EffectChain(private val sessionId: Int) {
        private var loudnessEnhancer: LoudnessEnhancer? = null

        fun enable(boostPercent: Int): BoostState {
            return try {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = LoudnessEnhancer(sessionId)
                }

                val gainMb = percentToGainMb(boostPercent)
                loudnessEnhancer?.setTargetGain(gainMb)
                loudnessEnhancer?.enabled = boostPercent > 0

                if (boostPercent > 0) {
                    BoostState.Enabled("session $sessionId: loudness=${gainMb}mB")
                } else {
                    BoostState.Disabled("session $sessionId: boost=0")
                }
            } catch (exception: RuntimeException) {
                release()
                BoostState.Error("session $sessionId: loudness failed: ${exception.message ?: "unsupported"}")
            }
        }

        fun disable() {
            runCatching { loudnessEnhancer?.enabled = false }
        }

        fun release() {
            runCatching { loudnessEnhancer?.release() }
            loudnessEnhancer = null
        }

        private fun percentToGainMb(percent: Int): Int {
            return (percent.coerceIn(MIN_PERCENT, MAX_PERCENT) * GAIN_MB_PER_PERCENT)
                .coerceIn(0, MAX_GAIN_MB)
        }
    }

    companion object {
        const val GLOBAL_AUDIO_SESSION = 0
        const val MIN_PERCENT = 0
        const val MAX_PERCENT = 100
        private const val GAIN_MB_PER_PERCENT = 25
        private const val MAX_GAIN_MB = 2500
    }
}

sealed interface BoostState {
    val message: String

    data class Enabled(override val message: String) : BoostState
    data class Disabled(override val message: String) : BoostState
    data class Error(override val message: String) : BoostState
}
