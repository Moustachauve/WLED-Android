package ca.cgagnier.wlednativeandroid.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.MulticastLock
import android.util.Log


class DeviceDiscovery(
    val context: Context, val onDeviceDiscovered: (address: String, macAddress: String?) -> Unit
) {

    val nsdManager: NsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val wifi =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val multicastLock: MulticastLock = wifi.createMulticastLock("multicastLock")

    private fun initialize() {
        discoveryListener = object : NsdManager.DiscoveryListener {

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: Error code:$errorCode")
                stop()
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (e: Exception) {
                    // Do nothing, exceptions here usually means we were not actually listening for
                    // discovery. This is likely since we are stopping it just before.
                    Log.e(TAG, "Failed to stop discovery: ${e.message}", e)
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: Error code:$errorCode")
                try {
                    nsdManager.stopServiceDiscovery(this)
                } catch (e: Exception) {
                    // Do nothing, exceptions here usually means we were not actually listening for
                    // discovery.
                    Log.e(TAG, "Failed to stop: ${e.message}", e)
                }
            }

            override fun onDiscoveryStarted(serviceType: String?) {
                Log.d(TAG, "Service discovery started: $serviceType")
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onServiceFound(service: NsdServiceInfo?) {
                Log.d(TAG, "Service discovery success [$service]")
                if (service != null) {
                    if (service.serviceType != SERVICE_TYPE) {
                        Log.d(TAG, "Unknown service type: ${service.serviceType}")
                        return
                    }
                    return nsdManager.resolveService(
                        service, ResolveListener(nsdManager) { onServiceResolved(it) })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo?) {
                Log.e(TAG, "service lost: $service")
            }
        }
    }

    private fun onServiceResolved(serviceInfo: NsdServiceInfo) {
        val deviceIp = serviceInfo.host.hostAddress
        if (deviceIp.isNullOrEmpty()) {
            Log.w(TAG, "Device discovered, but did not have IP")
            return
        }
        val macBytes = serviceInfo.attributes["mac"]
        val macAddress = if (macBytes != null) String(macBytes) else null

        Log.i(TAG, "Device discovered: $deviceIp, MAC: $macAddress")
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
                Log.e(TAG, "Failed to stop: ${e.message}", e)
            }

            discoveryListener = null
        }
    }

    class ResolveListener(
        private val nsdManager: NsdManager, private val serviceResolved: (NsdServiceInfo) -> Unit
    ) : NsdManager.ResolveListener {

        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
            Log.e(TAG, "Resolve failed $errorCode")
            when (errorCode) {
                NsdManager.FAILURE_ALREADY_ACTIVE -> {
                    Log.e(TAG, "FAILURE ALREADY ACTIVE")
                    nsdManager.resolveService(
                        serviceInfo, ResolveListener(nsdManager, serviceResolved)
                    )
                }

                NsdManager.FAILURE_INTERNAL_ERROR -> {
                    Log.e(TAG, "FAILURE_INTERNAL_ERROR")
                }

                NsdManager.FAILURE_MAX_LIMIT -> {
                    Log.e(TAG, "FAILURE_MAX_LIMIT")
                }

                else -> Log.e(TAG, "Resolve failed")
            }
        }

        override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
            Log.i(TAG, "Resolve Succeeded. [$serviceInfo]")
            if (serviceInfo != null) {
                serviceResolved(serviceInfo)
            } else {
                Log.e(TAG, "Resolve Succeeded, but serviceInfo null.")
            }
        }

    }

    companion object {
        private const val TAG = "DEVICE_DISCOVERY"
        const val SERVICE_TYPE = "_wled._tcp."
    }
}