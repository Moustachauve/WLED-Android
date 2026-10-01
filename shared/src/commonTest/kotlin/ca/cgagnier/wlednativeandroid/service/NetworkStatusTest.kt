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
}
