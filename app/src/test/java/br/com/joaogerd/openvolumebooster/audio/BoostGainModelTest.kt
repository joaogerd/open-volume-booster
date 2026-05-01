package br.com.joaogerd.openvolumebooster.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostGainModelTest {
    @Test
    fun zeroBoostProducesNoGain() {
        val profile = BoostGainModel.compute(0, 80)
        assertEquals(0f, profile.inputGainDb, 0.0f)
        assertEquals(0, profile.loudnessGainMb)
        assertEquals(BoostRisk.OFF, profile.risk)
    }

    @Test
    fun strongerGainIsAudibleButClampedAtFullSystemVolume() {
        val profile = BoostGainModel.compute(200, 100)
        assertTrue(profile.loudnessGainMb <= 2500)
        assertTrue(profile.perceptualGainDb <= 17.0f)
        assertEquals(BoostRisk.HIGH, profile.risk)
    }

    @Test
    fun highSystemVolumeReducesMaximumGain() {
        val lowVolume = BoostGainModel.compute(100, 50)
        val highVolume = BoostGainModel.compute(100, 100)
        assertTrue(highVolume.perceptualGainDb < lowVolume.perceptualGainDb)
        assertTrue(highVolume.loudnessGainMb < lowVolume.loudnessGainMb)
    }

    @Test
    fun dbAndLinearConversionsAreConsistent() {
        val linear = BoostGainModel.dbToLinear(6.0)
        val db = BoostGainModel.linearToDb(linear)
        assertEquals(6.0, db, 0.0001)
    }

    @Test
    fun moderatePresetUsesModerateRisk() {
        val profile = BoostGainModel.compute(45, 50)
        assertEquals(BoostRisk.MODERATE, profile.risk)
    }

    @Test
    fun highBoostUsesLimiterHeadroom() {
        val profile = BoostGainModel.compute(90, 90)
        assertTrue(profile.limiterThresholdDb < 0f)
        assertTrue(profile.headroomDb > 0f)
    }
}
