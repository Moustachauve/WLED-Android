package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.UserMods
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlinx.coroutines.delay


@Composable
fun DeviceInfoTwoRows(
    modifier: Modifier = Modifier,
    device: DeviceWithState,
    nameMaxLines: Int = 2,
) {
    val updateTag by device.updateVersionTagFlow.collectAsState(initial = null)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                deviceName(device.device),
                style = MaterialTheme.typography.titleLarge,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .padding(bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WebsocketStatusIndicator(device.websocketStatus.value)
            Text(
                device.device.address,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
            )
            deviceNetworkStrengthImage(device)
            deviceBatteryPercentageImage(device)

            if (updateTag != null) {
                Icon(
                    painter = painterResource(R.drawable.baseline_download_24),
                    contentDescription = stringResource(R.string.network_status),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .height(20.dp)
                )
            }
            if (!device.isOnline) {
                Text(
                    stringResource(R.string.is_offline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (device.device.isHidden) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_visibility_off_24),
                    contentDescription = stringResource(R.string.description_back_button),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .height(16.dp)
                )
                Text(
                    stringResource(R.string.hidden_status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

        }
    }
}

@Composable
fun WebsocketStatusIndicator(websocketState: WebsocketStatus) {
    val tooltipTextResource = when (websocketState) {
        WebsocketStatus.CONNECTED -> R.string.websocket_connected
        WebsocketStatus.CONNECTING -> R.string.websocket_connecting
        WebsocketStatus.DISCONNECTED -> R.string.websocket_disconnected
    }
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                Text(stringResource(tooltipTextResource))
            }
        },
        state = rememberTooltipState()
    ) {
        WebsocketStatusShape(websocketState)
    }
}

@Composable
fun WebsocketStatusShape(websocketState: WebsocketStatus) {
    val connectedColor = MaterialTheme.colorScheme.primary
    val connectingColor = MaterialTheme.colorScheme.tertiary
    val disconnectedColor = MaterialTheme.colorScheme.error

    val targetColor = when (websocketState) {
        WebsocketStatus.CONNECTED -> connectedColor
        WebsocketStatus.CONNECTING -> connectingColor
        WebsocketStatus.DISCONNECTED -> disconnectedColor
    }
    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "ColorAnimation"
    )

    // Infinite Rotation for "Connecting" state
    val infiniteTransition = rememberInfiniteTransition(label = "Spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ), label = "Rotation"
    )

    // TODO: Replace those RoundedPolygon with MaterialShapes (cookies) when available
    fun getShape(status: WebsocketStatus): RoundedPolygon {
        return when (status) {
            // Circle = Stable, Connected
            WebsocketStatus.CONNECTED -> RoundedPolygon.circle()
            // Scalloped/Star shape = Active, Gear-like
            WebsocketStatus.CONNECTING -> RoundedPolygon.star(
                8,
                innerRadius = 0.7f,
                rounding = CornerRounding(0.1f)
            )
            // Square/Diamond = Stopped, Error
            WebsocketStatus.DISCONNECTED -> RoundedPolygon(4, rounding = CornerRounding(0.25f))
        }
    }

    // State to hold the transition shapes
    // We initialize both to the current state so it starts static
    var startShape by remember { mutableStateOf(getShape(websocketState)) }
    var endShape by remember { mutableStateOf(getShape(websocketState)) }
    val progress = remember { Animatable(1f) }

    // When the state changes, shift the shapes and trigger animation
    LaunchedEffect(websocketState) {
        startShape = endShape // The old 'end' becomes the new 'start'
        endShape = getShape(websocketState) // Create the new target

        // Reset progress to 0 and animate to 1
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }
    val morph = remember(startShape, endShape) {
        Morph(startShape, endShape)
    }

    Box(
        modifier = Modifier
            .padding(end = 4.dp)
            .size(12.dp)
            .drawWithCache {
                val matrix = Matrix()
                matrix.translate(size.width / 2f, size.height / 2f)
                matrix.scale(size.width / 2f, size.height / 2f)

                // Apply rotation ONLY if connecting
                if (websocketState == WebsocketStatus.CONNECTING) {
                    matrix.rotateZ(rotation)
                }
                val path = morph.toPath(progress = progress.value).asComposePath()
                path.transform(matrix)

                onDrawBehind {
                    val style = if (websocketState == WebsocketStatus.DISCONNECTED) {
                        Stroke(1.7.dp.toPx())
                    } else {
                        Fill
                    }
                    drawPath(
                        path,
                        color = animatedColor,
                        style = style
                    )
                }
            }

    )
}

class SampleDevicesWithStateProvider : PreviewParameterProvider<DeviceWithState> {
    override val values = sequenceOf(
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "4.3.2.1",
                originalName = "original name",
                customName = "custom name",
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.CONNECTED
        },
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "4.3.2.1",
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.CONNECTING
        },
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "4.3.2.1",
                originalName = "original name"
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.DISCONNECTED
        },
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "very-long-address-that-takes-more-than-a-full-width-so-should-be-truncated",
                originalName = "Very long name that should also be truncated if everything is working"
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.DISCONNECTED
        },
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "4.3.2.1",
                originalName = "device with battery"
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.CONNECTED
            stateInfo.value = DeviceStateInfo(
                State(isOn = true, brightness = 128, transition = 7),
                Info(
                    version = "0.14.0",
                    leds = Leds(count = 60),
                    name = "WLED",
                    wifi = Wifi(bssid = "ff:ee:dd:cc:bb:aa", rssi = -65, signal = 70, channel = 6),
                    userMods = UserMods(
                        batteryLevel = listOf(75.0)
                    )
                )
            )
        },
    )
}

@Preview
@Composable
fun DeviceInfoTwoRowsPreview(
    @PreviewParameter(SampleDevicesWithStateProvider::class) device: DeviceWithState
) {
    Card(
        modifier = Modifier
            .padding(16.dp)
            .padding(top = 20.dp)
            .fillMaxWidth()
    ) {
        DeviceInfoTwoRows(device = device, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, name = "Live Animation Preview")
@Composable
fun AnimatedWebsocketPreview() {
    // This state will cycle automatically
    var currentState by remember { mutableStateOf(WebsocketStatus.DISCONNECTED) }

    // Loop to cycle through states for preview purposes
    LaunchedEffect(Unit) {
        while (true) {
            delay(1500)
            currentState = WebsocketStatus.CONNECTING
            delay(1500)
            currentState = WebsocketStatus.CONNECTED
            delay(1500)
            currentState = WebsocketStatus.DISCONNECTED
        }
    }
    val device = DeviceWithState(
        Device(
            macAddress = AP_MODE_MAC_ADDRESS,
            address = "4.3.2.1",
        )
    )
    device.websocketStatus.value = currentState

    MaterialTheme {

        Card(
            modifier = Modifier
                .padding(16.dp)
                .padding(top = 20.dp)
                .fillMaxWidth()
        ) {
            DeviceInfoTwoRows(device = device, modifier = Modifier.padding(16.dp))
        }
    }
}