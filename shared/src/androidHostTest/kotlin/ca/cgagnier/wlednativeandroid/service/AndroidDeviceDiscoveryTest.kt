package ca.cgagnier.wlednativeandroid.service

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidDeviceDiscoveryTest {

    @Test
    fun startInitiatesNsdDiscovery() {
        val fixture = DiscoveryFixture()
        fixture.discovery.start()

        verify { fixture.nsdManager.discoverServices(any(), NsdManager.PROTOCOL_DNS_SD, any()) }
        assertTrue(fixture.discovery.isDiscovering.value)
    }

    @Test
    fun stopHaltsNsdDiscovery() {
        val fixture = DiscoveryFixture()
        fixture.discovery.start()
        fixture.discovery.stop()

        verify { fixture.nsdManager.stopServiceDiscovery(any()) }
        assertFalse(fixture.discovery.isDiscovering.value)
    }

    @Test
    fun startSafelyHandlesNullNsdManager() {
        val discovery = AndroidDeviceDiscovery(nsdManager = null, wifiManager = null)
        discovery.start()

        assertFalse(discovery.isDiscovering.value)
    }

    @Test
    fun serviceResolvedEmitsDiscoveredDevice() = runTest {
        val fixture = DiscoveryFixture()
        fixture.discovery.start()

        val service = mockk<NsdServiceInfo> {
            every { serviceType } returns "_wled._tcp."
        }
        val resolveSlot = slot<NsdManager.ResolveListener>()
        every { fixture.nsdManager.resolveService(service, capture(resolveSlot)) } answers {}

        fixture.discoveryListener.onServiceFound(service)

        val resolvedServiceInfo = mockk<NsdServiceInfo> {
            every { host } returns mockk<InetAddress> {
                every { hostAddress } returns "192.168.1.100"
            }
            every { attributes } returns mapOf("mac" to "AABBCCDDEEFF".toByteArray())
        }

        resolveSlot.captured.onServiceResolved(resolvedServiceInfo)

        val expected = DiscoveredDevice("192.168.1.100", "AABBCCDDEEFF")
        assertEquals(expected, fixture.discovery.discoveredDevices.first())
    }

    private class DiscoveryFixture {
        val listenerSlot = slot<NsdManager.DiscoveryListener>()
        val nsdManager = mockk<NsdManager>(relaxed = true) {
            every { discoverServices(any<String>(), any<Int>(), capture(listenerSlot)) } answers {}
        }
        val multicastLock = mockk<WifiManager.MulticastLock>(relaxed = true)
        val wifiManager = mockk<WifiManager>(relaxed = true) {
            every { createMulticastLock(any()) } returns multicastLock
        }
        val discovery = AndroidDeviceDiscovery(nsdManager, wifiManager)

        val discoveryListener: NsdManager.DiscoveryListener get() = listenerSlot.captured
    }
}
