package br.com.joaogerd.openvolumebooster.audio

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AudioBoostService : Service() {
    private val controller = AudioBoostController()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val boostPercent = intent?.getIntExtra(EXTRA_BOOST_PERCENT, 0) ?: 0
        controller.enable(boostPercent)
        return START_STICKY
    }

    override fun onDestroy() {
        controller.disable()
        controller.release()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_BOOST_PERCENT = "extra_boost_percent"
    }
}
