package br.com.joaogerd.openvolumebooster.audio

import kotlin.math.log10
import kotlin.math.pow

object BoostGainModel {
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 100

    fun compute(requestedPercent: Int, systemVolumePercent: Int): BoostProfile {
        val percent = requestedPercent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val volume = systemVolumePercent.coerceIn(0, 100)
        if (percent == 0) return BoostProfile.off(volume)

        val maxInputGainDb = when {
            volume >= 95 -> 10.5f
            volume >= 85 -> 12.0f
            volume >= 70 -> 14.0f
            else -> 16.0f
        }
        val shaped = smoothStep(percent / 100f)
        val inputGainDb = shaped * maxInputGainDb
        val protectedInputGainDb = applyHighVolumeProtection(inputGainDb, percent, volume)
        val limiterThresholdDb = when {
            volume >= 90 -> -4.0f
            percent >= 80 -> -3.0f
            else -> -2.0f
        }
        val fallbackLoudnessGainDb = (protectedInputGainDb * 0.72f).coerceIn(0f, 11.5f)
        val risk = when {
            protectedInputGainDb <= 0.1f -> BoostRisk.OFF
            percent >= 80 || volume >= 90 || protectedInputGainDb >= 11.5f -> BoostRisk.HIGH
            percent >= 45 || volume >= 75 || protectedInputGainDb >= 6.0f -> BoostRisk.MODERATE
            else -> BoostRisk.SAFE
        }

        return BoostProfile(
            requestedPercent = percent,
            systemVolumePercent = volume,
            inputGainDb = round1(protectedInputGainDb),
            fallbackLoudnessGainMb = (fallbackLoudnessGainDb * 100f).toInt(),
            limiterThresholdDb = limiterThresholdDb,
            limiterPostGainDb = 0f,
            headroomDb = -limiterThresholdDb,
            risk = risk
        )
    }

    fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    fun linearToDb(linearGain: Double): Double {
        require(linearGain > 0.0) { "linearGain must be positive" }
        return 20.0 * log10(linearGain)
    }

    private fun applyHighVolumeProtection(gainDb: Float, percent: Int, volume: Int): Float {
        val reduction = when {
            percent >= 90 && volume >= 90 -> 2.4f
            percent >= 80 && volume >= 85 -> 1.6f
            percent >= 70 && volume >= 95 -> 1.2f
            else -> 0f
        }
        return (gainDb - reduction).coerceAtLeast(0f)
    }

    private fun smoothStep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun round1(value: Float): Float = (value * 10f).toInt() / 10f
}

data class BoostProfile(
    val requestedPercent: Int,
    val systemVolumePercent: Int,
    val inputGainDb: Float,
    val fallbackLoudnessGainMb: Int,
    val limiterThresholdDb: Float,
    val limiterPostGainDb: Float,
    val headroomDb: Float,
    val risk: BoostRisk
) {
    val targetGainMb: Int get() = fallbackLoudnessGainMb
    val targetGainDb: Float get() = inputGainDb
    val message: String get() = when (risk) {
        BoostRisk.OFF -> "Boost desligado"
        BoostRisk.SAFE -> "Boost seguro: $requestedPercent%, ganho=${inputGainDb}dB"
        BoostRisk.MODERATE -> "Boost moderado: $requestedPercent%, ganho=${inputGainDb}dB"
        BoostRisk.HIGH -> "Boost alto protegido: $requestedPercent%, ganho=${inputGainDb}dB"
    }

    companion object {
        fun off(volume: Int) = BoostProfile(
            requestedPercent = 0,
            systemVolumePercent = volume,
            inputGainDb = 0f,
            fallbackLoudnessGainMb = 0,
            limiterThresholdDb = 0f,
            limiterPostGainDb = 0f,
            headroomDb = 0f,
            risk = BoostRisk.OFF
        )
    }
}

enum class BoostRisk { OFF, SAFE, MODERATE, HIGH }
