package ca.cgagnier.wlednativeandroid.service

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

private const val TAG = "service.IosNetworkConnectivityManager"
private val logger = Logger.withTag(TAG)
private const val STOP_TIMEOUT_MILLIS = 5000L

interface IosPathMonitor {
    fun start(onUpdate: (isConnected: Boolean) -> Unit)
    fun cancel()
}

@OptIn(ExperimentalForeignApi::class)
internal class RealIosPathMonitor(
    private val queue: dispatch_queue_t = dispatch_queue_create("ca.cgagnier.wlednativeandroid.networkmonitor", null),
) : IosPathMonitor {
    private var monitor: nw_path_monitor_t = null

    override fun start(onUpdate: (isConnected: Boolean) -> Unit) {
        cancel()
        val m = nw_path_monitor_create()
        if (m == null) {
            logger.w { "nw_path_monitor_create returned null" }
            onUpdate(false)
            return
        }
        monitor = m
        nw_path_monitor_set_queue(m, queue)
        nw_path_monitor_set_update_handler(m) { path ->
            val isConnected = nw_path_get_status(path) == nw_path_status_satisfied
            logger.d { "nw_path update: isConnected=$isConnected" }
            onUpdate(isConnected)
        }
        nw_path_monitor_start(m)
    }

    override fun cancel() {
        monitor?.let {
            nw_path_monitor_cancel(it)
        }
        monitor = null
    }
}

class IosNetworkConnectivityManager internal constructor(
    externalScope: CoroutineScope,
    private val pathMonitor: IosPathMonitor,
    initialStatus: NetworkStatus = NetworkStatus(isConnected = false, isWLEDCaptivePortal = false),
) : NetworkConnectivityManager {

    constructor(
        externalScope: CoroutineScope,
    ) : this(
        externalScope = externalScope,
        pathMonitor = RealIosPathMonitor(),
    )

    override val networkStatus: StateFlow<NetworkStatus> = callbackFlow {
        pathMonitor.start { isConnected ->
            trySend(NetworkStatus(isConnected = isConnected, isWLEDCaptivePortal = false))
        }

        awaitClose {
            pathMonitor.cancel()
        }
    }.distinctUntilChanged()
        .stateIn(
            scope = externalScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = initialStatus,
        )

    override val isConnected: StateFlow<Boolean> = networkStatus
        .map { it.isConnected }
        .distinctUntilChanged()
        .stateIn(
            scope = externalScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = networkStatus.value.isConnected,
        )

    override val isWLEDCaptivePortal: StateFlow<Boolean> = networkStatus
        .map { it.isWLEDCaptivePortal }
        .distinctUntilChanged()
        .stateIn(
            scope = externalScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = networkStatus.value.isWLEDCaptivePortal,
        )
}
