package ca.cgagnier.wlednativeandroid.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
    fun startWhenAlreadyDiscoveringDoesNotRestart() {
        val fakeBrowser = FakeIosBonjourBrowser()
        val discovery = IosDeviceDiscovery(fakeBrowser)
        discovery.start()
        assertEquals(1, fakeBrowser.startCount)

        discovery.start()
        assertEquals(1, fakeBrowser.startCount)
    }

    @Test
    fun browserDiscoveredDeviceEmitsToFlow() = runTest {
        val fakeBrowser = FakeIosBonjourBrowser()
        val discovery = IosDeviceDiscovery(fakeBrowser)
        discovery.start()

        val collected = mutableListOf<DiscoveredDevice>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            discovery.discoveredDevices.toList(collected)
        }

        val expected = DiscoveredDevice("192.168.1.120", "112233445566")
        fakeBrowser.onDeviceFound?.invoke(expected)

        assertEquals(listOf(expected), collected)
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
        var startCount = 0
        var onDeviceFound: ((DiscoveredDevice) -> Unit)? = null

        override fun start(onDeviceFound: (DiscoveredDevice) -> Unit) {
            isStarted = true
            startCount++
            this.onDeviceFound = onDeviceFound
        }

        override fun stop() {
            isStopped = true
        }
    }
}
