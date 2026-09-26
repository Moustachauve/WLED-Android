package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeviceUpdateManagerTest {

    private val releaseService: ReleaseService = mockk()
    private lateinit var updateManager: DeviceUpdateManager

    private val testDevice = Device(
        macAddress = "AABBCCDDEEFF",
        address = "192.168.1.50",
        originalName = "Living Room WLED",
        branch = Branch.STABLE,
        skipUpdateTag = "0.13.3",
    )

    @BeforeEach
    fun setUp() {
        updateManager = DeviceUpdateManager(releaseService)
    }

    @Test
    fun `checkForUpdate returns null when stateInfo is null`() = runTest {
        val result = updateManager.checkForUpdate(testDevice, null)

        assertNull(result)
        coVerify(exactly = 0) { releaseService.getNewerReleaseTag(any(), any(), any()) }
    }

    @Test
    fun `checkForUpdate queries releaseService with device properties and returns tag`() = runTest {
        val stateInfo = createDeviceStateInfo(version = "0.14.0")
        coEvery {
            releaseService.getNewerReleaseTag(
                deviceInfo = stateInfo.info,
                branch = Branch.STABLE,
                ignoreVersion = "0.13.3",
            )
        } returns "0.14.1"

        val result = updateManager.checkForUpdate(testDevice, stateInfo)

        assertEquals("0.14.1", result)
        coVerify(exactly = 1) {
            releaseService.getNewerReleaseTag(
                deviceInfo = stateInfo.info,
                branch = Branch.STABLE,
                ignoreVersion = "0.13.3",
            )
        }
    }

    @Test
    fun `checkForUpdate returns null when device is up to date`() = runTest {
        val stateInfo = createDeviceStateInfo(version = "0.14.1")
        coEvery {
            releaseService.getNewerReleaseTag(any(), any(), any())
        } returns null

        val result = updateManager.checkForUpdate(testDevice, stateInfo)

        assertNull(result)
    }

    @Test
    fun `checkForUpdate with DeviceWithState delegates to device and stateInfo`() = runTest {
        val stateInfo = createDeviceStateInfo(version = "0.14.0")
        val deviceWithState = DeviceWithState(device = testDevice, stateInfo = stateInfo)
        coEvery {
            releaseService.getNewerReleaseTag(
                deviceInfo = stateInfo.info,
                branch = Branch.STABLE,
                ignoreVersion = "0.13.3",
            )
        } returns "0.14.2"

        val result = updateManager.checkForUpdate(deviceWithState)

        assertEquals("0.14.2", result)
    }

    @Test
    fun `getUpdateFlow deduplicates emissions when only non-update fields change`() = runTest {
        val info1 = createInfo(version = "0.14.0", uptime = 100)
        val info2 = createInfo(version = "0.14.0", uptime = 200)
        val stateInfo1 = DeviceStateInfo(state = State(isOn = true, brightness = 100), info = info1)
        val stateInfo2 = DeviceStateInfo(state = State(isOn = true, brightness = 200), info = info2)

        val dev1 = DeviceWithState(device = testDevice, stateInfo = stateInfo1)
        val dev2 = DeviceWithState(device = testDevice, stateInfo = stateInfo2)

        coEvery {
            releaseService.getNewerReleaseTag(any(), any(), any())
        } returns "0.14.4"

        val results = updateManager.getUpdateFlow(flowOf(dev1, dev2)).toList()

        assertEquals(1, results.size)
        assertEquals("0.14.4", results[0])
        coVerify(exactly = 1) { releaseService.getNewerReleaseTag(any(), any(), any()) }
    }

    private fun createDeviceStateInfo(version: String? = "0.14.0", repository: String? = null): DeviceStateInfo =
        DeviceStateInfo(
            state = State(isOn = true),
            info = createInfo(version = version, repository = repository),
        )

    private fun createInfo(version: String? = "0.14.0", repository: String? = null, uptime: Int? = null): Info = Info(
        leds = Leds(count = 30, fps = 30, maxPower = 0, maxSegment = 1),
        wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
        name = "Test Device",
        version = version,
        repository = repository,
        uptime = uptime,
        options = 0x01,
    )
}
