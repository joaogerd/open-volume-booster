package br.com.joaogerd.openvolumebooster

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.joaogerd.openvolumebooster.audio.AudioBoostService
import br.com.joaogerd.openvolumebooster.audio.BoostGainModel
import br.com.joaogerd.openvolumebooster.audio.BoostRisk
import br.com.joaogerd.openvolumebooster.audio.InternalAudioPlayer
import br.com.joaogerd.openvolumebooster.audio.PlayerState
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var audioManager: AudioManager
    private val prefs by lazy { getSharedPreferences("booster", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoosterScreen(
                        context = this,
                        initialVolume = currentMusicVolumePercent(),
                        initialBoost = prefs.getInt(KEY_BOOST_PERCENT, 0).toFloat(),
                        onVolumeChanged = { setMusicVolumePercent(it) },
                        onBoostChanged = { prefs.edit().putInt(KEY_BOOST_PERCENT, it.roundToInt()).apply() },
                        onServiceChanged = { running, boost -> updateBoostService(running, boost) }
                    )
                }
            }
        }
    }

    private fun currentMusicVolumePercent(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return (current.toFloat() / max.toFloat()) * 100f
    }

    private fun setMusicVolumePercent(percent: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val index = ((percent.coerceIn(0f, 100f) / 100f) * max).roundToInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, index, AudioManager.FLAG_SHOW_UI)
    }

    private fun updateBoostService(running: Boolean, boost: Float) {
        val intent = Intent(this, AudioBoostService::class.java).apply {
            putExtra(AudioBoostService.EXTRA_BOOST_PERCENT, boost.roundToInt())
        }
        if (running) startService(intent) else stopService(intent)
    }

    private companion object {
        const val KEY_BOOST_PERCENT = "boost_percent"
    }
}

@Composable
private fun BoosterScreen(
    context: Context,
    initialVolume: Float,
    initialBoost: Float,
    onVolumeChanged: (Float) -> Unit,
    onBoostChanged: (Float) -> Unit,
    onServiceChanged: (Boolean, Float) -> Unit
) {
    val internalPlayer = remember { InternalAudioPlayer(context.applicationContext) }
    DisposableEffect(Unit) {
        onDispose { internalPlayer.release() }
    }

    var running by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(initialVolume.coerceIn(0f, 100f)) }
    var boost by remember { mutableFloatStateOf(initialBoost.coerceIn(0f, 100f)) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var playerStatus by remember { mutableStateOf("Nenhum audio carregado no player interno.") }
    var playerMode by remember { mutableStateOf(false) }
    val profile = BoostGainModel.compute(boost.roundToInt(), volume.roundToInt())

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedUri = uri
            playerMode = true
            playerStatus = when (val state = internalPlayer.load(uri, boost.roundToInt(), volume.roundToInt())) {
                is PlayerState.Ready -> state.message
                is PlayerState.Error -> state.message
                else -> state.message
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            modifier = Modifier.fillMaxWidth(),
            text = "Open Volume Booster",
            textAlign = TextAlign.Center,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        StatusCard(running = running || playerMode, risk = profile.risk, gainDb = profile.targetGainDb)

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Player interno", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Use este modo para garantir booster real em fone ou Bluetooth. Ele processa a sessao do proprio app.", fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(modifier = Modifier.weight(1f), onClick = { audioPicker.launch("audio/*") }) {
                        Text("Escolher audio")
                    }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = selectedUri != null,
                        onClick = {
                            playerMode = true
                            onServiceChanged(false, boost)
                            running = false
                            playerStatus = internalPlayer.play().message
                        }
                    ) { Text("Play") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = selectedUri != null,
                        onClick = { playerStatus = internalPlayer.pause().message }
                    ) { Text("Pause") }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = selectedUri != null,
                        onClick = { playerStatus = internalPlayer.stop().message }
                    ) { Text("Stop") }
                }
                Text(playerStatus, fontSize = 14.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            PresetButton("Normal", 0f, Modifier.weight(1f)) { value ->
                boost = value
                onBoostChanged(value)
                internalPlayer.updateBoost(value.roundToInt(), volume.roundToInt())
                if (running) onServiceChanged(true, value)
            }
            PresetButton("Moderado", 45f, Modifier.weight(1f)) { value ->
                boost = value
                onBoostChanged(value)
                internalPlayer.updateBoost(value.roundToInt(), volume.roundToInt())
                if (running) onServiceChanged(true, value)
            }
            PresetButton("Alto", 75f, Modifier.weight(1f)) { value ->
                boost = value
                onBoostChanged(value)
                internalPlayer.updateBoost(value.roundToInt(), volume.roundToInt())
                if (running) onServiceChanged(true, value)
            }
        }

        Button(
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(),
            onClick = {
                running = !running
                playerMode = false
                onServiceChanged(running, boost)
            }
        ) {
            Text(if (running) "PARAR BOOSTER EXTERNO" else "INICIAR BOOSTER EXTERNO", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        ControlSlider(
            label = "Volume do sistema",
            value = volume,
            onValueChange = {
                volume = it
                onVolumeChanged(it)
                internalPlayer.updateBoost(boost.roundToInt(), volume.roundToInt())
                if (running) onServiceChanged(true, boost)
            }
        )

        ControlSlider(
            label = "Boost perceptual",
            value = boost,
            onValueChange = {
                boost = it.coerceIn(0f, 100f)
                onBoostChanged(boost)
                internalPlayer.updateBoost(boost.roundToInt(), volume.roundToInt())
                if (running) onServiceChanged(true, boost)
            }
        )

        Text(
            text = "Ganho: ${profile.targetGainDb} dB | Presenca: ${profile.presenceBoostMb} mB | Grave: ${profile.bassBoostStrength}",
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(2.dp))
        WarningCard(profile.risk)
    }
}

@Composable
private fun StatusCard(running: Boolean, risk: BoostRisk, gainDb: Float) {
    val status = if (running) "BOOSTER ATIVO" else "BOOSTER DESLIGADO"
    val riskText = when (risk) {
        BoostRisk.OFF -> "Sem reforco de ganho"
        BoostRisk.SAFE -> "Faixa segura"
        BoostRisk.MODERATE -> "Faixa moderada"
        BoostRisk.HIGH -> "Proximo da saturacao"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(status, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("$riskText | ${gainDb} dB", fontSize = 17.sp)
        }
    }
}

@Composable
private fun PresetButton(label: String, value: Float, modifier: Modifier, onClick: (Float) -> Unit) {
    Button(modifier = modifier, onClick = { onClick(value) }) {
        Text(label, fontSize = 14.sp)
    }
}

@Composable
private fun ControlSlider(label: String, value: Float, onValueChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = "$label: ${value.roundToInt()}%", fontSize = 19.sp, fontWeight = FontWeight.Medium)
        Slider(value = value, onValueChange = onValueChange, valueRange = 0f..100f)
    }
}

@Composable
private fun WarningCard(risk: BoostRisk) {
    val text = when (risk) {
        BoostRisk.OFF -> "O modo externo depende do player. O player interno aplica o booster na sessao real do audio."
        BoostRisk.SAFE -> "Use volumes confortaveis e evite exposicao prolongada."
        BoostRisk.MODERATE -> "Boost moderado pode aumentar distorcao dependendo do fone, Bluetooth ou alto-falante."
        BoostRisk.HIGH -> "Atencao: boost alto pode causar clipping, fadiga auditiva e dano a alto-falantes. Reduza se ouvir distorcao."
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Text(modifier = Modifier.padding(18.dp), text = text, fontSize = 16.sp, lineHeight = 23.sp)
    }
}
