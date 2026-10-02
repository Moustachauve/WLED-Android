package ca.cgagnier.wlednativeandroid.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class IosNetworkConnectivityManagerTest {

    @Test
    fun initialStatusIsDisconnectedByDefault() = runTest {
        val manager = IosNetworkConnectivityManager(backgroundScope, FakePathMonitor())

        assertEquals(NetworkStatus(isConnected = false, isWLEDCaptivePortal = false), manager.networkStatus.value)
    }

    @Test
    fun initialStatusCanBeConfigured() = runTest {
        val customStatus = NetworkStatus(isConnected = true, isWLEDCaptivePortal = false)
        val manager = IosNetworkConnectivityManager(backgroundScope, FakePathMonitor(), initialStatus = customStatus)

        assertEquals(customStatus, manager.networkStatus.value)
    }

    @Test
    fun pathMonitorUpdateEmitsConnectedStatus() = runTest {
        val fixture = IosCallbackFixture(this)
        fixture.emitUpdate(true)

        assertEquals(
            NetworkStatus(isConnected = true, isWLEDCaptivePortal = false),
            fixture.manager.networkStatus.value,
        )
    }

    @Test
    fun pathMonitorUpdateEmitsDisconnectedStatus() = runTest {
        val fixture = IosCallbackFixture(
            this,
            initialStatus = NetworkStatus(isConnected = true, isWLEDCaptivePortal = false),
        )
        fixture.emitUpdate(false)

        assertEquals(
            NetworkStatus(isConnected = false, isWLEDCaptivePortal = false),
            fixture.manager.networkStatus.value,
        )
    }

    @Test
    fun convenienceFlowsReflectNetworkStatus() = runTest {
        val fixture = IosCallbackFixture(this)
        fixture.emitUpdate(true)

        assertTrue(fixture.manager.isConnected.value)
        assertFalse(fixture.manager.isWLEDCaptivePortal.value)
    }

    @Test
    fun flowCollectionStartsPathMonitor() = runTest {
        val fakeMonitor = FakePathMonitor()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            val manager = IosNetworkConnectivityManager(this, fakeMonitor)
            manager.networkStatus.collect()
        }
        testScheduler.runCurrent()

        assertTrue(fakeMonitor.isStarted)
    }

    @Test
    fun flowCancellationCancelsPathMonitor() = runTest {
        val fakeMonitor = FakePathMonitor()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            val manager = IosNetworkConnectivityManager(this, fakeMonitor)
            manager.networkStatus.collect()
        }
        testScheduler.runCurrent()

        job.cancel()
        testScheduler.runCurrent()

        assertTrue(fakeMonitor.isCancelled)
    }

    @Test
    fun realIosPathMonitorStartsAndCancelsSafely() {
        val monitor = RealIosPathMonitor()
        monitor.start { }
        monitor.cancel()
    }

    private class FakePathMonitor : IosPathMonitor {
        var onUpdateCallback: ((Boolean) -> Unit)? = null
        var isStarted: Boolean = false
        var isCancelled: Boolean = false

        override fun start(onUpdate: (isConnected: Boolean) -> Unit) {
            isStarted = true
            onUpdateCallback = onUpdate
        }

        override fun cancel() {
            isCancelled = true
        }
    }

    private class IosCallbackFixture(
        private val testScope: TestScope,
        initialStatus: NetworkStatus = NetworkStatus(isConnected = false, isWLEDCaptivePortal = false),
    ) {
        val fakeMonitor = FakePathMonitor()
        val manager = IosNetworkConnectivityManager(testScope.backgroundScope, fakeMonitor, initialStatus)

        init {
            testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
                manager.networkStatus.collect()
            }
            testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
                manager.isConnected.collect()
            }
            testScope.backgroundScope.launch(UnconfinedTestDispatcher(testScope.testScheduler)) {
                manager.isWLEDCaptivePortal.collect()
            }
            testScope.testScheduler.runCurrent()
        }

        fun emitUpdate(isConnected: Boolean) {
            fakeMonitor.onUpdateCallback?.invoke(isConnected)
            testScope.testScheduler.runCurrent()
        }
    }
}
