package br.com.joaogerd.openvolumebooster.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import kotlin.math.log10

/**
 * Controller for Android audio effects.
 *
 * The booster level follows the same idea used by many volume booster apps:
 * 100 means normal level, 150 means about 1.5x, 175 means about 1.75x.
 * Internally this is converted to dB/mB using 20 * log10(multiplier), which
 * avoids the aggressive clipping caused by linear gain mapping.
 */
class AudioBoostController {
    private val chains = linkedMapOf<Int, EffectChain>()
    private var boostLevel: Int = DEFAULT_LEVEL

    fun enable(level: Int, sessionId: Int = GLOBAL_AUDIO_SESSION): BoostState {
        boostLevel = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        val chain = chains.getOrPut(sessionId) { EffectChain(sessionId) }
        return chain.enable(boostLevel)
    }

    fun update(level: Int): BoostState {
        boostLevel = level.coerceIn(MIN_LEVEL, MAX_LEVEL)
        if (chains.isEmpty()) {
            return enable(boostLevel, GLOBAL_AUDIO_SESSION)
        }

        val states = chains.map { (_, chain) -> chain.enable(boostLevel) }
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

        fun enable(level: Int): BoostState {
            if (level <= 0) {
                disable()
                return BoostState.Disabled("session $sessionId: muted booster level")
            }

            val messages = mutableListOf<String>()
            var enabledEffects = 0

            runCatching {
                if (loudnessEnhancer == null) {
                    loudnessEnhancer = LoudnessEnhancer(sessionId)
                }
                loudnessEnhancer?.setTargetGain(levelToGainMb(level))
                loudnessEnhancer?.enabled = level > DEFAULT_LEVEL
            }.onSuccess {
                enabledEffects += 1
                messages += "session $sessionId: loudness=${levelToGainMb(level)}mB"
            }.onFailure { error ->
                messages += "session $sessionId: loudness failed: ${error.message ?: "unsupported"}"
            }

            runCatching {
                if (equalizer == null) {
                    equalizer = Equalizer(0, sessionId)
                }
                applyFlatEqualizer()
                equalizer?.enabled = false
            }.onSuccess {
                messages += "equalizer=flat"
            }.onFailure { error ->
                messages += "equalizer failed: ${error.message ?: "unsupported"}"
            }

            runCatching {
                if (bassBoost == null) {
                    bassBoost = BassBoost(0, sessionId)
                }
                bassBoost?.setStrength(levelToBassStrength(level))
                bassBoost?.enabled = levelToBassStrength(level) > 0
            }.onSuccess {
                enabledEffects += 1
                messages += "bass=${levelToBassStrength(level)}"
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

        private fun applyFlatEqualizer() {
            val currentEqualizer = equalizer ?: return
            val bandRange = currentEqualizer.bandLevelRange
            val flatLevel = 0.coerceIn(bandRange[0].toInt(), bandRange[1].toInt()).toShort()
            for (band in 0 until currentEqualizer.numberOfBands) {
                currentEqualizer.setBandLevel(band.toShort(), flatLevel)
            }
        }

        private fun levelToGainMb(level: Int): Int {
            if (level <= DEFAULT_LEVEL) return 0
            val multiplier = level / DEFAULT_LEVEL.toFloat()
            val db = 20.0 * log10(multiplier.toDouble())
            return (db * 100.0).toInt().coerceIn(0, MAX_GAIN_MB)
        }

        private fun levelToBassStrength(level: Int): Short {
            if (level <= DEFAULT_LEVEL) return 0
            val normalized = (level - DEFAULT_LEVEL).toFloat() / (MAX_LEVEL - DEFAULT_LEVEL).toFloat()
            return (normalized * MAX_BASS_STRENGTH).toInt().coerceIn(0, MAX_BASS_STRENGTH).toShort()
        }
    }

    companion object {
        const val GLOBAL_AUDIO_SESSION = 0
        const val MIN_LEVEL = 0
        const val DEFAULT_LEVEL = 100
        const val MAX_LEVEL = 200
        private const val MAX_GAIN_MB = 700
        private const val MAX_BASS_STRENGTH = 250
    }
}

sealed interface BoostState {
    val message: String

    data class Enabled(override val message: String) : BoostState
    data class Disabled(override val message: String) : BoostState
    data class Error(override val message: String) : BoostState
}
