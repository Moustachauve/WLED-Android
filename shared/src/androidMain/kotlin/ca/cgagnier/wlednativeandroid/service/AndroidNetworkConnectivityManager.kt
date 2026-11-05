package ca.cgagnier.wlednativeandroid.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private const val TAG = "service.AndroidNetworkConnectivityManager"
private val logger = Logger.withTag(TAG)
private const val STOP_TIMEOUT_MILLIS = 5000L

class AndroidNetworkConnectivityManager(
    private val connectivityManager: ConnectivityManager?,
    externalScope: CoroutineScope,
) : NetworkConnectivityManager {

    constructor(
        context: Context,
        externalScope: CoroutineScope,
    ) : this(
        connectivityManager = context.getSystemService(ConnectivityManager::class.java),
        externalScope = externalScope,
    )

    override val networkStatus: StateFlow<NetworkStatus> = callbackFlow {
        if (connectivityManager == null) {
            logger.w { "ConnectivityManager not available" }
            trySend(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false))
            awaitClose {}
            return@callbackFlow
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                logger.d { "onAvailable: $network" }
                val linkProperties = getSafeLinkProperties(network)
                trySend(
                    NetworkStatus(
                        isConnected = true,
                        isWLEDCaptivePortal = linkProperties.isWLEDCaptivePortal(),
                    ),
                )
            }

            override fun onLost(network: Network) {
                logger.d { "onLost: $network" }
                val activeNetwork = getSafeActiveNetwork()
                if (activeNetwork == null || activeNetwork == network) {
                    trySend(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false))
                } else {
                    val linkProperties = getSafeLinkProperties(activeNetwork)
                    trySend(
                        NetworkStatus(
                            isConnected = true,
                            isWLEDCaptivePortal = linkProperties.isWLEDCaptivePortal(),
                        ),
                    )
                }
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                logger.d { "onLinkPropertiesChanged: $network, $linkProperties" }
                trySend(
                    NetworkStatus(
                        isConnected = true,
                        isWLEDCaptivePortal = linkProperties.isWLEDCaptivePortal(),
                    ),
                )
            }
        }

        var isRegistered = false
        try {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            isRegistered = true
        } catch (e: SecurityException) {
            logger.e(e) { "SecurityException while registering network callback" }
        } catch (e: IllegalStateException) {
            logger.e(e) { "IllegalStateException while registering network callback" }
        } catch (e: IllegalArgumentException) {
            logger.e(e) { "IllegalArgumentException while registering network callback" }
        }

        awaitClose {
            if (isRegistered) {
                try {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                } catch (e: IllegalStateException) {
                    logger.e(e) { "IllegalStateException while unregistering network callback" }
                } catch (e: IllegalArgumentException) {
                    logger.e(e) { "IllegalArgumentException while unregistering network callback" }
                }
            }
        }
    }.distinctUntilChanged()
        .stateIn(
            scope = externalScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = currentNetworkStatus(),
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

    private fun currentNetworkStatus(): NetworkStatus {
        val activeNetwork = getSafeActiveNetwork()
            ?: return NetworkStatus(isConnected = false, isWLEDCaptivePortal = false)
        val linkProperties = getSafeLinkProperties(activeNetwork)
        return NetworkStatus(
            isConnected = true,
            isWLEDCaptivePortal = linkProperties.isWLEDCaptivePortal(),
        )
    }

    private fun getSafeLinkProperties(network: Network): LinkProperties? = try {
        connectivityManager?.getLinkProperties(network)
    } catch (e: SecurityException) {
        logger.e(e) { "SecurityException while querying LinkProperties for $network" }
        null
    }

    private fun getSafeActiveNetwork(): Network? = try {
        connectivityManager?.activeNetwork
    } catch (e: SecurityException) {
        logger.e(e) { "SecurityException while querying activeNetwork" }
        null
    }
}

internal fun LinkProperties?.isWLEDCaptivePortal(): Boolean {
    if (this == null) return false
    for (dnsServer in dnsServers) {
        if (dnsServer.hostAddress == DEFAULT_WLED_AP_IP) {
            return true
        }
    }
    return false
}
