package br.com.joaogerd.openvolumebooster.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

/**
 * Controller for Android audio effects.
 *
 * The preferred path is attaching the effects to the real audio session exposed
 * by the media app. Session 0 is kept only as a fallback because many Android
 * devices ignore global effects for third-party players.
 */
class AudioBoostController {
    private val chains = linkedMapOf<Int, EffectChain>()
    private var boostPercent: Int = 0

    fun enable(percent: Int, sessionId: Int = GLOBAL_AUDIO_SESSION): BoostState {
        boostPercent = percent.coerceIn(0, 100)
        val chain = chains.getOrPut(sessionId) { EffectChain(sessionId) }
        return chain.enable(boostPercent)
    }

    fun update(percent: Int): BoostState {
        boostPercent = percent.coerceIn(0, 100)
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
        private var equalizer: Equalizer? = null
        private var bassBoost: BassBoost? = null

        fun enable(boostPercent: Int): BoostState {
            val messages = mutableListOf<String>()
            var enabledEffects = 0

            runCatching {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = LoudnessEnhancer(sessionId)
                }
                loudnessEnhancer?.setTargetGain(percentToGainMb(boostPercent))
                loudnessEnhancer?.enabled = true
            }.onSuccess {
                enabledEffects += 1
                messages += "session $sessionId: loudness"
            }.onFailure { error ->
                messages += "session $sessionId: loudness failed: ${error.message ?: "unsupported"}"
            }

            runCatching {
                if (equalizer == null) {
                    equalizer = Equalizer(0, sessionId)
                }
                applyEqualizerBoost(boostPercent)
                equalizer?.enabled = true
            }.onSuccess {
                enabledEffects += 1
                messages += "equalizer"
            }.onFailure { error ->
                messages += "equalizer failed: ${error.message ?: "unsupported"}"
            }

            runCatching {
                if (bassBoost == null) {
                    bassBoost = BassBoost(0, sessionId)
                }
                bassBoost?.setStrength(percentToBassStrength(boostPercent))
                bassBoost?.enabled = true
            }.onSuccess {
                enabledEffects += 1
                messages += "bass"
            }.onFailure { error ->
                messages += "bass failed: ${error.message ?: "unsupported"}"
            }

            return if (enabledEffects > 0) {
                BoostState.Enabled(messages.joinToString())
            } else {
                release()
                BoostState.Error(messages.joinToString(separator = "\n"))
            }
        }

        fun disable() {
            runCatching { loudnessEnhancer?.enabled = false }
            runCatching { equalizer?.enabled = false }
            runCatching { bassBoost?.enabled = false }
        }

        fun release() {
            runCatching { loudnessEnhancer?.release() }
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
            loudnessEnhancer = null
            equalizer = null
            bassBoost = null
        }

        private fun applyEqualizerBoost(boostPercent: Int) {
            val currentEqualizer = equalizer ?: return
            val bandRange = currentEqualizer.bandLevelRange
            val minLevel = bandRange[0]
            val maxLevel = bandRange[1]
            val targetLevel = ((boostPercent / 100f) * maxLevel).toInt().toShort()
            val safeLevel = targetLevel.coerceIn(minLevel, maxLevel).toShort()

            for (band in 0 until currentEqualizer.numberOfBands) {
                currentEqualizer.setBandLevel(band.toShort(), safeLevel)
            }
        }

        private fun percentToGainMb(percent: Int): Int {
            return ((percent / 100f) * MAX_GAIN_MB).toInt().coerceIn(0, MAX_GAIN_MB)
        }

        private fun percentToBassStrength(percent: Int): Short {
            return ((percent / 100f) * MAX_BASS_STRENGTH).toInt().coerceIn(0, MAX_BASS_STRENGTH).toShort()
        }
    }

    companion object {
        const val GLOBAL_AUDIO_SESSION = 0
        private const val MAX_GAIN_MB = 5000
        private const val MAX_BASS_STRENGTH = 1000
    }
}

sealed interface BoostState {
    val message: String

    data class Enabled(override val message: String) : BoostState
    data class Disabled(override val message: String) : BoostState
    data class Error(override val message: String) : BoostState
}
