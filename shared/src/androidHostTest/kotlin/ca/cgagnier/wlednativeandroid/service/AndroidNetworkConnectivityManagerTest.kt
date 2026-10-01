package ca.cgagnier.wlednativeandroid.service

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidNetworkConnectivityManagerTest {

    @Test
    fun isWLEDCaptivePortalDetectsMatchingDnsServer() {
        val matchingDns = mockk<InetAddress> {
            every { hostAddress } returns DEFAULT_WLED_AP_IP
        }
        val regularDns = mockk<InetAddress> {
            every { hostAddress } returns "1.1.1.1"
        }

        val matchingLinkProperties = mockk<LinkProperties> {
            every { dnsServers } returns listOf(regularDns, matchingDns)
        }
        val nonMatchingLinkProperties = mockk<LinkProperties> {
            every { dnsServers } returns listOf(regularDns)
        }

        assertTrue(matchingLinkProperties.isWLEDCaptivePortal())
        assertFalse(nonMatchingLinkProperties.isWLEDCaptivePortal())
        assertFalse((null as LinkProperties?).isWLEDCaptivePortal())
    }

    @Test
    fun initialStatusReflectsActiveNetwork() = runTest {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        val activeNetwork = mockk<Network>()
        val regularDns = mockk<InetAddress> {
            every { hostAddress } returns "8.8.8.8"
        }
        val linkProperties = mockk<LinkProperties> {
            every { dnsServers } returns listOf(regularDns)
        }

        every { connectivityManager.activeNetwork } returns activeNetwork
        every { connectivityManager.getLinkProperties(activeNetwork) } returns linkProperties

        val manager = AndroidNetworkConnectivityManager(connectivityManager, backgroundScope)

        assertEquals(NetworkStatus(isConnected = true, isWLEDCaptivePortal = false), manager.networkStatus.value)
        assertTrue(manager.isConnected.value)
        assertFalse(manager.isWLEDCaptivePortal.value)
    }

    @Test
    fun initialStatusIsDisconnectedWhenActiveNetworkIsNull() = runTest {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { connectivityManager.activeNetwork } returns null

        val manager = AndroidNetworkConnectivityManager(connectivityManager, backgroundScope)

        assertEquals(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false), manager.networkStatus.value)
        assertFalse(manager.isConnected.value)
        assertFalse(manager.isWLEDCaptivePortal.value)
    }

    @Test
    fun initialStatusIsDisconnectedWhenConnectivityManagerIsNull() = runTest {
        val manager = AndroidNetworkConnectivityManager(null, backgroundScope)

        assertEquals(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false), manager.networkStatus.value)
        assertFalse(manager.isConnected.value)
        assertFalse(manager.isWLEDCaptivePortal.value)
    }

    @Test
    fun networkCallbackEventsUpdateStateFlows() = runTest {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        every { connectivityManager.activeNetwork } returns null

        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        every { connectivityManager.registerDefaultNetworkCallback(capture(callbackSlot)) } answers { }

        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val manager = AndroidNetworkConnectivityManager(connectivityManager, backgroundScope)

        val network = mockk<Network>()
        val wledDns = mockk<InetAddress> {
            every { hostAddress } returns DEFAULT_WLED_AP_IP
        }
        val wledLinkProperties = mockk<LinkProperties> {
            every { dnsServers } returns listOf(wledDns)
        }
        every { connectivityManager.getLinkProperties(network) } returns wledLinkProperties

        backgroundScope.launch(testDispatcher) { manager.networkStatus.collect {} }
        backgroundScope.launch(testDispatcher) { manager.isConnected.collect {} }
        backgroundScope.launch(testDispatcher) { manager.isWLEDCaptivePortal.collect {} }
        testScheduler.runCurrent()

        val callback = callbackSlot.captured

        // onAvailable with WLED captive portal
        callback.onAvailable(network)
        testScheduler.runCurrent()
        assertEquals(NetworkStatus(isConnected = true, isWLEDCaptivePortal = true), manager.networkStatus.value)
        assertTrue(manager.isConnected.value)
        assertTrue(manager.isWLEDCaptivePortal.value)

        // onLinkPropertiesChanged updates captive portal state
        val regularLinkProperties = mockk<LinkProperties> {
            every { dnsServers } returns listOf(mockk { every { hostAddress } returns "8.8.8.8" })
        }
        callback.onLinkPropertiesChanged(network, regularLinkProperties)
        testScheduler.runCurrent()
        assertEquals(NetworkStatus(isConnected = true, isWLEDCaptivePortal = false), manager.networkStatus.value)
        assertTrue(manager.isConnected.value)
        assertFalse(manager.isWLEDCaptivePortal.value)

        // onLost
        callback.onLost(network)
        testScheduler.runCurrent()
        assertEquals(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false), manager.networkStatus.value)
        assertFalse(manager.isConnected.value)
        assertFalse(manager.isWLEDCaptivePortal.value)
    }
}
