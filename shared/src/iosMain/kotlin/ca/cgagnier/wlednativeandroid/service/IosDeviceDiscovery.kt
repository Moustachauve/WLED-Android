package ca.cgagnier.wlednativeandroid.service

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_browse_descriptor_create_bonjour_service
import platform.Network.nw_browser_cancel
import platform.Network.nw_browser_create
import platform.Network.nw_browser_set_queue
import platform.Network.nw_browser_start
import platform.Network.nw_browser_t
import platform.Network.nw_parameters_create
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

private const val TAG = "service.IosDeviceDiscovery"
private val logger = Logger.withTag(TAG)
private const val SERVICE_TYPE = "_wled._tcp"
private const val DOMAIN = "local."

interface IosBonjourBrowser {
    fun start(onDeviceFound: (DiscoveredDevice) -> Unit): Boolean
    fun stop()
}

@OptIn(ExperimentalForeignApi::class)
internal class RealIosBonjourBrowser(
    private val queue: dispatch_queue_t = dispatch_queue_create("ca.cgagnier.wlednativeandroid.browser", null),
) : IosBonjourBrowser {
    private var browser: nw_browser_t = null

    override fun start(onDeviceFound: (DiscoveredDevice) -> Unit): Boolean {
        stop()
        val descriptor = nw_browse_descriptor_create_bonjour_service(SERVICE_TYPE, DOMAIN)
        val parameters = nw_parameters_create()
        val b = nw_browser_create(descriptor, parameters)
        if (b == null) {
            logger.w { "nw_browser_create returned null" }
            return false
        }
        browser = b
        nw_browser_set_queue(b, queue)
        nw_browser_start(b)
        logger.i { "nw_browser started for $SERVICE_TYPE.$DOMAIN (endpoint IP resolution deferred to iOS app target)" }
        return true
    }

    override fun stop() {
        browser?.let {
            nw_browser_cancel(it)
        }
        browser = null
        logger.d { "nw_browser stopped" }
    }
}

class IosDeviceDiscovery(private val browser: IosBonjourBrowser) : DeviceDiscovery {

    constructor() : this(RealIosBonjourBrowser())

    private val _discoveredDevices = MutableSharedFlow<DiscoveredDevice>(extraBufferCapacity = 64)
    override val discoveredDevices: SharedFlow<DiscoveredDevice> = _discoveredDevices.asSharedFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    override fun start() {
        if (_isDiscovering.value) return
        val started = browser.start { device ->
            _discoveredDevices.tryEmit(device)
        }
        _isDiscovering.value = started
    }

    override fun stop() {
        browser.stop()
        _isDiscovering.value = false
    }
}
