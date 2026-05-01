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

        val maxPerceptualGainDb = when {
            volume >= 95 -> 17.0f
            volume >= 85 -> 20.0f
            volume >= 70 -> 23.0f
            else -> 25.0f
        }
        val shaped = smoothStep(percent / 100f)
        val perceptualGainDb = applyHighVolumeProtection(shaped * maxPerceptualGainDb, percent, volume)
        val loudnessGainMb = (perceptualGainDb * 100f).toInt().coerceIn(0, 2500)
        val dynamicsInputGainDb = (perceptualGainDb * 0.55f).coerceIn(0f, 12f)
        val limiterThresholdDb = when {
            volume >= 90 -> -6.0f
            percent >= 80 -> -5.0f
            else -> -3.0f
        }
        val risk = when {
            perceptualGainDb <= 0.1f -> BoostRisk.OFF
            percent >= 80 || volume >= 90 || perceptualGainDb >= 16.0f -> BoostRisk.HIGH
            percent >= 45 || volume >= 75 || perceptualGainDb >= 8.0f -> BoostRisk.MODERATE
            else -> BoostRisk.SAFE
        }

        return BoostProfile(
            requestedPercent = percent,
            systemVolumePercent = volume,
            inputGainDb = round1(dynamicsInputGainDb),
            loudnessGainMb = loudnessGainMb,
            limiterThresholdDb = limiterThresholdDb,
            limiterPostGainDb = 0f,
            headroomDb = -limiterThresholdDb,
            risk = risk,
            perceptualGainDb = round1(perceptualGainDb)
        )
    }

    fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    fun linearToDb(linearGain: Double): Double {
        require(linearGain > 0.0) { "linearGain must be positive" }
        return 20.0 * log10(linearGain)
    }

    private fun applyHighVolumeProtection(gainDb: Float, percent: Int, volume: Int): Float {
        val reduction = when {
            percent >= 95 && volume >= 95 -> 3.0f
            percent >= 90 && volume >= 90 -> 2.0f
            percent >= 80 && volume >= 85 -> 1.0f
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
    val loudnessGainMb: Int,
    val limiterThresholdDb: Float,
    val limiterPostGainDb: Float,
    val headroomDb: Float,
    val risk: BoostRisk,
    val perceptualGainDb: Float
) {
    val fallbackLoudnessGainMb: Int get() = loudnessGainMb
    val targetGainMb: Int get() = loudnessGainMb
    val targetGainDb: Float get() = perceptualGainDb
    val message: String get() = when (risk) {
        BoostRisk.OFF -> "Boost desligado"
        BoostRisk.SAFE -> "Boost seguro: $requestedPercent%, ganho=${perceptualGainDb}dB"
        BoostRisk.MODERATE -> "Boost moderado: $requestedPercent%, ganho=${perceptualGainDb}dB"
        BoostRisk.HIGH -> "Boost alto: $requestedPercent%, ganho=${perceptualGainDb}dB"
    }

    companion object {
        fun off(volume: Int) = BoostProfile(
            requestedPercent = 0,
            systemVolumePercent = volume,
            inputGainDb = 0f,
            loudnessGainMb = 0,
            limiterThresholdDb = 0f,
            limiterPostGainDb = 0f,
            headroomDb = 0f,
            risk = BoostRisk.OFF,
            perceptualGainDb = 0f
        )
    }
}

enum class BoostRisk { OFF, SAFE, MODERATE, HIGH }
