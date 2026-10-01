package ca.cgagnier.wlednativeandroid.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeNetworkConnectivityManagerTest {

    @Test
    fun defaultConstructorInitializesToDisconnected() {
        val fake = FakeNetworkConnectivityManager()

        assertFalse(fake.networkStatus.value.isConnected)
        assertFalse(fake.networkStatus.value.isWLEDCaptivePortal)
        assertFalse(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)
    }

    @Test
    fun convenienceConstructorInitializesFlagsDirectly() {
        val fake = FakeNetworkConnectivityManager(
            isConnected = true,
            isWLEDCaptivePortal = true,
        )

        assertTrue(fake.networkStatus.value.isConnected)
        assertTrue(fake.networkStatus.value.isWLEDCaptivePortal)
        assertTrue(fake.isConnected.value)
        assertTrue(fake.isWLEDCaptivePortal.value)
    }

    @Test
    fun primaryConstructorAcceptsNetworkStatusObject() {
        val initialStatus = NetworkStatus(isConnected = true, isWLEDCaptivePortal = false)
        val fake = FakeNetworkConnectivityManager(initialStatus)

        assertEquals(initialStatus, fake.networkStatus.value)
        assertTrue(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)
    }

    @Test
    fun setConnectedUpdatesStatusAndFlows() {
        val fake = FakeNetworkConnectivityManager()

        fake.setConnected(true)
        assertTrue(fake.networkStatus.value.isConnected)
        assertTrue(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)

        fake.setConnected(false)
        assertFalse(fake.networkStatus.value.isConnected)
        assertFalse(fake.isConnected.value)
    }

    @Test
    fun setWLEDCaptivePortalUpdatesStatusAndFlows() {
        val fake = FakeNetworkConnectivityManager(isConnected = true)

        fake.setWLEDCaptivePortal(true)
        assertTrue(fake.networkStatus.value.isConnected)
        assertTrue(fake.networkStatus.value.isWLEDCaptivePortal)
        assertTrue(fake.isWLEDCaptivePortal.value)

        fake.setWLEDCaptivePortal(false)
        assertFalse(fake.networkStatus.value.isWLEDCaptivePortal)
        assertFalse(fake.isWLEDCaptivePortal.value)
        assertTrue(fake.isConnected.value)
    }

    @Test
    fun setStatusUpdatesAllFlowsAtomically() {
        val fake = FakeNetworkConnectivityManager()
        val newStatus = NetworkStatus(isConnected = true, isWLEDCaptivePortal = true)

        fake.setStatus(newStatus)
        assertEquals(newStatus, fake.networkStatus.value)
        assertTrue(fake.isConnected.value)
        assertTrue(fake.isWLEDCaptivePortal.value)

        fake.setStatus(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false))
        assertFalse(fake.networkStatus.value.isConnected)
        assertFalse(fake.isConnected.value)
        assertFalse(fake.isWLEDCaptivePortal.value)
    }
}
