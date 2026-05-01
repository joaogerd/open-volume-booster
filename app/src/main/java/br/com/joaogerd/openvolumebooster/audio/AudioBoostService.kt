package br.com.joaogerd.openvolumebooster.audio

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.IBinder
import kotlin.math.roundToInt

class AudioBoostService : Service() {
    private val controller = AudioBoostController()
    private lateinit var audioManager: AudioManager
    private var boostPercent: Int = 0

    private val audioSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    val sessionId = intent.getIntExtra(
                        AudioEffect.EXTRA_AUDIO_SESSION,
                        AudioBoostController.GLOBAL_AUDIO_SESSION
                    )
                    if (sessionId != AudioBoostController.GLOBAL_AUDIO_SESSION) {
                        controller.enable(boostPercent, sessionId, currentMusicVolumePercent())
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
        audioManager = getSystemService(AudioManager::class.java)
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
        boostPercent = intent?.getIntExtra(EXTRA_BOOST_PERCENT, boostPercent) ?: boostPercent
        boostPercent = boostPercent.coerceIn(AudioBoostController.MIN_PERCENT, AudioBoostController.MAX_PERCENT)
        controller.update(boostPercent, currentMusicVolumePercent())
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(audioSessionReceiver) }
        controller.disable()
        controller.release()
        super.onDestroy()
    }

    private fun currentMusicVolumePercent(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((current.toFloat() / max.toFloat()) * 100f).roundToInt().coerceIn(0, 100)
    }

    companion object {
        const val EXTRA_BOOST_PERCENT = "extra_boost_percent"
    }
}
