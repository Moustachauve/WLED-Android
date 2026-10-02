package ca.cgagnier.wlednativeandroid.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FakeDeviceDiscoveryTest {

    @Test
    fun startSetsIsDiscoveringToTrue() {
        val discovery = FakeDeviceDiscovery()
        discovery.start()

        assertTrue(discovery.isDiscovering.value)
    }

    @Test
    fun stopSetsIsDiscoveringToFalse() {
        val discovery = FakeDeviceDiscovery()
        discovery.start()
        discovery.stop()

        assertFalse(discovery.isDiscovering.value)
    }

    @Test
    fun emitDeviceEmitsToDiscoveredDevicesFlow() = runTest {
        val discovery = FakeDeviceDiscovery()
        val expectedDevice = DiscoveredDevice("192.168.1.50", "00:11:22:33:44:55")

        discovery.emitDeviceSync(expectedDevice)

        assertEquals(expectedDevice, discovery.discoveredDevices.first())
    }
}
