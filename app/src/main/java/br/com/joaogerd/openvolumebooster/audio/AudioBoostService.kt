package br.com.joaogerd.openvolumebooster.audio

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder

class AudioBoostService : Service() {
    private val controller = AudioBoostController()
    private var boostLevel: Int = AudioBoostController.DEFAULT_LEVEL

    private val audioSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    val sessionId = intent.getIntExtra(
                        AudioEffect.EXTRA_AUDIO_SESSION,
                        AudioBoostController.GLOBAL_AUDIO_SESSION
                    )
                    if (sessionId != AudioBoostController.GLOBAL_AUDIO_SESSION) {
                        controller.enable(boostLevel, sessionId)
                    }
                }

                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    val sessionId = intent.getIntExtra(
                        AudioEffect.EXTRA_AUDIO_SESSION,
                        AudioBoostController.GLOBAL_AUDIO_SESSION
                    )
                    controller.closeSession(sessionId)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(audioSessionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(audioSessionReceiver, filter)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val boostPercent = intent?.getIntExtra(EXTRA_BOOST_PERCENT, 0) ?: 0
        boostLevel = percentToLevel(boostPercent)
        controller.update(boostLevel)
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(audioSessionReceiver) }
        controller.disable()
        controller.release()
        super.onDestroy()
    }

    private fun percentToLevel(percent: Int): Int {
        val safePercent = percent.coerceIn(0, 100)
        return AudioBoostController.DEFAULT_LEVEL + safePercent
    }

    companion object {
        const val EXTRA_BOOST_PERCENT = "extra_boost_percent"
    }
}
