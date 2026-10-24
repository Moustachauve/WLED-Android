package ca.cgagnier.wlednativeandroid.ui.soundsync

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.SoundSyncAudioSourceType
import org.koin.androidx.compose.koinViewModel

private const val CHART_HEIGHT_DP = 120
private const val BYTE_MASK = 0xFF
private const val CHANNEL_MAX_VALUE = 255f
private const val BAR_GAP_RATIO = 0.15f

@Composable
fun SoundSyncScreen(navigateUp: () -> Unit, viewModel: SoundSyncViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.refreshPermissionState()
        if (granted) {
            viewModel.start()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionState()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.sound_sync)) },
                navigationIcon = {
                    IconButton(onClick = navigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.description_back_button),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        SoundSyncContent(
            uiState = uiState,
            permissionLauncher = permissionLauncher,
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun SoundSyncContent(
    uiState: SoundSyncUiState,
    permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    viewModel: SoundSyncViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 6.dp)
                .widthIn(0.dp, 1100.dp),
        ) {
            DescriptionCard()
            SourceSelectionCard(
                sourceType = uiState.sourceType,
                isRunning = uiState.isRunning,
                onSourceSelected = { viewModel.setSourceType(it) },
            )
            GainCard(
                gain = uiState.gain,
                agcEnabled = uiState.agcEnabled,
                onGainChanged = { viewModel.setGain(it) },
                onAgcChanged = { viewModel.setAgcEnabled(it) },
            )
            if (uiState.isRunning) {
                VisualizationCard(
                    fftChannels = uiState.fftChannels,
                    sampleLevel = uiState.sampleLevel,
                    isPeak = uiState.isPeak,
                )
            }
            uiState.errorMessage?.let { message ->
                ErrorCard(errorMessage = message)
            }
            StartStopCard(
                isRunning = uiState.isRunning,
                hasRecordAudioPermission = uiState.hasRecordAudioPermission,
                onStart = {
                    if (!uiState.hasRecordAudioPermission) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        viewModel.start()
                    }
                },
                onStop = { viewModel.stop() },
            )
        }
    }
}

@Composable
private fun DescriptionCard() {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_graphic_eq_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.sound_sync_description),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun SourceSelectionCard(
    sourceType: SoundSyncAudioSourceType,
    isRunning: Boolean,
    onSourceSelected: (SoundSyncAudioSourceType) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.sound_sync_audio_source),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SourceRadioRow(
                label = stringResource(R.string.sound_sync_source_microphone),
                selected = sourceType == SoundSyncAudioSourceType.MICROPHONE,
                enabled = !isRunning,
                onSelected = { onSourceSelected(SoundSyncAudioSourceType.MICROPHONE) },
            )
            SourceRadioRow(
                label = stringResource(R.string.sound_sync_source_music),
                selected = sourceType == SoundSyncAudioSourceType.MUSIC,
                enabled = !isRunning,
                onSelected = { onSourceSelected(SoundSyncAudioSourceType.MUSIC) },
            )
        }
    }
}

@Composable
private fun SourceRadioRow(label: String, selected: Boolean, enabled: Boolean, onSelected: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelected,
            enabled = enabled,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun GainCard(
    gain: Float,
    agcEnabled: Boolean,
    onGainChanged: (Float) -> Unit,
    onAgcChanged: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.sound_sync_gain),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Slider(
                    value = gain,
                    onValueChange = onGainChanged,
                    valueRange = 0f..10f,
                    modifier = Modifier.weight(1f),
                    enabled = !agcEnabled,
                )
                Text(
                    text = "${(gain * 10).toInt() / 10f}x",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.sound_sync_agc),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = agcEnabled,
                    onCheckedChange = onAgcChanged,
                )
            }
        }
    }
}

@Composable
private fun VisualizationCard(fftChannels: ByteArray, sampleLevel: Float, isPeak: Boolean) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.sound_sync_fft_visualization),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            FftBarChart(
                fftChannels = fftChannels,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT_DP.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            SampleLevelIndicator(
                sampleLevel = sampleLevel,
                isPeak = isPeak,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
            )
        }
    }
}

@Composable
private fun FftBarChart(fftChannels: ByteArray, modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val channelCount = fftChannels.size
        if (channelCount == 0) return@Canvas
        val barWidth = size.width / channelCount
        val gap = barWidth * BAR_GAP_RATIO
        val actualBarWidth = barWidth - gap
        for (i in 0 until channelCount) {
            val value = (fftChannels[i].toInt() and BYTE_MASK) / CHANNEL_MAX_VALUE
            val barHeight = value * size.height
            val left = i * barWidth + gap / 2f
            drawRect(
                color = barColor,
                topLeft = Offset(left, size.height - barHeight),
                size = Size(actualBarWidth, barHeight),
            )
        }
    }
}

@Composable
private fun SampleLevelIndicator(sampleLevel: Float, isPeak: Boolean, modifier: Modifier = Modifier) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val levelColor = if (isPeak) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    Box(modifier = modifier) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                color = backgroundColor,
                topLeft = Offset.Zero,
                size = size,
            )
            val levelWidth = sampleLevel.coerceIn(0f, CHANNEL_MAX_VALUE) / CHANNEL_MAX_VALUE * size.width
            drawRect(
                color = levelColor,
                topLeft = Offset.Zero,
                size = Size(levelWidth, size.height),
            )
        }
        Text(
            text = stringResource(R.string.sound_sync_sample_level),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp),
        )
    }
}

@Composable
private fun ErrorCard(errorMessage: String) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(R.string.sound_sync_error, errorMessage),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun StartStopCard(
    isRunning: Boolean,
    hasRecordAudioPermission: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (isRunning) {
                Text(
                    text = stringResource(R.string.sound_sync_running),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onStop,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.sound_sync_stop))
                }
            } else {
                if (!hasRecordAudioPermission) {
                    Text(
                        text = stringResource(R.string.sound_sync_permission_required),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.sound_sync_grant_permission))
                    }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.sound_sync_start))
                    }
                }
            }
        }
    }
}
