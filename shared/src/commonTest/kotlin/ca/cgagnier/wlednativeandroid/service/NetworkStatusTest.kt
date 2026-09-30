package ca.cgagnier.wlednativeandroid.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NetworkStatusTest {

    @Test
    fun defaultValuesAreFalse() {
        val status = NetworkStatus()
        assertFalse(status.isConnected)
        assertFalse(status.isWLEDCaptivePortal)
    }

    @Test
    fun copyMaintainsIntegrity() {
        val original = NetworkStatus(isConnected = true, isWLEDCaptivePortal = false)
        val updated = original.copy(isWLEDCaptivePortal = true)

        assertTrue(updated.isConnected)
        assertTrue(updated.isWLEDCaptivePortal)
        assertFalse(original.isWLEDCaptivePortal)
    }

    @Test
    fun fakeNetworkConnectivityManagerUpdatesFlows() {
        val fake = FakeNetworkConnectivityManager()

        assertFalse(fake.networkStatus.value.isConnected)
        assertFalse(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)

        fake.setConnected(true)
        assertTrue(fake.networkStatus.value.isConnected)
        assertTrue(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)

        fake.setWLEDCaptivePortal(true)
        assertTrue(fake.networkStatus.value.isConnected)
        assertTrue(fake.isConnected.value)
        assertTrue(fake.isWLEDCaptivePortal.value)
        assertEquals(NetworkStatus(isConnected = true, isWLEDCaptivePortal = true), fake.networkStatus.value)

        fake.setStatus(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false))
        assertFalse(fake.networkStatus.value.isConnected)
        assertFalse(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)
    }
}
