package ca.cgagnier.wlednativeandroid.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "service.AndroidDeviceDiscovery"
private val logger = Logger.withTag(TAG)
private const val SERVICE_TYPE_REGISTRATION = "_wled._tcp."
private const val SERVICE_TYPE_MATCH = "_wled._tcp"

class AndroidDeviceDiscovery(private val nsdManager: NsdManager?, private val wifiManager: WifiManager?) :
    DeviceDiscovery {

    constructor(context: Context) : this(
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager,
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager,
    )

    private val lock = Any()
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var isResolving = false
    private var activeResolveListener: NsdManager.ResolveListener? = null

    private val _discoveredDevices = MutableSharedFlow<DiscoveredDevice>(extraBufferCapacity = 64)
    override val discoveredDevices: SharedFlow<DiscoveredDevice> = _discoveredDevices.asSharedFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    override fun start() {
        synchronized(lock) {
            if (_isDiscovering.value || discoveryListener != null) {
                logger.d { "Service discovery already active" }
                return
            }

            if (nsdManager == null) {
                logger.w { "NsdManager not available" }
                return
            }

            acquireMulticastLock()

            val listener = createDiscoveryListener()
            discoveryListener = listener
            try {
                nsdManager.discoverServices(SERVICE_TYPE_REGISTRATION, NsdManager.PROTOCOL_DNS_SD, listener)
                _isDiscovering.value = true
            } catch (e: IllegalArgumentException) {
                logger.e(e) { "Failed to start service discovery due to invalid argument" }
                releaseMulticastLock()
                discoveryListener = null
            } catch (e: IllegalStateException) {
                logger.e(e) { "Failed to start service discovery due to invalid state" }
                releaseMulticastLock()
                discoveryListener = null
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            resolveQueue.clear()
            activeResolveListener = null
            isResolving = false

            releaseMulticastLock()

            discoveryListener?.let { listener ->
                try {
                    nsdManager?.stopServiceDiscovery(listener)
                } catch (e: IllegalArgumentException) {
                    logger.e(e) { "Failed to stop service discovery due to invalid argument" }
                } catch (e: IllegalStateException) {
                    logger.e(e) { "Failed to stop service discovery due to invalid state" }
                }
                discoveryListener = null
            }
            _isDiscovering.value = false
        }
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                multicastLock = wifiManager?.createMulticastLock("WLED_mDNS_multicastLock")?.apply {
                    setReferenceCounted(true)
                }
            }
            multicastLock?.let {
                if (!it.isHeld) {
                    it.acquire()
                }
            }
        } catch (e: SecurityException) {
            logger.e(e) { "SecurityException while acquiring multicast lock" }
        }
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: SecurityException) {
            logger.e(e) { "SecurityException while releasing multicast lock" }
        } catch (e: IllegalStateException) {
            logger.e(e) { "IllegalStateException while releasing multicast lock" }
        }
    }

    private fun createDiscoveryListener(): NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
            synchronized(lock) {
                if (discoveryListener !== this) return
                logger.e { "Discovery start failed: Error code:$errorCode" }
                stop()
            }
        }

        override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
            synchronized(lock) {
                if (discoveryListener !== this) return
                logger.e { "Discovery stop failed: Error code:$errorCode" }
            }
        }

        override fun onDiscoveryStarted(serviceType: String?) {
            synchronized(lock) {
                if (discoveryListener !== this) return
                logger.d { "Service discovery started: $serviceType" }
                _isDiscovering.value = true
            }
        }

        override fun onDiscoveryStopped(serviceType: String?) {
            synchronized(lock) {
                if (discoveryListener !== this) return
                logger.i { "Discovery stopped: $serviceType" }
                _isDiscovering.value = false
            }
        }

        override fun onServiceFound(service: NsdServiceInfo?) {
            synchronized(lock) {
                if (discoveryListener !== this) return
                logger.d { "Service discovery success [$service]" }
                if (service == null) return
                if (service.serviceType?.contains(SERVICE_TYPE_MATCH) != true) {
                    logger.d { "Unknown service type: ${service.serviceType}" }
                    return
                }
                enqueueServiceResolveLocked(service)
            }
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            synchronized(lock) {
                if (discoveryListener !== this) return
                logger.d { "Service lost: $service" }
            }
        }
    }

    private fun enqueueServiceResolveLocked(service: NsdServiceInfo) {
        resolveQueue.add(service)
        processNextResolveLocked()
    }

    private fun processNextResolveLocked() {
        if (isResolving || resolveQueue.isEmpty()) return
        val nextService = resolveQueue.removeFirst()
        isResolving = true
        resolveServiceInternal(nextService)
    }

    private fun finishResolve(listener: NsdManager.ResolveListener?) {
        synchronized(lock) {
            if (listener != null && activeResolveListener !== listener) {
                return
            }
            activeResolveListener = null
            isResolving = false
            processNextResolveLocked()
        }
    }

    private fun resolveServiceInternal(service: NsdServiceInfo) {
        val manager = nsdManager ?: run {
            finishResolve(null)
            return
        }
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                logger.w { "Resolve failed: code $errorCode" }
                finishResolve(this)
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
                synchronized(lock) {
                    if (activeResolveListener === this) {
                        handleServiceResolved(serviceInfo)
                    }
                }
                finishResolve(this)
            }
        }
        activeResolveListener = listener
        try {
            manager.resolveService(service, listener)
        } catch (e: IllegalArgumentException) {
            logger.e(e) { "Failed to resolve service due to invalid argument" }
            finishResolve(listener)
        } catch (e: IllegalStateException) {
            logger.e(e) { "Failed to resolve service due to invalid state" }
            finishResolve(listener)
        }
    }

    private fun handleServiceResolved(serviceInfo: NsdServiceInfo?) {
        if (serviceInfo == null) {
            logger.w { "Resolved serviceInfo is null" }
            return
        }
        val deviceIp = serviceInfo.host?.hostAddress
        if (deviceIp.isNullOrEmpty()) {
            logger.w { "Device discovered but host address is null/empty" }
            return
        }
        val macBytes = serviceInfo.attributes["mac"]
        val macAddress = macBytes?.let { String(it) }

        logger.i { "Device discovered: $deviceIp, MAC: $macAddress" }
        _discoveredDevices.tryEmit(DiscoveredDevice(address = deviceIp, macAddress = macAddress))
    }
}
