package br.com.joaogerd.openvolumebooster.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoostGainModelTest {
    @Test
    fun zeroBoostProducesNoGain() {
        val profile = BoostGainModel.compute(0, 80)
        assertEquals(0, profile.targetGainMb)
        assertEquals(BoostRisk.OFF, profile.risk)
    }

    @Test
    fun gainIsClampedToSafeRange() {
        val profile = BoostGainModel.compute(200, 100)
        assertTrue(profile.targetGainMb <= 650)
        assertEquals(BoostRisk.HIGH, profile.risk)
    }

    @Test
    fun highSystemVolumeReducesMaximumGain() {
        val lowVolume = BoostGainModel.compute(100, 50)
        val highVolume = BoostGainModel.compute(100, 100)
        assertTrue(highVolume.targetGainMb < lowVolume.targetGainMb)
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
}
