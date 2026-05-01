package br.com.joaogerd.openvolumebooster.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class InternalAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val boostController = AudioBoostController()
    private var currentUri: Uri? = null
    private var currentBoostPercent: Int = 0
    private var currentVolumePercent: Int = 100

    val isPrepared: Boolean
        get() = mediaPlayer != null

    val isPlaying: Boolean
        get() = runCatching { mediaPlayer?.isPlaying == true }.getOrDefault(false)

    fun load(uri: Uri, boostPercent: Int, volumePercent: Int): PlayerState {
        releasePlayerOnly()
        currentUri = uri
        currentBoostPercent = boostPercent.coerceIn(AudioBoostController.MIN_PERCENT, AudioBoostController.MAX_PERCENT)
        currentVolumePercent = volumePercent.coerceIn(0, 100)

        return try {
            val player = MediaPlayer().apply {
                setDataSource(context, uri)
                setOnCompletionListener { pauseBoost() }
                prepare()
            }
            mediaPlayer = player
            applyBoost()
            PlayerState.Ready("Audio carregado. Sessao: ${player.audioSessionId}")
        } catch (exception: RuntimeException) {
            release()
            PlayerState.Error("Falha ao carregar audio: ${exception.message ?: "formato nao suportado"}")
        }
    }

    fun play(): PlayerState {
        val player = mediaPlayer ?: return PlayerState.Error("Nenhum audio carregado")
        return try {
            applyBoost()
            player.start()
            PlayerState.Playing("Reproduzindo com booster interno")
        } catch (exception: RuntimeException) {
            PlayerState.Error("Falha ao reproduzir: ${exception.message ?: "erro desconhecido"}")
        }
    }

    fun pause(): PlayerState {
        val player = mediaPlayer ?: return PlayerState.Error("Nenhum audio carregado")
        return try {
            if (player.isPlaying) player.pause()
            PlayerState.Paused("Pausado")
        } catch (exception: RuntimeException) {
            PlayerState.Error("Falha ao pausar: ${exception.message ?: "erro desconhecido"}")
        }
    }

    fun stop(): PlayerState {
        val uri = currentUri ?: return PlayerState.Error("Nenhum audio carregado")
        return try {
            releasePlayerOnly()
            load(uri, currentBoostPercent, currentVolumePercent)
            PlayerState.Stopped("Parado")
        } catch (exception: RuntimeException) {
            PlayerState.Error("Falha ao parar: ${exception.message ?: "erro desconhecido"}")
        }
    }

    fun updateBoost(boostPercent: Int, volumePercent: Int): PlayerState {
        currentBoostPercent = boostPercent.coerceIn(AudioBoostController.MIN_PERCENT, AudioBoostController.MAX_PERCENT)
        currentVolumePercent = volumePercent.coerceIn(0, 100)
        return applyBoost()
    }

    fun release() {
        releasePlayerOnly()
        boostController.release()
    }

    private fun applyBoost(): PlayerState {
        val player = mediaPlayer ?: return PlayerState.Error("Nenhum audio carregado")
        return when (val state = boostController.enable(currentBoostPercent, player.audioSessionId, currentVolumePercent)) {
            is BoostState.Enabled -> PlayerState.Ready("Booster aplicado: ${state.message}")
            is BoostState.Disabled -> PlayerState.Ready(state.message)
            is BoostState.Error -> PlayerState.Error(state.message)
        }
    }

    private fun pauseBoost() {
        boostController.disable()
    }

    private fun releasePlayerOnly() {
        runCatching { mediaPlayer?.stop() }
        runCatching { mediaPlayer?.release() }
        mediaPlayer = null
        boostController.disable()
    }
}

sealed interface PlayerState {
    val message: String

    data class Ready(override val message: String) : PlayerState
    data class Playing(override val message: String) : PlayerState
    data class Paused(override val message: String) : PlayerState
    data class Stopped(override val message: String) : PlayerState
    data class Error(override val message: String) : PlayerState
}
