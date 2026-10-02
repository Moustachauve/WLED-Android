package ca.cgagnier.wlednativeandroid.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosDeviceDiscoveryTest {

    @Test
    fun startSetsIsDiscoveringToTrue() {
        val fakeBrowser = FakeIosBonjourBrowser()
        val discovery = IosDeviceDiscovery(fakeBrowser)

        discovery.start()

        assertTrue(discovery.isDiscovering.value)
        assertTrue(fakeBrowser.isStarted)
    }

    @Test
    fun stopSetsIsDiscoveringToFalse() {
        val fakeBrowser = FakeIosBonjourBrowser()
        val discovery = IosDeviceDiscovery(fakeBrowser)

        discovery.start()
        discovery.stop()

        assertFalse(discovery.isDiscovering.value)
        assertTrue(fakeBrowser.isStopped)
    }

    @Test
    fun browserDiscoveredDeviceEmitsToFlow() = runTest {
        val fakeBrowser = FakeIosBonjourBrowser()
        val discovery = IosDeviceDiscovery(fakeBrowser)
        discovery.start()

        val expected = DiscoveredDevice("192.168.1.120", "112233445566")
        fakeBrowser.onDeviceFound?.invoke(expected)

        assertEquals(expected, discovery.discoveredDevices.first())
    }

    @Test
    fun realIosBonjourBrowserStartsAndStopsSafely() {
        val browser = RealIosBonjourBrowser()
        browser.start { }
        browser.stop()
    }

    private class FakeIosBonjourBrowser : IosBonjourBrowser {
        var isStarted = false
        var isStopped = false
        var onDeviceFound: ((DiscoveredDevice) -> Unit)? = null

        override fun start(onDeviceFound: (DiscoveredDevice) -> Unit) {
            isStarted = true
            this.onDeviceFound = onDeviceFound
        }

        override fun stop() {
            isStopped = true
        }
    }
}
