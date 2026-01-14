package ca.cgagnier.wlednativeandroid.service.controls

import android.app.PendingIntent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.service.controls.Control
import android.service.controls.ControlsProviderService
import android.service.controls.DeviceTypes
import android.service.controls.actions.BooleanAction
import android.service.controls.actions.ControlAction
import android.service.controls.actions.FloatAction
import android.service.controls.templates.ControlButton
import android.service.controls.templates.ToggleRangeTemplate
import android.util.Log
import androidx.annotation.RequiresApi
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.domain.DeepLinkHandler
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.ui.theme.getColorFromDeviceState
import ca.cgagnier.wlednativeandroid.util.MAX_BRIGHTNESS_PERCENT
import ca.cgagnier.wlednativeandroid.util.brightnessToPercent
import ca.cgagnier.wlednativeandroid.util.percentToBrightness
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.jdk9.asPublisher
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Flow
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Consumer

/**
 * Android Device Controls (Quick Access) service for WLED devices.
 *
 * Provides controls in the Quick Settings panel to toggle lights on/off
 * and adjust brightness. Available on Android 11 (API 30) and above.
 */
@RequiresApi(Build.VERSION_CODES.R)
class WledControlsService : ControlsProviderService() {

    companion object {
        private const val TAG = "WledControlsService"
        private const val BRIGHTNESS_STEP = 1f
        private const val BRIGHTNESS_FORMAT = "%.0f%%"
        private const val CLEANUP_DELAY_MS = 5000L
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ControlsEntryPoint {
        fun deviceRepository(): DeviceRepository
        fun deviceApiFactory(): DeviceApiFactory
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    private val controlFlows = ConcurrentHashMap<String, MutableSharedFlow<Control>>()

    // Cache for device state (on/off and brightness)
    private val deviceStates = ConcurrentHashMap<String, DeviceControlState>()

    // Thread-safe unique request code generation for PendingIntents
    private val requestCodeCounter = AtomicInteger(0)
    private val requestCodes = ConcurrentHashMap<String, Int>()

    private val entryPoint: ControlsEntryPoint by lazy {
        EntryPointAccessors.fromApplication(applicationContext, ControlsEntryPoint::class.java)
    }

    private val deviceRepository: DeviceRepository
        get() = entryPoint.deviceRepository()

    private val deviceApiFactory: DeviceApiFactory
        get() = entryPoint.deviceApiFactory()

    override fun createPublisherForAllAvailable(): Flow.Publisher<Control> = kotlinx.coroutines.flow.flow {
        val devices = deviceRepository.getAllDevices()
        Log.d(TAG, "createPublisherForAllAvailable: Found ${devices.size} devices")
        devices.forEach { device ->
            emit(createStatelessControl(device))
        }
    }.flowOn(Dispatchers.IO).asPublisher()

    override fun createPublisherFor(controlIds: List<String>): Flow.Publisher<Control> {
        Log.d(TAG, "createPublisherFor: ${controlIds.size} controls")

        val flows = controlIds.map { getOrCreateFlow(it) }

        scope.launch {
            controlIds.forEach { controlId ->
                val device = deviceRepository.findDeviceByMacAddress(controlId)
                if (device != null) {
                    val flow = controlFlows[controlId]
                    if (flow != null) {
                        fetchAndEmitDeviceState(device, flow)
                    }
                } else {
                    Log.w(TAG, "Device not found for control: $controlId")
                }
            }
        }

        return flows.merge().asPublisher()
    }

    /**
     * Gets an existing flow for a control or creates a new dedicated flow.
     * Each control has its own flow to ensure updates reach all subscribers.
     */
    private fun getOrCreateFlow(controlId: String): MutableSharedFlow<Control> = controlFlows.getOrPut(controlId) {
        MutableSharedFlow<Control>(replay = 1, extraBufferCapacity = 1).also { flow ->
            observeSubscriptionCount(controlId, flow)
        }
    }

    /**
     * Observes subscription count and removes the flow from the map when
     * there are no subscribers for a period of time to prevent memory leaks.
     */
    private fun observeSubscriptionCount(controlId: String, flow: MutableSharedFlow<Control>) {
        scope.launch {
            flow.subscriptionCount.collect { count ->
                if (count == 0) {
                    delay(CLEANUP_DELAY_MS)
                    // Double-check subscription count after delay
                    if (flow.subscriptionCount.value == 0) {
                        controlFlows.remove(controlId, flow)
                        Log.d(TAG, "Removed inactive flow for control: $controlId")
                    }
                }
            }
        }
    }

    override fun performControlAction(controlId: String, action: ControlAction, consumer: Consumer<Int>) {
        Log.d(TAG, "performControlAction: $controlId, action type: ${action::class.simpleName}")

        val flow = controlFlows[controlId]
        if (flow == null) {
            Log.e(TAG, "No flow found for control: $controlId")
            consumer.accept(ControlAction.RESPONSE_FAIL)
            return
        }

        scope.launch {
            val device = deviceRepository.findDeviceByMacAddress(controlId)
            if (device == null) {
                Log.e(TAG, "Device not found: $controlId")
                consumer.accept(ControlAction.RESPONSE_FAIL)
                return@launch
            }

            try {
                when (action) {
                    is BooleanAction -> handleToggleAction(device, action.newState, flow, consumer)
                    is FloatAction -> handleBrightnessAction(device, action.newValue, flow, consumer)
                    else -> {
                        Log.w(TAG, "Unknown action type: ${action::class.simpleName}")
                        consumer.accept(ControlAction.RESPONSE_FAIL)
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Error performing action on ${device.address}", e)
                consumer.accept(ControlAction.RESPONSE_FAIL)
                emitUnavailableControl(device, flow)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    private suspend fun fetchAndEmitDeviceState(device: Device, flow: MutableSharedFlow<Control>) {
        try {
            val api = deviceApiFactory.create(device)
            val response = api.postJson(JsonPost(verbose = true))

            if (response.isSuccessful) {
                response.body()?.let { state ->
                    updateStateAndEmit(device, state, flow)
                    Log.d(
                        TAG,
                        "Emitted state for ${device.address}: on=${state.isOn}, bri=${state.brightness}",
                    )
                } ?: run {
                    Log.w(TAG, "Body is missing for ${device.address}: ${response.code()}")
                    emitUnavailableControl(device, flow)
                }
            } else {
                Log.w(TAG, "Failed to fetch state for ${device.address}: ${response.code()}")
                emitUnavailableControl(device, flow)
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Error fetching state for ${device.address}", e)
            emitUnavailableControl(device, flow)
        }
    }

    private suspend fun handleToggleAction(
        device: Device,
        newState: Boolean,
        flow: MutableSharedFlow<Control>,
        consumer: Consumer<Int>,
    ) {
        val api = deviceApiFactory.create(device)
        val response = api.postJson(JsonPost(isOn = newState, verbose = true))

        if (response.isSuccessful) {
            response.body()?.let { state ->
                updateStateAndEmit(device, state, flow)
                consumer.accept(ControlAction.RESPONSE_OK)
            } ?: consumer.accept(ControlAction.RESPONSE_FAIL)
        } else {
            consumer.accept(ControlAction.RESPONSE_FAIL)
        }
    }

    private suspend fun handleBrightnessAction(
        device: Device,
        newValue: Float,
        flow: MutableSharedFlow<Control>,
        consumer: Consumer<Int>,
    ) {
        val brightness = percentToBrightness(newValue)
        val api = deviceApiFactory.create(device)
        val response = api.postJson(JsonPost(brightness = brightness, verbose = true))

        if (response.isSuccessful) {
            response.body()?.let { state ->
                updateStateAndEmit(device, state, flow)
                consumer.accept(ControlAction.RESPONSE_OK)
            } ?: consumer.accept(ControlAction.RESPONSE_FAIL)
        } else {
            consumer.accept(ControlAction.RESPONSE_FAIL)
        }
    }

    private suspend fun updateStateAndEmit(device: Device, state: State, flow: MutableSharedFlow<Control>) {
        val currentColor = deviceStates[device.macAddress]?.color
        val newColor = getColorFromDeviceState(state)
        // If the new color is the default White (parsing failed/missing), preserve the old color if it exists
        // Otherwise use the new color (White) or Black as a safe fallback for the control
        val resolvedColor = if (newColor == Color.WHITE && currentColor != null && currentColor != Color.WHITE) {
            currentColor
        } else {
            newColor.takeIf { it != Color.WHITE } ?: currentColor ?: Color.BLACK
        }

        val deviceState = DeviceControlState(
            isOn = state.isOn ?: deviceStates[device.macAddress]?.isOn ?: false,
            brightness = state.brightness ?: deviceStates[device.macAddress]?.brightness ?: 0,
            color = resolvedColor,
        )
        deviceStates[device.macAddress] = deviceState

        val control = createStatefulControl(device, deviceState, Control.STATUS_OK)
        flow.emit(control)
    }

    private suspend fun emitUnavailableControl(device: Device, flow: MutableSharedFlow<Control>) {
        val cachedState =
            deviceStates[device.macAddress] ?: DeviceControlState(isOn = false, brightness = 0, color = Color.BLACK)
        val control = createStatefulControl(device, cachedState, Control.STATUS_DISABLED)
        flow.emit(control)
    }

    private fun createStatelessControl(device: Device): Control {
        val pendingIntent = createPendingIntent(device)
        val displayName = device.customName.ifBlank { device.originalName.ifBlank { device.address } }

        return Control.StatelessBuilder(device.macAddress, pendingIntent)
            .setTitle(displayName)
            .setSubtitle(device.address)
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .build()
    }

    private fun createStatefulControl(device: Device, state: DeviceControlState, status: Int): Control {
        val pendingIntent = createPendingIntent(device)
        val displayName = device.customName.ifBlank { device.originalName.ifBlank { device.address } }
        val brightnessPercent = brightnessToPercent(state.brightness)

        val template = ToggleRangeTemplate(
            device.macAddress,
            ControlButton(
                state.isOn,
                getString(if (state.isOn) R.string.control_on else R.string.control_off),
            ),
            /* range = */
            android.service.controls.templates.RangeTemplate(
                "${device.macAddress}_range",
                0f,
                MAX_BRIGHTNESS_PERCENT,
                brightnessPercent,
                BRIGHTNESS_STEP,
                BRIGHTNESS_FORMAT,
            ),
        )

        return Control.StatefulBuilder(device.macAddress, pendingIntent)
            .setTitle(displayName)
            .setSubtitle(device.address)
            .setDeviceType(DeviceTypes.TYPE_LIGHT)
            .setStatus(status)
            .setControlTemplate(template)
            .setCustomColor(ColorStateList.valueOf(state.color))
            .build()
    }

    private fun createPendingIntent(device: Device): PendingIntent {
        val intent = DeepLinkHandler.createDeviceIntent(this, device.macAddress)

        return PendingIntent.getActivity(
            this,
            getOrCreateRequestCode(device.macAddress),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Gets or creates a unique request code for a device's PendingIntent.
     * This avoids hash code collisions that could occur with String.hashCode().
     */
    private fun getOrCreateRequestCode(macAddress: String): Int =
        requestCodes.getOrPut(macAddress) { requestCodeCounter.incrementAndGet() }

    private data class DeviceControlState(val isOn: Boolean, val brightness: Int, val color: Int)
}
