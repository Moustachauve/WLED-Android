package ca.cgagnier.wlednativeandroid.ui.soundsync

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncIconVisualizer

private const val ICON_SIZE_DP = 24
private const val VIEWPORT_SIZE = 24f
private const val BAR_WIDTH_UNITS = 2f
private const val BAR_STEP_UNITS = 4f
private const val FIRST_BAR_X_UNITS = 3f
private const val MAX_BAR_HEIGHT_UNITS = 20f

/**
 * Toolbar icon for Sound Sync that renders the 5 rounded `graphic_eq` bars.
 *
 * When [isRunning] is `false`, renders the static resting waveform in [LocalContentColor].
 * When [isRunning] is `true`, tints the icon with `MaterialTheme.colorScheme.primary` and
 * animates the bars growing up and down from the vertical center with a gentle idle wave
 * in silence that reacts to live FFT frequency bands when sound plays.
 */
@Composable
fun SoundSyncToolbarIcon(isRunning: Boolean, fftChannelsProvider: () -> ByteArray, modifier: Modifier = Modifier) {
    val tint = if (isRunning) {
        MaterialTheme.colorScheme.primary
    } else {
        LocalContentColor.current
    }
    val contentDescription = stringResource(R.string.sound_sync)

    var frameTimeMs by remember { mutableLongStateOf(0L) }
    val lastDrawTimeMs = remember { LongArray(1) }
    val targetLevels = remember { FloatArray(SoundSyncIconVisualizer.BAR_COUNT) }
    val smoothedLevels = remember {
        FloatArray(SoundSyncIconVisualizer.BAR_COUNT) {
            SoundSyncIconVisualizer.restingBarLevel(it)
        }
    }

    if (isRunning) {
        LaunchedEffect(Unit) {
            lastDrawTimeMs[0] = 0L
            val startTimeMs = withInfiniteAnimationFrameMillis { it }
            while (true) {
                frameTimeMs = withInfiniteAnimationFrameMillis { it - startTimeMs }
            }
        }
    }

    Canvas(
        modifier = modifier.size(ICON_SIZE_DP.dp),
        contentDescription = contentDescription,
    ) {
        val elapsedMs = frameTimeMs
        val deltaTimeMs = if (isRunning) {
            (elapsedMs - lastDrawTimeMs[0]).coerceAtLeast(0L).also {
                lastDrawTimeMs[0] = elapsedMs
            }
        } else {
            0L
        }

        SoundSyncIconVisualizer.computeBarLevels(
            isRunning = isRunning,
            fftChannels = fftChannelsProvider(),
            elapsedTimeMs = elapsedMs,
            output = targetLevels,
        )

        for (i in 0 until SoundSyncIconVisualizer.BAR_COUNT) {
            smoothedLevels[i] = SoundSyncIconVisualizer.smoothBarLevel(
                previous = smoothedLevels[i],
                target = targetLevels[i],
                isRunning = isRunning,
                deltaTimeMs = deltaTimeMs,
            )
        }

        drawSoundSyncBars(tint = tint, levels = smoothedLevels)
    }
}

private fun DrawScope.drawSoundSyncBars(tint: Color, levels: FloatArray) {
    val unitScaleX = size.width / VIEWPORT_SIZE
    val unitScaleY = size.height / VIEWPORT_SIZE
    val barWidth = BAR_WIDTH_UNITS * unitScaleX
    val maxBarHeight = MAX_BAR_HEIGHT_UNITS * unitScaleY
    val centerY = size.height / 2f
    val cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)

    for (i in 0 until SoundSyncIconVisualizer.BAR_COUNT) {
        val barHeight = maxBarHeight * levels[i]
        val left = (FIRST_BAR_X_UNITS + i * BAR_STEP_UNITS) * unitScaleX
        val top = centerY - barHeight / 2f

        drawRoundRect(
            color = tint,
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = cornerRadius,
        )
    }
}
