package br.com.joaogerd.openvolumebooster.audio

import android.media.audiofx.LoudnessEnhancer

class AudioBoostController {
    private val chains = linkedMapOf<Int, EffectChain>()
    private var boostPercent: Int = 0
    private var systemVolumePercent: Int = 100

    fun enable(
        percent: Int,
        sessionId: Int = GLOBAL_AUDIO_SESSION,
        volumePercent: Int = systemVolumePercent
    ): BoostState {
        boostPercent = percent.coerceIn(BoostGainModel.MIN_PERCENT, BoostGainModel.MAX_PERCENT)
        systemVolumePercent = volumePercent.coerceIn(0, 100)
        val chain = chains.getOrPut(sessionId) { EffectChain(sessionId) }
        return chain.enable(BoostGainModel.compute(boostPercent, systemVolumePercent))
    }

    fun update(percent: Int, volumePercent: Int = systemVolumePercent): BoostState {
        boostPercent = percent.coerceIn(BoostGainModel.MIN_PERCENT, BoostGainModel.MAX_PERCENT)
        systemVolumePercent = volumePercent.coerceIn(0, 100)
        val profile = BoostGainModel.compute(boostPercent, systemVolumePercent)
        if (chains.isEmpty()) return enable(boostPercent, GLOBAL_AUDIO_SESSION, systemVolumePercent)

        val states = chains.map { (_, chain) -> chain.enable(profile) }
        val enabled = states.filterIsInstance<BoostState.Enabled>()
        return if (enabled.isNotEmpty()) {
            BoostState.Enabled(profile, enabled.joinToString(separator = " | ") { it.message })
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

        fun enable(profile: BoostProfile): BoostState {
            return try {
                if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(sessionId)
                loudnessEnhancer?.setTargetGain(profile.targetGainMb)
                loudnessEnhancer?.enabled = profile.targetGainMb > 0

                if (profile.targetGainMb > 0) {
                    BoostState.Enabled(profile, "session $sessionId: loudness=${profile.targetGainMb}mB")
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
    }

    companion object {
        const val GLOBAL_AUDIO_SESSION = 0
        const val MIN_PERCENT = BoostGainModel.MIN_PERCENT
        const val MAX_PERCENT = BoostGainModel.MAX_PERCENT
    }
}

sealed interface BoostState {
    val message: String

    data class Enabled(val profile: BoostProfile, override val message: String) : BoostState
    data class Disabled(override val message: String) : BoostState
    data class Error(override val message: String) : BoostState
}
