package ca.cgagnier.wlednativeandroid.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeNetworkConnectivityManagerTest {

    @Test
    fun stateMutationsSynchronizeAllExposedFlows() {
        val fake = FakeNetworkConnectivityManager()

        fake.setConnected(true)
        assertEquals(NetworkStatus(isConnected = true, isWLEDCaptivePortal = false), fake.networkStatus.value)
        assertTrue(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)

        fake.setWLEDCaptivePortal(true)
        assertEquals(NetworkStatus(isConnected = true, isWLEDCaptivePortal = true), fake.networkStatus.value)
        assertTrue(fake.isWLEDCaptivePortal.value)

        fake.setStatus(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false))
        assertFalse(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)
    }
}
