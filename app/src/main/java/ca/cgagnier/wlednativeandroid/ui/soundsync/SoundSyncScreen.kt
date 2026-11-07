package ca.cgagnier.wlednativeandroid.ui.soundsync

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncError
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncIconVisualizer
import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.GainControl
import kotlinx.coroutines.flow.StateFlow
import org.koin.androidx.compose.koinViewModel

private const val CHART_HEIGHT_DP = 120
private const val CHANNEL_MAX_VALUE = 255f
private const val BAR_GAP_RATIO = 0.15f

@Composable
fun SoundSyncScreen(navigateUp: () -> Unit, viewModel: SoundSyncViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        viewModel.refreshPermissionState()
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            permanentlyDenied = false
            viewModel.start()
        } else {
            val activity = context.findActivity()
            permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermissionState()
        onPauseOrDispose { }
    }

    if (uiState.hasRecordAudioPermission && permanentlyDenied) {
        permanentlyDenied = false
    }

    Scaffold(
        topBar = { SoundSyncTopBar(navigateUp = navigateUp) },
    ) { innerPadding ->
        SoundSyncContent(
            uiState = uiState,
            permanentlyDenied = permanentlyDenied,
            onRequestPermissionOrSettings = {
                if (permanentlyDenied) {
                    openAppSettings(context)
                } else {
                    permissionLauncher.launch(requiredPermissions())
                }
            },
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun SoundSyncTopBar(navigateUp: () -> Unit) {
    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.sound_sync))
                Surface(
                    modifier = Modifier.padding(start = 8.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.beta_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.description_back_button),
                )
            }
        },
    )
}

@Composable
private fun SoundSyncContent(
    uiState: SoundSyncUiState,
    permanentlyDenied: Boolean,
    onRequestPermissionOrSettings: () -> Unit,
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
            StartStopCard(
                isRunning = uiState.isRunning,
                hasRecordAudioPermission = uiState.hasRecordAudioPermission,
                permanentlyDenied = permanentlyDenied,
                onStart = {
                    if (!uiState.hasRecordAudioPermission) {
                        onRequestPermissionOrSettings()
                    } else {
                        viewModel.start()
                    }
                },
                onStop = { viewModel.stop() },
            )
            uiState.error?.let { ErrorCard(it) }
            if (uiState.isRunning) {
                VisualizationCard(latestPacketFlow = viewModel.latestPacket)
            }
            GainCard(
                gain = uiState.displayedGain,
                agcEnabled = uiState.agcEnabled,
                onGainChanged = { viewModel.setGain(it) },
                onGainChangeFinished = { viewModel.saveGain() },
                onAgcChanged = { viewModel.setAgcEnabled(it) },
            )
        }
    }
}

@Composable
private fun DescriptionCard() {
    SoundSyncCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_graphic_eq_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = stringResource(R.string.sound_sync_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.sound_sync_beta_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(error: SoundSyncError) {
    val messageResId = when (error) {
        SoundSyncError.PERMISSION_REQUIRED -> R.string.sound_sync_error_permission
        SoundSyncError.CAPTURE_FAILED -> R.string.sound_sync_error_capture
    }
    SoundSyncCard {
        Text(
            text = stringResource(R.string.sound_sync_error, stringResource(messageResId)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun SoundSyncCard(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

@Composable
private fun GainCard(
    gain: Float,
    agcEnabled: Boolean,
    onGainChanged: (Float) -> Unit,
    onGainChangeFinished: () -> Unit,
    onAgcChanged: (Boolean) -> Unit,
) {
    val animatedAgcGain by animateFloatAsState(
        targetValue = gain,
        label = "agcGainSlider",
    )
    val sliderGain = if (agcEnabled) animatedAgcGain else gain

    SoundSyncCard {
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
                value = sliderGain,
                onValueChange = onGainChanged,
                onValueChangeFinished = onGainChangeFinished,
                valueRange = 0f..GainControl.MAX_MANUAL_GAIN,
                modifier = Modifier.weight(1f),
                enabled = !agcEnabled,
            )
            Text(
                text = stringResource(R.string.sound_sync_gain_value, gain),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = agcEnabled,
                    onValueChange = onAgcChanged,
                    role = Role.Switch,
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.sound_sync_agc),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = agcEnabled,
                onCheckedChange = null,
            )
        }
    }
}

@Composable
private fun VisualizationCard(latestPacketFlow: StateFlow<UdpSoundSyncPacket>) {
    val packet by latestPacketFlow.collectAsStateWithLifecycle()
    var frameTimeMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        val startTimeMs = withInfiniteAnimationFrameMillis { it }
        while (true) {
            frameTimeMs = withInfiniteAnimationFrameMillis { it - startTimeMs }
        }
    }

    SoundSyncCard {
        Text(
            stringResource(R.string.sound_sync_fft_visualization),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(8.dp))
        FftBarChart(
            fftChannelsProvider = { packet.fftResult },
            frameTimeMsProvider = { frameTimeMs },
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT_DP.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.sound_sync_sample_level),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SampleLevelIndicator(
            sampleLevelProvider = { packet.sampleSmth },
            isPeakProvider = { packet.samplePeak },
            frameTimeMsProvider = { frameTimeMs },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        )
    }
}

@Composable
private fun FftBarChart(
    fftChannelsProvider: () -> ByteArray,
    frameTimeMsProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val barColor = MaterialTheme.colorScheme.primary
    val lastDrawTimeMs = remember { LongArray(1) }
    val smoothedChannels = remember { FloatArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT) }

    Canvas(modifier = modifier) {
        val elapsedMs = frameTimeMsProvider()
        val deltaTimeMs = (elapsedMs - lastDrawTimeMs[0]).coerceAtLeast(0L).also {
            lastDrawTimeMs[0] = elapsedMs
        }
        val fftChannels = fftChannelsProvider()
        if (fftChannels.isEmpty()) return@Canvas

        SoundSyncIconVisualizer.smoothFftChannels(
            fftChannels = fftChannels,
            deltaTimeMs = deltaTimeMs,
            smoothedOutput = smoothedChannels,
        )

        val barWidth = size.width / smoothedChannels.size
        val gap = barWidth * BAR_GAP_RATIO
        val actualBarWidth = barWidth - gap
        for (i in smoothedChannels.indices) {
            val barHeight = smoothedChannels[i] * size.height
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
private fun SampleLevelIndicator(
    sampleLevelProvider: () -> Float,
    isPeakProvider: () -> Boolean,
    frameTimeMsProvider: () -> Long,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val lastDrawTimeMs = remember { LongArray(1) }
    val smoothedLevel = remember { FloatArray(1) }

    Canvas(modifier = modifier) {
        val elapsedMs = frameTimeMsProvider()
        val deltaTimeMs = (elapsedMs - lastDrawTimeMs[0]).coerceAtLeast(0L).also {
            lastDrawTimeMs[0] = elapsedMs
        }
        val targetLevel = sampleLevelProvider().coerceIn(0f, CHANNEL_MAX_VALUE)
        smoothedLevel[0] = SoundSyncIconVisualizer.smoothBarLevel(
            previous = smoothedLevel[0],
            target = targetLevel,
            isRunning = true,
            deltaTimeMs = deltaTimeMs,
        )

        drawRect(color = backgroundColor, topLeft = Offset.Zero, size = size)
        val levelColor = if (isPeakProvider()) errorColor else primaryColor
        val levelWidth = smoothedLevel[0] / CHANNEL_MAX_VALUE * size.width
        drawRect(color = levelColor, topLeft = Offset.Zero, size = Size(levelWidth, size.height))
    }
}

@Composable
private fun StartStopCard(
    isRunning: Boolean,
    hasRecordAudioPermission: Boolean,
    permanentlyDenied: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    SoundSyncCard(horizontalAlignment = Alignment.CenterHorizontally) {
        if (isRunning) {
            Text(
                text = stringResource(R.string.sound_sync_running),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sound_sync_stop))
            }
        } else if (!hasRecordAudioPermission) {
            Text(
                text = stringResource(R.string.sound_sync_permission_required),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                val buttonTextRes = if (permanentlyDenied) {
                    R.string.sound_sync_open_settings
                } else {
                    R.string.sound_sync_grant_permission
                }
                Text(stringResource(buttonTextRes))
            }
        } else {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sound_sync_start))
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

private fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
} else {
    arrayOf(Manifest.permission.RECORD_AUDIO)
}
