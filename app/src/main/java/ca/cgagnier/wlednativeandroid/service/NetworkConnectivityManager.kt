package ca.cgagnier.wlednativeandroid.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.util.Log
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

private const val TAG = "service.NetworkConnectivityManager"

// Inspired from this medium article:
// https://medium.com/@meytataliti/obtaining-network-connection-info-with-flow-in-android-af2e6b760dfd

class NetworkConnectivityManager(context: Context, externalScope: CoroutineScope) {

    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    val networkEvents: StateFlow<NetworkProperties> = callbackFlow {
        Log.d(TAG, "_connectionFlow")
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                Log.d(TAG, "onLost: $network")
                trySend(NetworkProperties(network, null))
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                Log.d(TAG, "onLinkPropertiesChanged: $network, $linkProperties")
                trySend(NetworkProperties(network, linkProperties))
            }

            // For now we don't care about this callback, but maybe we will in the future. In that
            // case, we would need to figure out how to not lose the linkProperties when emitting a
            // new NetworkProperties.
            //override fun onCapabilitiesChanged(
            //    network: Network,
            //    networkCapabilities: NetworkCapabilities
            //) {
            //    Log.d(TAG, "onCapabilitiesChanged: $network, $networkCapabilities")
            //    trySend(NetworkProperties(network, networkCapabilities, null))
            //}
        }
        subscribe(networkCallback)
        awaitClose {
            unsubscribe(networkCallback)
        }
    }.stateIn(
        scope = externalScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = _currentNetwork
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val isWLEDCaptivePortal = networkEvents.mapLatest { networkProperties ->
        for (dnsServer in networkProperties.linkProperties?.dnsServers ?: emptyList()) {
            if (dnsServer.hostAddress == DEFAULT_WLED_AP_IP) {
                Log.d(TAG, "This is a WLED captive portal")
                return@mapLatest true
            }
        }

        false

    }.stateIn(
        scope = externalScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _currentNetwork: NetworkProperties
        get() {
            val activeNetwork = connectivityManager.activeNetwork
            return if (activeNetwork == null) {
                NetworkProperties(network = null, linkProperties = null)
            } else {
                //val netCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                NetworkProperties(
                    network = activeNetwork,
                    linkProperties = linkProperties
                )
            }
        }

    private fun subscribe(networkCallback: ConnectivityManager.NetworkCallback) {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
    }

    private fun unsubscribe(networkCallback: ConnectivityManager.NetworkCallback) {
        connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}

data class NetworkProperties(
    val network: Network?,
    val linkProperties: LinkProperties?

)