package br.com.joaogerd.openvolumebooster.audio

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent

class AudioBoostService : Service() {
    private val controller = AudioBoostController()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private var boostLevel: Int = AudioBoostController.DEFAULT_LEVEL
    private var restartScheduled = false

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
        boostLevel = intent?.getIntExtra(EXTRA_BOOST_PERCENT, boostLevel) ?: boostLevel
        controller.update(boostLevel)
        schedulePlaybackSessionRefresh()
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(audioSessionReceiver) }
        handler.removeCallbacksAndMessages(null)
        controller.disable()
        controller.release()
        super.onDestroy()
    }

    private fun schedulePlaybackSessionRefresh() {
        if (restartScheduled) return
        restartScheduled = true
        handler.postDelayed({
            restartScheduled = false
            refreshPlaybackSession()
        }, 350)
    }

    private fun refreshPlaybackSession() {
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
        dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    private fun dispatchMediaKey(keyCode: Int) {
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    companion object {
        const val EXTRA_BOOST_PERCENT = "extra_boost_percent"
    }
}
