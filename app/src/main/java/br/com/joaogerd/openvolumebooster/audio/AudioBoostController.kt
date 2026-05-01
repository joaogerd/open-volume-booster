package br.com.joaogerd.openvolumebooster.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer

/**
 * Controller for Android audio effects.
 *
 * Android does not guarantee that session 0 affects every third-party player.
 * Some devices allow it, others ignore it. For that reason this controller
 * applies more than one standard effect and reports a readable status.
 */
class AudioBoostController {
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    fun enable(boostPercent: Int): BoostState {
        val normalizedBoost = boostPercent.coerceIn(0, 100)
        val messages = mutableListOf<String>()
        var enabledEffects = 0

        runCatching {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(GLOBAL_AUDIO_SESSION)
            }
            loudnessEnhancer?.setTargetGain(percentToGainMb(normalizedBoost))
            loudnessEnhancer?.enabled = true
        }.onSuccess {
            enabledEffects += 1
            messages += "LoudnessEnhancer"
        }.onFailure { error ->
            messages += "LoudnessEnhancer failed: ${error.message ?: "unsupported"}"
        }

        runCatching {
            if (equalizer == null) {
                equalizer = Equalizer(0, GLOBAL_AUDIO_SESSION)
            }
            applyEqualizerBoost(normalizedBoost)
            equalizer?.enabled = true
        }.onSuccess {
            enabledEffects += 1
            messages += "Equalizer"
        }.onFailure { error ->
            messages += "Equalizer failed: ${error.message ?: "unsupported"}"
        }

        runCatching {
            if (bassBoost == null) {
                bassBoost = BassBoost(0, GLOBAL_AUDIO_SESSION)
            }
            bassBoost?.setStrength(percentToBassStrength(normalizedBoost))
            bassBoost?.enabled = true
        }.onSuccess {
            enabledEffects += 1
            messages += "BassBoost"
        }.onFailure { error ->
            messages += "BassBoost failed: ${error.message ?: "unsupported"}"
        }

        return if (enabledEffects > 0) {
            BoostState.Enabled("Active effects: ${messages.joinToString()}")
        } else {
            release()
            BoostState.Error(messages.joinToString(separator = "\n"))
        }
    }

    fun update(boostPercent: Int): BoostState {
        val normalizedBoost = boostPercent.coerceIn(0, 100)
        return enable(normalizedBoost)
    }

    fun disable(): BoostState {
        runCatching { loudnessEnhancer?.enabled = false }
        runCatching { equalizer?.enabled = false }
        runCatching { bassBoost?.enabled = false }
        return BoostState.Disabled("Boost disabled")
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

    companion object {
        private const val GLOBAL_AUDIO_SESSION = 0
        const val MAX_GAIN_MB = 3000
        private const val MAX_BASS_STRENGTH = 1000
    }
}

sealed interface BoostState {
    val message: String

    data class Enabled(override val message: String) : BoostState
    data class Disabled(override val message: String) : BoostState
    data class Error(override val message: String) : BoostState
}
