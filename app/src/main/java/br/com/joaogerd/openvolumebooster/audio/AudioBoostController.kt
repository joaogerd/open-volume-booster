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
            if (profile.requestedPercent == 0 || profile.inputGainDb <= 0f) {
                disable()
                return BoostState.Disabled("session $sessionId: boost=0")
            }

            val dynamicsState = enableDynamicsProcessing(profile)
            if (dynamicsState is BoostState.Enabled) return dynamicsState
            return enableLoudnessFallback(profile, dynamicsState.message)
        }

        private fun enableDynamicsProcessing(profile: BoostProfile): BoostState {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                return BoostState.Error("DynamicsProcessing unavailable before Android 9")
            }
            return try {
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

                dynamicsProcessing?.setInputGainByChannelIndex(0, profile.inputGainDb)
                dynamicsProcessing?.setLimiterByChannelIndex(
                    0,
                    DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        1,
                        60f,
                        profile.limiterPostGainDb,
                        profile.limiterThresholdDb
                    )
                )
                dynamicsProcessing?.enabled = true
                BoostState.Enabled(profile, "session $sessionId: dynamics=${profile.inputGainDb}dB limiter=${profile.limiterThresholdDb}dB")
            } catch (exception: RuntimeException) {
                runCatching { dynamicsProcessing?.release() }
                dynamicsProcessing = null
                BoostState.Error("dynamics failed: ${exception.message ?: "unsupported"}")
            }
        }

        private fun enableLoudnessFallback(profile: BoostProfile, previousError: String): BoostState {
            return try {
                if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(sessionId)
                loudnessEnhancer?.setTargetGain(profile.fallbackLoudnessGainMb)
                loudnessEnhancer?.enabled = profile.fallbackLoudnessGainMb > 0
                BoostState.Enabled(profile, "session $sessionId: loudness fallback=${profile.fallbackLoudnessGainMb}mB; $previousError")
            } catch (exception: RuntimeException) {
                release()
                BoostState.Error("session $sessionId: audio boost failed: ${exception.message ?: "unsupported"}")
            }
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
    }
}

sealed interface BoostState {
    val message: String
    data class Enabled(val profile: BoostProfile, override val message: String) : BoostState
    data class Disabled(override val message: String) : BoostState
    data class Error(override val message: String) : BoostState
}
