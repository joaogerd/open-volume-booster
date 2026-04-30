package br.com.joaogerd.openvolumebooster.audio

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AudioBoostService : Service() {
    private val controller = AudioBoostController()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val boostPercent = intent?.getIntExtra(EXTRA_BOOST_PERCENT, 0) ?: 0
        val gainMb = percentToGain(boostPercent)
        controller.enable(gainMb)
        return START_STICKY
    }

    override fun onDestroy() {
        controller.disable()
        controller.release()
        super.onDestroy()
    }

    private fun percentToGain(percent: Int): Int {
        return ((percent.coerceIn(0, 100) / 100f) * AudioBoostController.MAX_GAIN_MB).toInt()
    }

    companion object {
        const val EXTRA_BOOST_PERCENT = "extra_boost_percent"
    }
}
