package br.com.joaogerd.openvolumebooster.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import kotlin.math.abs

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
        private var equalizer: Equalizer? = null
        private var bassBoost: BassBoost? = null
        private var dynamicsProcessing: DynamicsProcessing? = null

        fun enable(profile: BoostProfile): BoostState {
            if (profile.requestedPercent == 0 || profile.loudnessGainMb <= 0) {
                disable()
                return BoostState.Disabled("session $sessionId: boost=0")
            }

            val loudnessState = enableLoudness(profile)
            runCatching { enablePresenceEqualizer(profile) }
            runCatching { enableBassBoost(profile) }
            runCatching { enableDynamicsLimiter(profile) }
            return loudnessState
        }

        private fun enableLoudness(profile: BoostProfile): BoostState {
            return try {
                if (loudnessEnhancer == null) loudnessEnhancer = LoudnessEnhancer(sessionId)
                loudnessEnhancer?.setTargetGain(profile.loudnessGainMb)
                loudnessEnhancer?.enabled = true
                BoostState.Enabled(profile, "session $sessionId: loudness=${profile.loudnessGainMb}mB presence=${profile.presenceBoostMb}mB bass=${profile.bassBoostStrength}")
            } catch (exception: RuntimeException) {
                release()
                BoostState.Error("session $sessionId: loudness failed: ${exception.message ?: "unsupported"}")
            }
        }

        private fun enablePresenceEqualizer(profile: BoostProfile) {
            if (profile.presenceBoostMb <= 0) return
            if (equalizer == null) equalizer = Equalizer(0, sessionId)
            val eq = equalizer ?: return
            val range = eq.bandLevelRange
            val minLevel = range[0]
            val maxLevel = range[1]
            val presenceLevel = profile.presenceBoostMb.coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()
            val lowCutLevel = (-profile.presenceBoostMb / 4).coerceIn(minLevel.toInt(), maxLevel.toInt()).toShort()

            for (bandIndex in 0 until eq.numberOfBands) {
                val band = bandIndex.toShort()
                val freqHz = eq.getCenterFreq(band) / 1000
                val level = when {
                    freqHz < 180 -> lowCutLevel
                    isPresenceFrequency(freqHz) -> presenceLevel
                    freqHz > 7000 -> (presenceLevel / 2).toShort()
                    else -> 0
                }
                eq.setBandLevel(band, level)
            }
            eq.enabled = true
        }

        private fun enableBassBoost(profile: BoostProfile) {
            if (profile.bassBoostStrength <= 0) return
            if (bassBoost == null) bassBoost = BassBoost(0, sessionId)
            bassBoost?.setStrength(profile.bassBoostStrength)
            bassBoost?.enabled = true
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
            runCatching { equalizer?.enabled = false }
            runCatching { bassBoost?.enabled = false }
            runCatching { dynamicsProcessing?.enabled = false }
        }

        fun release() {
            runCatching { loudnessEnhancer?.release() }
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
            runCatching { dynamicsProcessing?.release() }
            loudnessEnhancer = null
            equalizer = null
            bassBoost = null
            dynamicsProcessing = null
        }

        private fun isPresenceFrequency(freqHz: Int): Boolean {
            val centers = intArrayOf(1000, 2000, 3000, 4000, 6000)
            return centers.any { abs(freqHz - it) <= 900 }
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
