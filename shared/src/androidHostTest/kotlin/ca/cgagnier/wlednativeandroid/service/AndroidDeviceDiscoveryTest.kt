package ca.cgagnier.wlednativeandroid.service

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

        verify { fixture.nsdManager.discoverServices(any<String>(), NsdManager.PROTOCOL_DNS_SD, any()) }
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
    fun startWhenAlreadyDiscoveringDoesNotRestart() {
        val fixture = DiscoveryFixture()
        fixture.discovery.start()
        fixture.discovery.start()

        verify(exactly = 1) { fixture.nsdManager.discoverServices(any<String>(), any<Int>(), any()) }
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

        val collected = mutableListOf<DiscoveredDevice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            fixture.discovery.discoveredDevices.toList(collected)
        }

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
        assertEquals(listOf(expected), collected)
    }

    @Test
    fun multipleServicesFoundAreResolvedSequentially() {
        val fixture = DiscoveryFixture()
        fixture.discovery.start()

        val service1 = mockk<NsdServiceInfo> { every { serviceType } returns "._wled._tcp" }
        val service2 = mockk<NsdServiceInfo> { every { serviceType } returns "_wled._tcp." }

        val resolveSlot1 = slot<NsdManager.ResolveListener>()
        val resolveSlot2 = slot<NsdManager.ResolveListener>()
        every { fixture.nsdManager.resolveService(service1, capture(resolveSlot1)) } answers {}
        every { fixture.nsdManager.resolveService(service2, capture(resolveSlot2)) } answers {}

        fixture.discoveryListener.onServiceFound(service1)
        fixture.discoveryListener.onServiceFound(service2)

        verify(exactly = 1) { fixture.nsdManager.resolveService(service1, any()) }
        verify(exactly = 0) { fixture.nsdManager.resolveService(service2, any()) }

        val resolved1 = mockk<NsdServiceInfo> {
            every { host } returns mockk<InetAddress> { every { hostAddress } returns "192.168.1.101" }
            every { attributes } returns emptyMap()
        }
        resolveSlot1.captured.onServiceResolved(resolved1)

        verify(exactly = 1) { fixture.nsdManager.resolveService(service2, any()) }
    }

    @Test
    fun unknownServiceTypeIsNotResolved() {
        val fixture = DiscoveryFixture()
        fixture.discovery.start()

        val unknownService = mockk<NsdServiceInfo> {
            every { serviceType } returns "_printer._tcp"
        }
        fixture.discoveryListener.onServiceFound(unknownService)

        verify(exactly = 0) { fixture.nsdManager.resolveService(any(), any()) }
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
