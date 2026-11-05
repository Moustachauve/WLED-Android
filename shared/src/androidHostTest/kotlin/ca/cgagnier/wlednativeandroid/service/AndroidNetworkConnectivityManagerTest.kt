package ca.cgagnier.wlednativeandroid.service

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
        assertTrue(mockLinkProperties("1.1.1.1", DEFAULT_WLED_AP_IP).isWLEDCaptivePortal())
        assertFalse(mockLinkProperties("1.1.1.1").isWLEDCaptivePortal())
        assertFalse((null as LinkProperties?).isWLEDCaptivePortal())
    }

    @Test
    fun initialStatusReflectsActiveNetwork() = runTest {
        val activeNetwork = mockk<Network>()
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true) {
            every { this@mockk.activeNetwork } returns activeNetwork
            every { getLinkProperties(activeNetwork) } returns mockLinkProperties("8.8.8.8")
        }

        val manager = AndroidNetworkConnectivityManager(connectivityManager, backgroundScope)

        assertEquals(NetworkStatus(isConnected = true, isWLEDCaptivePortal = false), manager.networkStatus.value)
    }

    @Test
    fun initialStatusIsDisconnectedWhenActiveNetworkIsNull() = runTest {
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true) {
            every { activeNetwork } returns null
        }

        val manager = AndroidNetworkConnectivityManager(connectivityManager, backgroundScope)

        assertEquals(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false), manager.networkStatus.value)
    }

    @Test
    fun initialStatusIsDisconnectedWhenConnectivityManagerIsNull() = runTest {
        val manager = AndroidNetworkConnectivityManager(null, backgroundScope)

        assertEquals(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false), manager.networkStatus.value)
    }

    @Test
    fun onAvailableEmitsConnectedWithCaptivePortalStatus() = runTest {
        val fixture = CallbackTestFixture(this)
        fixture.onAvailable(mockLinkProperties(DEFAULT_WLED_AP_IP))

        assertEquals(
            NetworkStatus(isConnected = true, isWLEDCaptivePortal = true),
            fixture.manager.networkStatus.value,
        )
    }

    @Test
    fun onLinkPropertiesChangedUpdatesCaptivePortalStatus() = runTest {
        val fixture = CallbackTestFixture(this)
        fixture.onLinkPropertiesChanged(mockLinkProperties(DEFAULT_WLED_AP_IP))

        assertEquals(
            NetworkStatus(isConnected = true, isWLEDCaptivePortal = true),
            fixture.manager.networkStatus.value,
        )
    }

    @Test
    fun onLostEmitsDisconnectedStatusWhenNetworkLost() = runTest {
        val fixture = CallbackTestFixture(this)
        fixture.onLost()

        assertEquals(
            NetworkStatus(isConnected = false, isWLEDCaptivePortal = false),
            fixture.manager.networkStatus.value,
        )
    }

    @Test
    fun onLostRetainsConnectedStatusWhenDifferentNetworkRemainsActive() = runTest {
        val fixture = CallbackTestFixture(this)
        val remainingActiveNetwork = mockk<Network>()
        every { fixture.connectivityManager.activeNetwork } returns remainingActiveNetwork
        every { fixture.connectivityManager.getLinkProperties(remainingActiveNetwork) } returns
            mockLinkProperties("1.1.1.1")

        fixture.onLost()

        assertEquals(
            NetworkStatus(isConnected = true, isWLEDCaptivePortal = false),
            fixture.manager.networkStatus.value,
        )
    }

    @Test
    fun convenienceFlowsReflectNetworkStatus() = runTest {
        val activeNetwork = mockk<Network>()
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true) {
            every { this@mockk.activeNetwork } returns activeNetwork
            every { getLinkProperties(activeNetwork) } returns mockLinkProperties(DEFAULT_WLED_AP_IP)
        }

        val manager = AndroidNetworkConnectivityManager(connectivityManager, backgroundScope)

        assertEquals(NetworkStatus(isConnected = true, isWLEDCaptivePortal = true), manager.networkStatus.value)
        assertTrue(manager.isConnected.value)
        assertTrue(manager.isWLEDCaptivePortal.value)
    }

    private fun mockLinkProperties(vararg dnsIps: String): LinkProperties = mockk {
        every { dnsServers } returns dnsIps.map { ip ->
            mockk<InetAddress> { every { hostAddress } returns ip }
        }
    }

    private class CallbackTestFixture(private val testScope: TestScope) {
        val callbackSlot = slot<ConnectivityManager.NetworkCallback>()
        val network = mockk<Network>()
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true) {
            every { activeNetwork } returns null
            every { registerDefaultNetworkCallback(capture(callbackSlot)) } answers {}
        }
        val manager = AndroidNetworkConnectivityManager(connectivityManager, testScope.backgroundScope)

        init {
            testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
                manager.networkStatus.collect()
            }
            testScope.testScheduler.runCurrent()
        }

        private val callback: ConnectivityManager.NetworkCallback get() = callbackSlot.captured

        fun onAvailable(linkProperties: LinkProperties) {
            every { connectivityManager.getLinkProperties(network) } returns linkProperties
            callback.onAvailable(network)
            testScope.testScheduler.runCurrent()
        }

        fun onLinkPropertiesChanged(linkProperties: LinkProperties) {
            callback.onLinkPropertiesChanged(network, linkProperties)
            testScope.testScheduler.runCurrent()
        }

        fun onLost() {
            callback.onLost(network)
            testScope.testScheduler.runCurrent()
        }
    }
}
