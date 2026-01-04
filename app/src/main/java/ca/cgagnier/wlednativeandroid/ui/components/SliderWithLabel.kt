package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Label
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SliderWithLabel(
    value: Float,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
) {
    val animatedSliderPosition by animateFloatAsState(
        targetValue = value,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
        ),
        label = "animatedValue",
    )
    val interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }
    val isDragging by interactionSource.collectIsDraggedAsState()
    Slider(
        value = if (isDragging) value else animatedSliderPosition,
        enabled = enabled,
        onValueChange = onValueChange,
        valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        thumb = {
            val labelValue = value.roundToInt().coerceIn(
                valueRange.start.roundToInt(), valueRange.endInclusive.roundToInt()
            )
            Label(
                label = {
                    PlainTooltip(shape = MaterialTheme.shapes.large) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .padding(8.dp)
                        ) {
                            Text(
                                labelValue.toString(),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                },
                interactionSource = interactionSource,
            ) {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource
                )
            }
        })
}
