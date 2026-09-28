package ca.cgagnier.wlednativeandroid.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import co.touchlab.kermit.Logger

private const val TAG = "DEVICE_DISCOVERY"
private val logger = Logger.withTag(TAG)

class DeviceDiscovery(val context: Context, val onDeviceDiscovered: (address: String, macAddress: String?) -> Unit) {

    val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val wifi =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val multicastLock: MulticastLock = wifi.createMulticastLock("multicastLock")

    private fun initialize() {
        discoveryListener = object : NsdManager.DiscoveryListener {

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                logger.e { "Discovery start failed: Error code:$errorCode" }
                stop()
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (e: Exception) {
                    // Do nothing, exceptions here usually means we were not actually listening for
                    // discovery. This is likely since we are stopping it just before.
                    logger.e(e) { "Failed to stop discovery: ${e.message}" }
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                logger.e { "Discovery stop failed: Error code:$errorCode" }
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (e: Exception) {
                    // Do nothing, exceptions here usually means we were not actually listening for
                    // discovery.
                    logger.e(e) { "Failed to stop: ${e.message}" }
                }
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                logger.d { "Service discovery started: $serviceType" }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                logger.i { "Discovery stopped: $serviceType" }
            }

            override fun onServiceFound(service: NsdServiceInfo?) {
                logger.d { "Service discovery success [$service]" }
                if (service != null) {
                    if (service.serviceType != SERVICE_TYPE) {
                        logger.d { "Unknown service type: ${service.serviceType}" }
                        return
                    }
                    return nsdManager.resolveService(
                        service,
                        ResolveListener(nsdManager) { onServiceResolved(it) },
                    )
                }
            }

            override fun onServiceLost(service: NsdServiceInfo?) {
                logger.e { "service lost: $service" }
            }
        }
    }

    private fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        val deviceIp = serviceInfo.host.hostAddress
        if (deviceIp.isNullOrEmpty()) {
            logger.w { "Device discovered, but did not have IP" }
            return
        }
        val macBytes = serviceInfo.attributes["mac"]
        val macAddress = if (macBytes != null) String(macBytes) else null

        logger.i { "Device discovered: $deviceIp, MAC: $macAddress" }
        onDeviceDiscovered(deviceIp, macAddress)
    }

    fun start() {
        stop()

        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()

        initialize()
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stop() {
        if (multicastLock.isHeld) {
            multicastLock.release()
        }
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener)
            } catch (e: Exception) {
                logger.e(e) { "Failed to stop: ${e.message}" }
            }

            discoveryListener = null
        }
    }

    class ResolveListener(private val nsdManager: NsdManager, private val serviceResolved: (NsdServiceInfo) -> Unit) :
        NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            logger.e { "Resolve failed $errorCode" }
            when (errorCode) {
                NsdManager.FAILURE_ALREADY_ACTIVE -> {
                    logger.e { "FAILURE ALREADY ACTIVE" }
                    nsdManager.resolveService(
                        serviceInfo,
                        ResolveListener(nsdManager, serviceResolved),
                    )
                }

                NsdManager.FAILURE_INTERNAL_ERROR -> {
                    logger.e { "FAILURE_INTERNAL_ERROR" }
                }

                NsdManager.FAILURE_MAX_LIMIT -> {
                    logger.e { "FAILURE_MAX_LIMIT" }
                }

                else -> logger.e { "Resolve failed" }
            }
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
            logger.i { "Resolve Succeeded. [$serviceInfo]" }
            if (serviceInfo != null) {
                serviceResolved(serviceInfo)
            } else {
                logger.e { "Resolve Succeeded, but serviceInfo null." }
            }
        }
    }

    companion object {
        const val SERVICE_TYPE = "_wled._tcp."
    }
}
