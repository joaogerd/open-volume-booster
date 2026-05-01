package br.com.joaogerd.openvolumebooster.audio

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

object BoostGainModel {
    const val MIN_PERCENT = 0
    const val MAX_PERCENT = 100

    fun compute(requestedPercent: Int, systemVolumePercent: Int): BoostProfile {
        val percent = requestedPercent.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val volume = systemVolumePercent.coerceIn(0, 100)
        if (percent == 0) return BoostProfile(percent, volume, 0, 0f, BoostRisk.OFF)

        val maxGainMb = when {
            volume >= 95 -> 650
            volume >= 85 -> 750
            volume >= 70 -> 850
            else -> 950
        }
        val shaped = smoothStep(percent / 100f)
        val requestedGainMb = (shaped * maxGainMb).roundToInt()
        val reduction = when {
            percent >= 90 && volume >= 90 -> 180
            percent >= 80 && volume >= 85 -> 120
            percent >= 70 && volume >= 95 -> 100
            else -> 0
        }
        val targetGainMb = (requestedGainMb - reduction).coerceIn(0, maxGainMb)
        val headroomDb = ((maxGainMb - targetGainMb) / 100f).coerceAtLeast(0f)
        val risk = when {
            targetGainMb == 0 -> BoostRisk.OFF
            percent >= 80 || volume >= 90 || targetGainMb >= 700 -> BoostRisk.HIGH
            percent >= 45 || volume >= 75 || targetGainMb >= 400 -> BoostRisk.MODERATE
            else -> BoostRisk.SAFE
        }
        return BoostProfile(percent, volume, targetGainMb, headroomDb, risk)
    }

    fun dbToLinear(db: Double): Double = 10.0.pow(db / 20.0)

    fun linearToDb(linearGain: Double): Double {
        require(linearGain > 0.0) { "linearGain must be positive" }
        return 20.0 * log10(linearGain)
    }

    private fun smoothStep(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}

data class BoostProfile(
    val requestedPercent: Int,
    val systemVolumePercent: Int,
    val targetGainMb: Int,
    val headroomDb: Float,
    val risk: BoostRisk
) {
    val targetGainDb: Float get() = targetGainMb / 100f
    val message: String get() = when (risk) {
        BoostRisk.OFF -> "Boost desligado"
        BoostRisk.SAFE -> "Boost seguro: $requestedPercent%, ganho=${targetGainDb}dB"
        BoostRisk.MODERATE -> "Boost moderado: $requestedPercent%, ganho=${targetGainDb}dB"
        BoostRisk.HIGH -> "Boost alto: $requestedPercent%, ganho=${targetGainDb}dB"
    }
}

enum class BoostRisk { OFF, SAFE, MODERATE, HIGH }
