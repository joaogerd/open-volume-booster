package br.com.joaogerd.openvolumebooster

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.joaogerd.openvolumebooster.audio.AudioBoostService
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var audioManager: AudioManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audioManager = getSystemService(AudioManager::class.java)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BoosterScreen(
                        initialVolume = currentMusicVolumePercent(),
                        onVolumeChanged = { setMusicVolumePercent(it) },
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
        if (running) {
            startService(intent)
        } else {
            stopService(intent)
        }
    }
}

@Composable
private fun BoosterScreen(
    initialVolume: Float,
    onVolumeChanged: (Float) -> Unit,
    onServiceChanged: (Boolean, Float) -> Unit
) {
    var running by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(initialVolume.coerceIn(0f, 100f)) }
    var boost by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                modifier = Modifier.weight(1f).height(58.dp),
                onClick = {
                    running = !running
                    onServiceChanged(running, boost)
                }
            ) {
                Text(if (running) "PARAR SERVICO" else "INICIAR SERVICO", fontSize = 18.sp)
            }
        }

        ControlSlider(
            label = "Vol.",
            value = volume,
            onValueChange = {
                volume = it
                onVolumeChanged(it)
            }
        )

        ControlSlider(
            label = "Boost",
            value = boost,
            onValueChange = {
                boost = it.coerceIn(0f, 100f)
                if (running) onServiceChanged(true, boost)
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        WarningCard(boost = boost)
    }
}

@Composable
private fun ControlSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "$label: ${value.roundToInt()}%",
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f
        )
    }
}

@Composable
private fun WarningCard(boost: Float) {
    val extra = if (boost > 40f) {
        "\n\nBoost acima de 40% pode causar distorcao com mais facilidade."
    } else {
        ""
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            modifier = Modifier.padding(18.dp),
            text = "Advertencia: volumes altos podem danificar alto-falantes e audicao. Use por sua conta e risco.$extra",
            fontSize = 18.sp,
            lineHeight = 25.sp
        )
    }
}
