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
            volume >= 95 -> 22.0f
            volume >= 85 -> 26.0f
            volume >= 70 -> 30.0f
            else -> 34.0f
        }
        val shaped = perceptualCurve(percent / 100f)
        val perceptualGainDb = applyHighVolumeProtection(shaped * maxPerceptualGainDb, percent, volume)
        val loudnessGainMb = (perceptualGainDb * 100f).toInt().coerceIn(0, 3400)
        val presenceBoostMb = computePresenceBoostMb(percent, volume)
        val bassBoostStrength = computeBassStrength(percent, volume)
        val dynamicsInputGainDb = (perceptualGainDb * 0.35f).coerceIn(0f, 10f)
        val limiterThresholdDb = when {
            volume >= 90 -> -7.0f
            percent >= 80 -> -6.0f
            else -> -4.0f
        }
        val risk = when {
            perceptualGainDb <= 0.1f -> BoostRisk.OFF
            percent >= 75 || volume >= 90 || perceptualGainDb >= 18.0f -> BoostRisk.HIGH
            percent >= 35 || volume >= 75 || perceptualGainDb >= 9.0f -> BoostRisk.MODERATE
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
            perceptualGainDb = round1(perceptualGainDb),
            presenceBoostMb = presenceBoostMb,
            bassBoostStrength = bassBoostStrength
        )
    }

    fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    fun linearToDb(linearGain: Double): Double {
        require(linearGain > 0.0) { "linearGain must be positive" }
        return 20.0 * log10(linearGain)
    }

    private fun applyHighVolumeProtection(gainDb: Float, percent: Int, volume: Int): Float {
        val reduction = when {
            percent >= 95 && volume >= 95 -> 4.0f
            percent >= 90 && volume >= 90 -> 2.5f
            percent >= 80 && volume >= 85 -> 1.2f
            else -> 0f
        }
        return (gainDb - reduction).coerceAtLeast(0f)
    }

    private fun computePresenceBoostMb(percent: Int, volume: Int): Int {
        val base = when {
            percent >= 80 -> 520
            percent >= 55 -> 360
            percent >= 30 -> 220
            else -> 120
        }
        val reduction = if (volume >= 90) 120 else 0
        return (base - reduction).coerceIn(0, 600)
    }

    private fun computeBassStrength(percent: Int, volume: Int): Short {
        val strength = when {
            percent >= 80 -> 220
            percent >= 55 -> 150
            percent >= 30 -> 80
            else -> 0
        }
        val reduction = if (volume >= 90) 50 else 0
        return (strength - reduction).coerceIn(0, 250).toShort()
    }

    private fun perceptualCurve(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * (1.35f - 0.35f * t)
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
    val perceptualGainDb: Float,
    val presenceBoostMb: Int,
    val bassBoostStrength: Short
) {
    val fallbackLoudnessGainMb: Int get() = loudnessGainMb
    val targetGainMb: Int get() = loudnessGainMb
    val targetGainDb: Float get() = perceptualGainDb
    val message: String get() = when (risk) {
        BoostRisk.OFF -> "Boost desligado"
        BoostRisk.SAFE -> "Boost perceptual seguro: $requestedPercent%, ganho=${perceptualGainDb}dB"
        BoostRisk.MODERATE -> "Boost perceptual moderado: $requestedPercent%, ganho=${perceptualGainDb}dB"
        BoostRisk.HIGH -> "Boost perceptual alto: $requestedPercent%, ganho=${perceptualGainDb}dB"
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
            perceptualGainDb = 0f,
            presenceBoostMb = 0,
            bassBoostStrength = 0
        )
    }
}

enum class BoostRisk { OFF, SAFE, MODERATE, HIGH }
