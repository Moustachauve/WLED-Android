package ca.cgagnier.wlednativeandroid.ui.homeScreen.audio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.service.audio.AudioData

@Composable
fun AudioSyncScreen(
    navigateUp: () -> Unit,
    viewModel: AudioSyncViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isRunning by viewModel.isRunning.collectAsStateWithLifecycle()
    val audioData by viewModel.currentAudioData.collectAsStateWithLifecycle()
    val gain by viewModel.gain.collectAsStateWithLifecycle()
    val targetAddress by viewModel.targetAddress.collectAsStateWithLifecycle()
    val targetPort by viewModel.targetPort.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        hasAudioPermission = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (hasAudioPermission) {
            viewModel.startAudioSync()
        }
    }

    DisposableEffect(Unit) {
        viewModel.bindService()
        onDispose {
            viewModel.unbindService()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audio Sync") },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go Back",
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Status card
            StatusCard(isRunning = isRunning, audioData = audioData)

            // Visualizer
            if (isRunning && audioData != null) {
                FrequencyVisualizer(audioData = audioData!!)
            }

            // Target settings
            TargetSettings(
                targetAddress = targetAddress,
                targetPort = targetPort,
                enabled = !isRunning,
                onAddressChange = { viewModel.updateTargetAddress(it) },
                onPortChange = { viewModel.updateTargetPort(it) },
            )

            // Gain control
            GainControl(gain = gain, onGainChange = { viewModel.updateGain(it) })

            // Start/Stop button
            StartStopButton(
                isRunning = isRunning,
                onStart = {
                    if (hasAudioPermission) {
                        viewModel.startAudioSync()
                    } else {
                        val permissions = buildList {
                            add(Manifest.permission.RECORD_AUDIO)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    }
                },
                onStop = { viewModel.stopAudioSync() },
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(isRunning: Boolean, audioData: AudioData?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = if (isRunning) "Streaming" else "Idle",
                style = MaterialTheme.typography.titleMedium,
                color = if (isRunning) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (isRunning && audioData != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Volume: %.0f  |  Peak: %s  |  Freq: %.0f Hz".format(
                        audioData.sampleSmth,
                        if (audioData.samplePeak > 0) "YES" else "no",
                        audioData.fftMajorPeak,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun FrequencyVisualizer(audioData: AudioData) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp),
    ) {
        val barCount = audioData.fftResult.size
        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (barCount - 1)
        val barWidth = (size.width - totalSpacing) / barCount

        for (i in 0 until barCount) {
            val value = audioData.fftResult[i] / 254.0f
            val barHeight = value * size.height
            val x = i * (barWidth + barSpacing)
            val color = lerp(primaryColor, tertiaryColor, i.toFloat() / (barCount - 1))

            drawRect(
                color = color,
                topLeft = Offset(x, size.height - barHeight),
                size = Size(barWidth, barHeight),
            )
        }
    }
}

private fun lerp(start: Color, end: Color, fraction: Float): Color {
    return Color(
        red = start.red + (end.red - start.red) * fraction,
        green = start.green + (end.green - start.green) * fraction,
        blue = start.blue + (end.blue - start.blue) * fraction,
        alpha = 1f,
    )
}

@Composable
private fun TargetSettings(
    targetAddress: String,
    targetPort: Int,
    enabled: Boolean,
    onAddressChange: (String) -> Unit,
    onPortChange: (Int) -> Unit,
) {
    var addressText by rememberSaveable { mutableStateOf(targetAddress) }
    var portText by rememberSaveable { mutableStateOf(targetPort.toString()) }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Target",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Use 239.0.0.1 for multicast (all devices), or a specific device IP",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = addressText,
                    onValueChange = {
                        addressText = it
                        onAddressChange(it)
                    },
                    label = { Text("IP Address") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = portText,
                    onValueChange = { text ->
                        portText = text
                        text.toIntOrNull()?.let { onPortChange(it) }
                    },
                    label = { Text("Port") },
                    singleLine = true,
                    enabled = enabled,
                    modifier = Modifier.width(100.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}

@Composable
private fun GainControl(gain: Float, onGainChange: (Float) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Gain",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "%.1fx".format(gain),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Slider(
                value = gain,
                onValueChange = onGainChange,
                valueRange = 0.1f..5.0f,
            )
        }
    }
}

@Composable
private fun StartStopButton(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Button(
        onClick = if (isRunning) onStop else onStart,
        modifier = Modifier.fillMaxWidth(),
        colors = if (isRunning) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        Text(
            text = if (isRunning) "Stop Audio Sync" else "Start Audio Sync",
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
