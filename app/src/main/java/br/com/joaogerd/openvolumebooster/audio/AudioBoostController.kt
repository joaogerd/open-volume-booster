package br.com.joaogerd.openvolumebooster.audio

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build

class AudioBoostController {
    private val chains = linkedMapOf<Int, EffectChain>()
    private var boostPercent: Int = 0
    private var systemVolumePercent: Int = 100

    fun enable(percent: Int, sessionId: Int = GLOBAL_AUDIO_SESSION, volumePercent: Int = systemVolumePercent): BoostState {
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

    fun closeSession(sessionId: Int) { chains.remove(sessionId)?.release() }

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
        private var dynamicsProcessing: DynamicsProcessing? = null

        fun enable(profile: BoostProfile): BoostState {
            if (profile.requestedPercent == 0 || profile.loudnessGainMb <= 0) {
                disable()
                return BoostState.Disabled("session $sessionId: boost=0")
            }

            val loudnessState = enableLoudness(profile)
            runCatching { enableDynamicsLimiter(profile) }
            return loudnessState
        }

        private fun enableLoudness(profile: BoostProfile): BoostState {
            return try {
                if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(sessionId)
                loudnessEnhancer?.setTargetGain(profile.loudnessGainMb)
                loudnessEnhancer?.enabled = true
                BoostState.Enabled(profile, "session $sessionId: loudness=${profile.loudnessGainMb}mB")
            } catch (exception: RuntimeException) {
                release()
                BoostState.Error("session $sessionId: loudness failed: ${exception.message ?: "unsupported"}")
            }
        }

        private fun enableDynamicsLimiter(profile: BoostProfile) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
            if (dynamicsProcessing == null) {
                val config = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    1,
                    true,
                    1,
                    false,
                    1,
                    false,
                    1,
                    true
                ).build()
                dynamicsProcessing = DynamicsProcessing(0, sessionId, config)
            }

            dynamicsProcessing?.setInputGainbyChannel(0, 0f)
            dynamicsProcessing?.setLimiterByChannelIndex(
                0,
                DynamicsProcessing.Limiter(
                    true,
                    true,
                    0,
                    LIMITER_ATTACK_MS,
                    LIMITER_RELEASE_MS,
                    LIMITER_RATIO,
                    profile.limiterThresholdDb,
                    profile.limiterPostGainDb
                )
            )
            dynamicsProcessing?.enabled = true
        }

        fun disable() {
            runCatching { loudnessEnhancer?.enabled = false }
            runCatching { dynamicsProcessing?.enabled = false }
        }

        fun release() {
            runCatching { loudnessEnhancer?.release() }
            runCatching { dynamicsProcessing?.release() }
            loudnessEnhancer = null
            dynamicsProcessing = null
        }
    }

    companion object {
        const val GLOBAL_AUDIO_SESSION = 0
        const val MIN_PERCENT = BoostGainModel.MIN_PERCENT
        const val MAX_PERCENT = BoostGainModel.MAX_PERCENT
        private const val LIMITER_ATTACK_MS = 1f
        private const val LIMITER_RELEASE_MS = 60f
        private const val LIMITER_RATIO = 12f
    }
}

sealed interface BoostState {
    val message: String
    data class Enabled(val profile: BoostProfile, override val message: String) : BoostState
    data class Disabled(override val message: String) : BoostState
    data class Error(override val message: String) : BoostState
}
